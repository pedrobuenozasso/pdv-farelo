package com.farelo.api.command;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountRepository;
import com.farelo.api.discount.DiscountService;
import com.farelo.api.discount.DiscountType;
import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemCancelReason;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommandCycleIntegrationTests extends AbstractIntegrationTest {

    @Autowired private CommandRepository commandRepository;
    @Autowired private CommandService commandService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private DiscountRepository discountRepository;
    @Autowired private DiscountService discountService;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;

    @Test
    void reopeningPhysicalNumberKeepsEachVisitsFinancialHistorySeparate() {
        int number = 200000 + Math.floorMod(UUID.randomUUID().hashCode(), 100000);
        Command first = commandRepository.save(new Command(number));
        Command second = null;
        Order firstOrder = null;
        Payment firstPayment = null;
        Payment secondPayment = null;
        Discount firstDiscount = null;
        FiscalDocument firstFiscal = null;
        try {
            first = commandService.open(number);
            firstOrder = orderRepository.save(new Order(first));
            firstPayment = paymentRepository.save(new Payment(first, new BigDecimal("10.00"), PaymentMethod.OTHER));
            firstDiscount = discountRepository.save(new Discount(first, DiscountType.FIXED_AMOUNT,
                    null, new BigDecimal("10.00"), BigDecimal.ONE, "fixture", UUID.randomUUID(), "Teste"));
            firstFiscal = fiscalDocumentRepository.save(new FiscalDocument(first));

            assertThat(paymentService.getTotalPaid(number)).isEqualByComparingTo("10.00");
            assertThat(discountService.getTotalDiscount(number)).isEqualByComparingTo("1.00");
            paymentService.closeCommand(number);

            second = commandService.open(number);
            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(second.getCycleNumber()).isEqualTo(2);
            assertThat(second.getStatus()).isEqualTo(CommandStatus.OPEN);
            assertThat(commandRepository.findByNumber(number)).contains(second);
            assertThat(commandService.listAll()).filteredOn(c -> c.getNumber() == number)
                    .containsExactly(second);
            assertThat(orderRepository.findByCommandOrderByCreatedAtAsc(second)).isEmpty();
            assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(second)).isEmpty();
            assertThat(discountRepository.findByCommandOrderByCreatedAtAsc(second)).isEmpty();
            assertThat(fiscalDocumentRepository.findByCommandOrderByCreatedAtAsc(second)).isEmpty();
            assertThat(paymentService.getTotalPaid(number)).isEqualByComparingTo("0.00");
            assertThat(discountService.getTotalDiscount(number)).isEqualByComparingTo("0.00");

            secondPayment = paymentRepository.save(new Payment(second, new BigDecimal("2.00"), PaymentMethod.OTHER));
            assertThat(paymentService.getTotalPaid(number)).isEqualByComparingTo("2.00");
            assertThat(commandService.listVisits(number)).extracting(Command::getCycleNumber)
                    .containsExactly(2, 1);
            assertThat(orderRepository.findByCommandOrderByCreatedAtAsc(first)).containsExactly(firstOrder);
            assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(first)).containsExactly(firstPayment);
            assertThat(fiscalDocumentRepository.findByCommandOrderByCreatedAtAsc(first)).containsExactly(firstFiscal);
        } finally {
            if (firstFiscal != null) fiscalDocumentRepository.deleteById(firstFiscal.getId());
            if (firstDiscount != null) discountRepository.deleteById(firstDiscount.getId());
            if (secondPayment != null) paymentRepository.deleteById(secondPayment.getId());
            if (firstPayment != null) paymentRepository.deleteById(firstPayment.getId());
            if (firstOrder != null) orderRepository.deleteById(firstOrder.getId());
            if (second != null) commandRepository.deleteById(second.getId());
            commandRepository.deleteById(first.getId());
        }
    }

    @Test
    void historicalOrderCannotBeChangedOrDiscountedAfterNumberIsReused() {
        int number = 300000 + Math.floorMod(UUID.randomUUID().hashCode(), 100000);
        Command first = commandRepository.save(new Command(number));
        Command second = null;
        Category category = null;
        Product product = null;
        Order order = null;
        OrderItem item = null;
        Payment payment = null;
        try {
            first = commandService.open(number);
            category = categoryRepository.save(new Category("Teste de ciclo"));
            product = productRepository.save(new Product("Item de ciclo", new BigDecimal("10.00"), category));
            order = orderRepository.save(new Order(first));
            item = orderItemRepository.save(new OrderItem(order, product, 1, new BigDecimal("10.00")));
            payment = paymentRepository.save(new Payment(first, new BigDecimal("10.00"), PaymentMethod.OTHER));
            paymentService.closeCommand(number);
            second = commandService.open(number);

            UUID orderId = order.getId();
            UUID itemId = item.getId();
            assertThatThrownBy(() -> discountService.applyFixedAmountToItem(
                    orderId, itemId, BigDecimal.ONE, "old visit", UUID.randomUUID()))
                    .isInstanceOf(CommandVisitNotCurrentException.class);
            assertThatThrownBy(() -> orderService.cancelItem(
                    orderId, itemId, OrderItemCancelReason.OTHER, "old visit", UUID.randomUUID()))
                    .isInstanceOf(CommandVisitNotCurrentException.class);
            assertThatThrownBy(() -> orderService.duplicateItem(orderId, itemId))
                    .isInstanceOf(CommandVisitNotCurrentException.class);
            assertThatThrownBy(() -> orderService.markAsPreparing(orderId))
                    .isInstanceOf(CommandVisitNotCurrentException.class);
            assertThat(orderItemRepository.findById(itemId).orElseThrow().isCancelled()).isFalse();
            assertThat(discountRepository.findByCommandOrderByCreatedAtAsc(second)).isEmpty();
        } finally {
            if (item != null) orderItemRepository.deleteById(item.getId());
            if (order != null) orderRepository.deleteById(order.getId());
            if (payment != null) paymentRepository.deleteById(payment.getId());
            if (product != null) productRepository.deleteById(product.getId());
            if (category != null) categoryRepository.deleteById(category.getId());
            if (second != null) commandRepository.deleteById(second.getId());
            commandRepository.deleteById(first.getId());
        }
    }

    @Test
    void simultaneousReopenCreatesOnlyOneNextVisit() throws Exception {
        int number = 400000 + Math.floorMod(UUID.randomUUID().hashCode(), 100000);
        Command first = commandRepository.save(new Command(number));
        try {
            commandService.open(number);
            commandService.close(number);
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Boolean> one = CompletableFuture.supplyAsync(() -> reopen(number, start));
            CompletableFuture<Boolean> two = CompletableFuture.supplyAsync(() -> reopen(number, start));
            start.countDown();

            assertThat(one.get() != two.get()).isTrue();
            assertThat(commandService.listVisits(number)).extracting(Command::getCycleNumber)
                    .containsExactly(2, 1);
            assertThat(commandService.findByNumber(number).getStatus()).isEqualTo(CommandStatus.OPEN);
        } finally {
            commandRepository.findByNumberOrderByCycleNumberDesc(number)
                    .forEach(command -> commandRepository.deleteById(command.getId()));
        }
    }

    private boolean reopen(int number, CountDownLatch start) {
        try {
            start.await();
            commandService.open(number);
            return true;
        } catch (CommandNotAvailableException expected) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
