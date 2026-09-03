package com.farelo.api.notification;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.outbox.OutboxWorker;
import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link OrderReadyNotificationService#createForOrder(UUID)}'s
 * database-only creation logic directly (FARELO-112) — deliberately calls
 * the service directly instead of going through {@code
 * OutboxWorker#processPendingEvents()} (the way {@code
 * OutboxWorkerOrderReadyIntegrationTests} does), same scoping decision
 * {@code PrintJobServiceIntegrationTests} already made for the equivalent
 * {@code PrintJobService#createForOrder}: this class's scope is strictly
 * "does the right {@code Notification} row (or no row) get created from an
 * order's current customer fields", not the outbox dispatch mechanism
 * (already covered elsewhere) or the {@code READY} status transition
 * itself. Orders here are never actually transitioned to {@code READY} —
 * this service doesn't read {@code Order.status} at all (see its javadoc).
 *
 * <p>Orders are created via {@link OrderService#create}, which itself
 * publishes an {@code OrderCreated} outbox event — the {@code @AfterEach}
 * below drains it via {@link OutboxWorker#processPendingEvents()} before
 * deleting the order, same anti-orphan-event reasoning documented in {@code
 * PrintJobServiceIntegrationTests}' javadoc. That drain incidentally also
 * creates a {@link PrintJob} (via {@code OutboxWorker}'s other consumer,
 * {@code PrintJobService}) — harmless, cleaned up the same way that class
 * already does.
 *
 * <p>Uses dedicated seeded command number 40 — distinct from every number
 * already spoken for elsewhere (1-20, 30-34, 101, 999 — see {@code
 * OutboxWorkerPrintJobIntegrationTests}'/{@code
 * InventoryMovementServiceIntegrationTests}' javadocs).
 */
@SpringBootTest
class OrderReadyNotificationServiceIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 40;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OrderReadyNotificationService orderReadyNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PrintJobRepository printJobRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    private UUID createdCategoryId;
    private UUID createdProductId;
    private UUID createdOrderId;

    @AfterEach
    void cleanUp() {
        if (createdOrderId != null) {
            outboxWorker.processPendingEvents();

            Order order = orderRepository.findById(createdOrderId).orElseThrow();
            notificationRepository.findAll().stream()
                    .filter(n -> n.getRecipient().equals(order.getCustomerPhone()))
                    .forEach(notificationRepository::delete);
            printJobRepository.findByOrder(order).forEach(printJobRepository::delete);
            orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order)
                    .forEach(orderStatusHistoryRepository::delete);
            orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
            orderRepository.delete(order);
            createdOrderId = null;
        }
        if (createdProductId != null) {
            productRepository.deleteById(createdProductId);
            createdProductId = null;
        }
        if (createdCategoryId != null) {
            categoryRepository.deleteById(createdCategoryId);
            createdCategoryId = null;
        }

        Command command = commandRepository.findByNumber(COMMAND_NUMBER).orElseThrow();
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    private OrderWithItems createOrder(String customerName, String customerPhone) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        createdCategoryId = category.getId();
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("5.00"), category));
        createdProductId = product.getId();

        OrderWithItems result = orderService.create(
                COMMAND_NUMBER, List.of(new NewOrderItem(product.getId(), 1)), customerName, customerPhone);
        createdOrderId = result.order().getId();
        return result;
    }

    @Test
    void createsPendingOrderReadyNotificationWhenCustomerPhonePresent() {
        OrderWithItems result = createOrder("Maria", "5511999999999");

        Optional<Notification> created = orderReadyNotificationService.createForOrder(result.order().getId());

        assertThat(created).isPresent();
        Notification notification = created.get();
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_READY);
        assertThat(notification.getRecipient()).isEqualTo("5511999999999");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getContent())
                .contains("Maria")
                .contains(String.valueOf(COMMAND_NUMBER));

        assertThat(notificationRepository.findById(notification.getId())).isPresent();
    }

    @Test
    void createsNotificationWithoutCustomerNameWhenOnlyPhoneIsPresent() {
        OrderWithItems result = createOrder(null, "5511988888888");

        Optional<Notification> created = orderReadyNotificationService.createForOrder(result.order().getId());

        assertThat(created).isPresent();
        assertThat(created.get().getContent())
                .doesNotContain("Olá,")
                .contains(String.valueOf(COMMAND_NUMBER));
    }

    @Test
    void createsNoNotificationWhenCustomerPhoneIsNull() {
        OrderWithItems result = createOrder("Maria", null);

        Optional<Notification> created = orderReadyNotificationService.createForOrder(result.order().getId());

        assertThat(created).isEmpty();
    }

    @Test
    void createsNoNotificationWhenCustomerPhoneIsBlank() {
        OrderWithItems result = createOrder("Maria", "   ");

        Optional<Notification> created = orderReadyNotificationService.createForOrder(result.order().getId());

        assertThat(created).isEmpty();
    }

    @Test
    void throwsOrderNotFoundExceptionForUnknownOrderId() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> orderReadyNotificationService.createForOrder(unknownId))
                .isInstanceOf(OrderNotFoundException.class);
    }

}
