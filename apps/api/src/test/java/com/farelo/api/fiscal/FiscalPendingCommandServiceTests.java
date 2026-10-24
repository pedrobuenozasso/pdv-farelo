package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.discount.DiscountRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.MockMakers.SUBCLASS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FiscalPendingCommandServiceTests {
    private final CommandRepository commands = mock(CommandRepository.class, withSettings().mockMaker(SUBCLASS));
    private final OrderRepository orders = mock(OrderRepository.class, withSettings().mockMaker(SUBCLASS));
    private final OrderItemRepository items = mock(OrderItemRepository.class, withSettings().mockMaker(SUBCLASS));
    private final DiscountRepository discounts = mock(DiscountRepository.class, withSettings().mockMaker(SUBCLASS));
    private final PaymentRepository payments = mock(PaymentRepository.class, withSettings().mockMaker(SUBCLASS));
    private final FiscalDocumentStartService starter = mock(FiscalDocumentStartService.class,
            withSettings().mockMaker(SUBCLASS));
    private final FiscalPendingCommandService service = new FiscalPendingCommandService(
            commands, orders, items, discounts, payments, starter);

    @Test
    void acceptsPositiveSettledSaleWithoutRequiringFiscalProfile() {
        Command command = command();
        FiscalDocument document = new FiscalDocument(command);
        when(commands.findByNumber(command.getNumber())).thenReturn(Optional.of(command));
        when(starter.start(command.getId())).thenReturn(document);
        when(orders.findByCommandOrderByCreatedAtAsc(command)).thenReturn(List.of());
        when(items.sumOwedByCommand(command, OrderStatus.CANCELLED)).thenReturn(new BigDecimal("12.00"));
        when(discounts.sumDiscountedAmountByCommand(command)).thenReturn(new BigDecimal("2.00"));
        when(payments.sumAmountByCommand(command)).thenReturn(new BigDecimal("10.00"));

        assertThat(service.start(command.getNumber())).isSameAs(document);
        verify(starter).start(command.getId());
    }

    @Test
    void rejectsUnconfirmedOrder() {
        Command command = command();
        Order order = new Order(command);
        when(commands.findByNumber(command.getNumber())).thenReturn(Optional.of(command));
        when(starter.start(command.getId())).thenReturn(new FiscalDocument(command));
        when(orders.findByCommandOrderByCreatedAtAsc(command)).thenReturn(List.of(order));

        assertThatThrownBy(() -> service.start(command.getNumber()))
                .isInstanceOf(FiscalDocumentStartException.class)
                .hasMessageContaining("confirmed");
    }

    @Test
    void rejectsStaleVisitAfterAcquiringLock() {
        Command command = command();
        Command newer = new Command(command.getNumber(), 2);
        ReflectionTestUtils.setField(newer, "id", UUID.randomUUID());
        when(commands.findByNumber(command.getNumber()))
                .thenReturn(Optional.of(command), Optional.of(newer));
        when(starter.start(command.getId())).thenReturn(new FiscalDocument(command));

        assertThatThrownBy(() -> service.start(command.getNumber()))
                .isInstanceOf(FiscalDocumentStartException.class)
                .hasMessageContaining("newer visit");
    }

    @Test
    void rejectsZeroValueOrInsufficientPayment() {
        Command command = command();
        when(commands.findByNumber(command.getNumber())).thenReturn(Optional.of(command));
        when(starter.start(command.getId())).thenReturn(new FiscalDocument(command));
        when(orders.findByCommandOrderByCreatedAtAsc(command)).thenReturn(List.of());
        when(items.sumOwedByCommand(command, OrderStatus.CANCELLED)).thenReturn(new BigDecimal("12.00"));
        when(discounts.sumDiscountedAmountByCommand(command)).thenReturn(BigDecimal.ZERO);
        when(payments.sumAmountByCommand(command)).thenReturn(new BigDecimal("11.99"));

        assertThatThrownBy(() -> service.start(command.getNumber()))
                .isInstanceOf(FiscalDocumentStartException.class)
                .hasMessageContaining("fully paid");
    }

    private static Command command() {
        Command command = new Command(90_001);
        command.setStatus(CommandStatus.CLOSED);
        ReflectionTestUtils.setField(command, "id", UUID.randomUUID());
        return command;
    }
}
