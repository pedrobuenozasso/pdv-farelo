package com.farelo.api.fiscal;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductStep;
import com.farelo.api.catalog.ProductStepOption;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountType;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfceCommandDraftServiceTests {
    private final NfceCommandDraftService service = new NfceCommandDraftService(null, null, null, null, null);
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void allocatesItemAndCommandDiscountsWithExactCentsAndMatchesLedger() {
        Command command = closedCommand();
        Order order = persistedOrder(command);
        OrderItem first = item(order, "Café", "10.00", FIRST);
        OrderItem second = item(order, "Bolo", "20.00", SECOND);
        Discount itemDiscount = discount(command, first, "1.00");
        Discount commandDiscount = discount(command, null, "3.00");

        NfceXmlBuilder.Result result = service.assemble(command, List.of(second, first),
                List.of(itemDiscount, commandDiscount), List.of(payment(command, "26.00")), header());

        assertTrue(result.unsignedXml().contains("<vProd>30.00</vProd>"));
        assertTrue(result.unsignedXml().contains("<vDesc>1.93</vDesc>"));
        assertTrue(result.unsignedXml().contains("<vDesc>2.07</vDesc>"));
        assertTrue(result.unsignedXml().contains("<vDesc>4.00</vDesc>"));
        assertTrue(result.unsignedXml().contains("<vNF>26.00</vNF>"));
        assertTrue(result.unsignedXml().contains("<vPag>26.00</vPag>"));
    }

    @Test
    void refusesUnbalancedOrExcessiveDiscountLedger() {
        Command command = closedCommand();
        OrderItem sold = item(persistedOrder(command), "Café", "10.00", FIRST);
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(discount(command, sold, "10.01")), List.of(payment(command, "0.01")), header()));
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(discount(command, null, "10.01")), List.of(payment(command, "0.01")), header()));
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(discount(command, null, "2.00")), List.of(payment(command, "10.00")), header()));
    }

    @Test
    void refusesOptionsAndIncompleteHistoricalSnapshots() {
        Command command = closedCommand();
        Order order = persistedOrder(command);
        OrderItem sold = item(order, "Café", "10.00", FIRST);
        ProductStep step = new ProductStep(sold.getProduct(), "Extra", 0, 1, 0, "ITEM", "FIXED");
        ReflectionTestUtils.setField(step, "id", UUID.randomUUID());
        ProductStepOption link = new ProductStepOption(step, sold.getProduct(), 0, BigDecimal.ONE);
        ReflectionTestUtils.setField(link, "id", UUID.randomUUID());
        sold.addOption(link, 1);
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(), List.of(payment(command, "10.00")), header()));
        OrderItem historical = item(order, "Café", "10.00", SECOND);
        ReflectionTestUtils.setField(historical, "fiscalNcmSnapshot", null);
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(historical),
                List.of(), List.of(payment(command, "10.00")), header()));
    }

    @Test
    void refusesForeignPaymentAndNonClosedCommand() {
        Command command = closedCommand();
        OrderItem sold = item(persistedOrder(command), "Café", "10.00", FIRST);
        Payment foreign = payment(closedCommand(), "10.00");
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(), List.of(foreign), header()));
        command.setStatus(CommandStatus.OPEN);
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(), List.of(payment(command, "10.00")), header()));
    }

    @Test
    void refusesUnconfirmedOrderEvenAfterCommandIsClosed() {
        Command command = closedCommand();
        Order order = persistedOrder(command);
        order.setStatus(OrderStatus.CREATED);
        OrderItem sold = item(order, "Café", "10.00", FIRST);
        assertThrows(IllegalArgumentException.class, () -> service.assemble(command, List.of(sold),
                List.of(), List.of(payment(command, "10.00")), header()));
    }

    @Test
    void largestRemainderDistributionIsStableAndNeverLosesACent() {
        assertEquals(List.of(BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO),
                NfceCommandDraftService.allocate(BigInteger.ONE,
                        List.of(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE)));
        assertEquals(List.of(BigInteger.valueOf(93), BigInteger.valueOf(207)),
                NfceCommandDraftService.allocate(BigInteger.valueOf(300),
                        List.of(BigInteger.valueOf(900), BigInteger.valueOf(2000))));
    }

    private static Command closedCommand() {
        Command command = new Command(1);
        ReflectionTestUtils.setField(command, "id", UUID.randomUUID());
        command.setStatus(CommandStatus.CLOSED);
        return command;
    }

    private static Order persistedOrder(Command command) {
        Order order = new Order(command, null, null);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }

    private static OrderItem item(Order order, String name, String price, UUID id) {
        FiscalProfile profile = new FiscalProfile("Simples");
        profile.setNcm("19059090");
        profile.setCfop("5102");
        profile.setCsosn("102");
        profile.setIcmsOrigin("0");
        profile.setPisCst("07");
        profile.setCofinsCst("07");
        Product product = new Product(name, new BigDecimal(price), null);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setGtin("SEM GTIN");
        product.setFiscalUnit("UN");
        product.setFiscalProfile(profile);
        OrderItem item = new OrderItem(order, product, 1, new BigDecimal(price));
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static Discount discount(Command command, OrderItem item, String amount) {
        Discount discount = new Discount(command, item, DiscountType.FIXED_AMOUNT, null,
                new BigDecimal(amount), new BigDecimal(amount), null, UUID.randomUUID(), "Caixa");
        ReflectionTestUtils.setField(discount, "id", UUID.randomUUID());
        return discount;
    }

    private static Payment payment(Command command, String amount) {
        Payment payment = new Payment(command, new BigDecimal(amount), PaymentMethod.CASH, null);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    private static NfceCommandDraftService.Header header() {
        var address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        var issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        return new NfceCommandDraftService.Header(issuer, "VENDA", 1, 123,
                "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"));
    }
}
