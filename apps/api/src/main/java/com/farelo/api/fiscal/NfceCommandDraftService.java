package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only, fail-closed preparation of an unsigned NFC-e homologation draft. */
@Service
public class NfceCommandDraftService {
    private final CommandRepository commands;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final DiscountRepository discounts;
    private final PaymentRepository payments;
    private final NfceXmlBuilder xml = new NfceXmlBuilder();

    public NfceCommandDraftService(CommandRepository commands, OrderRepository orders,
                                   OrderItemRepository orderItems, DiscountRepository discounts,
                                   PaymentRepository payments) {
        this.commands = commands;
        this.orders = orders;
        this.orderItems = orderItems;
        this.discounts = discounts;
        this.payments = payments;
    }

    /** All header fields come from a separately reviewed reservation/configuration, never defaults. */
    public record Header(NfceXmlBuilder.Issuer issuer, String operation, int series, int number,
                         String randomCode, OffsetDateTime issuedAt) { }

    @Transactional(readOnly = true)
    public NfceXmlBuilder.Result buildHomologation(UUID commandId, Header header) {
        if (commandId == null) throw new IllegalArgumentException("command id is required");
        Command command = commands.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("command was not found"));
        List<OrderItem> sold = new ArrayList<>();
        for (Order order : orders.findByCommandOrderByCreatedAtAsc(command)) {
            if (order == null || order.getId() == null || order.getCommand() == null
                    || !commandId.equals(order.getCommand().getId()) || order.getStatus() == null) {
                throw new IllegalArgumentException("invalid persisted order in command");
            }
            if (order.getStatus() == OrderStatus.CREATED) {
                throw new IllegalArgumentException("unconfirmed order cannot enter fiscal draft");
            }
            if (order.getStatus() != OrderStatus.CANCELLED) sold.addAll(orderItems.findByOrder(order));
        }
        return assemble(command, sold, discounts.findByCommandOrderByCreatedAtAsc(command),
                payments.findByCommandOrderByCreatedAtAsc(command), header);
    }

    /** Package-private for deterministic unit tests; no DB write or SEFAZ call. */
    NfceXmlBuilder.Result assemble(Command command, List<OrderItem> sold, List<Discount> discounts,
                                   List<Payment> payments, Header header) {
        if (command == null || command.getId() == null || command.getStatus() != CommandStatus.CLOSED
                || header == null || header.issuer() == null || sold == null || discounts == null
                || payments == null) {
            throw new IllegalArgumentException("closed persisted command and explicit fiscal data are required");
        }
        UUID commandId = command.getId();
        List<OrderItem> active = new ArrayList<>();
        Set<UUID> seenItems = new HashSet<>();
        for (OrderItem line : sold) {
            if (line == null || line.getId() == null || !seenItems.add(line.getId())
                    || line.getOrder() == null || line.getOrder().getId() == null
                    || line.getOrder().getStatus() == null
                    || line.getOrder().getStatus() == OrderStatus.CANCELLED
                    || line.getOrder().getStatus() == OrderStatus.CREATED
                    || line.getOrder().getCommand() == null
                    || !commandId.equals(line.getOrder().getCommand().getId())) {
                throw new IllegalArgumentException("invalid or duplicate persisted sale line");
            }
            if (!line.isCancelled()) active.add(line);
        }
        active.sort(Comparator.comparing(OrderItem::getId));
        if (active.isEmpty() || active.size() > 990) {
            throw new IllegalArgumentException("1 to 990 active sale lines are required");
        }

        List<NfceXmlBuilder.Item> mapped = active.stream()
                .map(line -> NfceOrderItemMapper.mapSimple(line, header.issuer().crt()))
                .toList();
        Map<UUID, Integer> position = new HashMap<>();
        for (int i = 0; i < active.size(); i++) position.put(active.get(i).getId(), i);
        List<BigDecimal> lineDiscounts = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) lineDiscounts.add(BigDecimal.ZERO);
        BigDecimal commandDiscount = BigDecimal.ZERO;
        Set<UUID> seenDiscounts = new HashSet<>();
        for (Discount discount : discounts) {
            if (discount == null || discount.getId() == null || !seenDiscounts.add(discount.getId())
                    || discount.getCommand() == null || !commandId.equals(discount.getCommand().getId())
                    || discount.getType() == null || discount.getOriginalAmount() == null) {
                throw new IllegalArgumentException("invalid or duplicate discount ledger entry");
            }
            BigDecimal amount = discount.getDiscountedAmount();
            if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
                throw new IllegalArgumentException("discount must be a positive exact-cent amount");
            }
            if (discount.getOrderItem() == null) {
                commandDiscount = commandDiscount.add(amount);
            } else {
                Integer index = position.get(discount.getOrderItem().getId());
                if (index == null) throw new IllegalArgumentException("discount refers to an inactive sale line");
                lineDiscounts.set(index, lineDiscounts.get(index).add(amount));
            }
        }
        List<BigInteger> remaining = new ArrayList<>();
        for (int i = 0; i < mapped.size(); i++) {
            BigDecimal gross = mapped.get(i).total();
            if (lineDiscounts.get(i).compareTo(gross) > 0) {
                throw new IllegalArgumentException("item discounts exceed sale line amount");
            }
            remaining.add(cents(gross.subtract(lineDiscounts.get(i))));
        }
        List<BigInteger> allocated = allocate(cents(commandDiscount), remaining);
        List<NfceXmlBuilder.Item> fiscalItems = new ArrayList<>(mapped.size());
        BigDecimal net = BigDecimal.ZERO;
        for (int i = 0; i < mapped.size(); i++) {
            NfceXmlBuilder.Item item = mapped.get(i);
            BigDecimal discount = lineDiscounts.get(i).add(new BigDecimal(allocated.get(i), 2));
            fiscalItems.add(new NfceXmlBuilder.Item(item.code(), item.description(), item.gtin(),
                    item.ncm(), item.cest(), item.cfop(), item.unit(), item.quantity(),
                    item.unitPrice(), item.total(), item.taxes(), discount));
            net = net.add(item.total().subtract(discount));
        }
        for (Payment payment : payments) {
            if (payment == null || payment.getCommand() == null
                    || !commandId.equals(payment.getCommand().getId())) {
                throw new IllegalArgumentException("payment belongs to another command");
            }
        }
        List<NfceXmlBuilder.Payment> fiscalPayments = NfceRecordedPaymentMapper.map(payments);
        BigDecimal paid = fiscalPayments.stream().map(NfceXmlBuilder.Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.signum() <= 0 || net.compareTo(paid) != 0) {
            throw new IllegalArgumentException("recorded payments do not equal the discounted sale total");
        }
        return xml.buildHomologation(new NfceXmlBuilder.Draft(header.issuer(), header.operation(),
                header.series(), header.number(), header.randomCode(), header.issuedAt(),
                List.copyOf(fiscalItems), fiscalPayments));
    }

    private static BigInteger cents(BigDecimal amount) {
        try {
            return amount.movePointRight(2).toBigIntegerExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount must have exact cents", ex);
        }
    }

    /** Largest-remainder split, stable on item order, with no lost cent. */
    static List<BigInteger> allocate(BigInteger discount, List<BigInteger> capacities) {
        if (discount.signum() < 0 || capacities.isEmpty()
                || capacities.stream().anyMatch(v -> v.signum() < 0)) {
            throw new IllegalArgumentException("invalid discount allocation");
        }
        BigInteger total = capacities.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (discount.compareTo(total) > 0 || (total.signum() == 0 && discount.signum() > 0)) {
            throw new IllegalArgumentException("command discount exceeds remaining sale amount");
        }
        List<BigInteger> result = new ArrayList<>();
        List<BigInteger> remainders = new ArrayList<>();
        BigInteger assigned = BigInteger.ZERO;
        for (BigInteger capacity : capacities) {
            BigInteger[] split = total.signum() == 0
                    ? new BigInteger[] {BigInteger.ZERO, BigInteger.ZERO}
                    : discount.multiply(capacity).divideAndRemainder(total);
            result.add(split[0]);
            remainders.add(split[1]);
            assigned = assigned.add(split[0]);
        }
        List<Integer> ranking = new ArrayList<>();
        for (int i = 0; i < capacities.size(); i++) ranking.add(i);
        ranking.sort(Comparator.<Integer, BigInteger>comparing(remainders::get).reversed()
                .thenComparingInt(Integer::intValue));
        for (int i = 0, left = discount.subtract(assigned).intValueExact(); i < left; i++) {
            int index = ranking.get(i);
            result.set(index, result.get(index).add(BigInteger.ONE));
        }
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).compareTo(capacities.get(i)) > 0) {
                throw new IllegalArgumentException("discount allocation exceeds item capacity");
            }
        }
        return List.copyOf(result);
    }
}
