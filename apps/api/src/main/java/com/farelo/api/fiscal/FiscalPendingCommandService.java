package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandNotFoundException;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.discount.DiscountRepository;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Explicit preparation boundary: no number reservation, XML, certificate or SEFAZ call. */
@Service
public class FiscalPendingCommandService {
    private final CommandRepository commands;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final DiscountRepository discounts;
    private final PaymentRepository payments;
    private final FiscalDocumentStartService starter;

    public FiscalPendingCommandService(CommandRepository commands, OrderRepository orders,
                                       OrderItemRepository orderItems, DiscountRepository discounts,
                                       PaymentRepository payments, FiscalDocumentStartService starter) {
        this.commands = commands;
        this.orders = orders;
        this.orderItems = orderItems;
        this.discounts = discounts;
        this.payments = payments;
        this.starter = starter;
    }

    @Transactional
    public FiscalDocument start(int number) {
        Command command = commands.findByNumber(number)
                .orElseThrow(() -> new CommandNotFoundException(number));
        // start() locks the visit and reuses its sole untouched placeholder.
        // The outer transaction rolls a newly inserted row back if any guard fails.
        FiscalDocument document = starter.start(command.getId());
        if (!commands.findByNumber(number).orElseThrow().getId().equals(command.getId())) {
            throw ineligible("The comanda has a newer visit");
        }
        if (orders.findByCommandOrderByCreatedAtAsc(command).stream()
                .anyMatch(order -> order.getStatus() == OrderStatus.CREATED)) {
            throw ineligible("Every order must be confirmed before fiscal preparation");
        }
        BigDecimal gross = orderItems.sumOwedByCommand(command, OrderStatus.CANCELLED);
        BigDecimal due = gross.subtract(discounts.sumDiscountedAmountByCommand(command));
        if (gross.signum() <= 0 || due.signum() <= 0
                || payments.sumAmountByCommand(command).compareTo(due) < 0) {
            throw ineligible("A positive, fully paid sale is required");
        }
        return document;
    }

    private static FiscalDocumentStartException ineligible(String message) {
        return new FiscalDocumentStartException("FISCAL_DOCUMENT_NOT_ELIGIBLE", message);
    }
}
