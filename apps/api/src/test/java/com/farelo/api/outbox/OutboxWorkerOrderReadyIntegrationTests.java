package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationRepository;
import com.farelo.api.notification.NotificationStatus;
import com.farelo.api.notification.NotificationType;
import com.farelo.api.notification.NotificationWorker;
import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.printing.PrintJobRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the full FARELO-112 pipeline end to end, against real Postgres
 * (Testcontainers) and a local HTTP stub standing in for the Meta WhatsApp
 * Cloud API: an order's {@code PREPARING} → {@code READY} transition
 * ({@code OrderService#markAsReady}) publishes an {@code OrderReady} outbox
 * event in the same transaction; {@link OutboxWorker#processPendingEvents()}
 * dispatches it to {@code OrderReadyNotificationService}, creating a {@code
 * PENDING} {@link Notification}; and {@link
 * NotificationWorker#processPendingNotifications()} drains that notification
 * to {@code SENT} via the real (stub-backed) {@code
 * com.farelo.api.notification.whatsapp.WhatsAppCloudApiClient} bean.
 *
 * <p>Every stage is exercised via an explicit, direct method call — {@code
 * processPendingEvents()}, then {@code processPendingNotifications()} — never
 * a real-time wait/poll/sleep for the background {@code @Scheduled}
 * triggers. This follows the same anti-flakiness convention already
 * established across this suite for {@code OutboxWorker} (see {@code
 * AbstractIntegrationTest}'s javadoc: both workers' real schedules are
 * disabled suite-wide precisely so every test controls exactly when/how much
 * gets processed, deterministically) — "eventually SENT" here means "after
 * calling both worker methods once, synchronously", not any race against
 * wall-clock time.
 *
 * <p>Uses dedicated seeded command numbers 41 (with phone) / 42 (without
 * phone) / 43 (other-transition case) — distinct from every number already
 * spoken for elsewhere (1-20, 30-34, 40, 101, 999 — see {@code
 * OrderReadyNotificationServiceIntegrationTests}' javadoc for 40).
 */
@SpringBootTest
class OutboxWorkerOrderReadyIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_PHONE = 41;
    private static final int COMMAND_WITHOUT_PHONE = 42;
    private static final int COMMAND_OTHER_TRANSITION = 43;

    private static HttpServer stubServer;
    private static final AtomicInteger nextResponseStatus = new AtomicInteger(200);

    static {
        try {
            stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stubServer.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                int status = nextResponseStatus.get();
                byte[] response = status == 200
                        ? "{\"messages\":[{\"id\":\"wamid.TEST\"}]}".getBytes(StandardCharsets.UTF_8)
                        : "{\"error\":{\"message\":\"stub failure\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            stubServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void overrideWhatsAppProperties(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.api.base-url", () -> "http://localhost:" + stubServer.getAddress().getPort());
        registry.add("whatsapp.api.phone-number-id", () -> "654321");
        registry.add("whatsapp.api.access-token", () -> "test-access-token");
    }

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
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationWorker notificationWorker;

    @Autowired
    private PrintJobRepository printJobRepository;

    private UUID createdCategoryId;
    private UUID createdProductId;
    private UUID createdOrderId;
    private int usedCommandNumber;

    @BeforeEach
    void resetStubToSucceed() {
        nextResponseStatus.set(200);
    }

    @AfterEach
    void cleanUp() {
        if (createdOrderId != null) {
            Order order = orderRepository.findById(createdOrderId).orElseThrow();
            notificationRepository.findAll().stream()
                    .filter(n -> n.getRecipient() != null && n.getRecipient().equals(order.getCustomerPhone()))
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

        Command command = commandRepository.findByNumber(usedCommandNumber).orElseThrow();
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    private OrderWithItems createOrder(int commandNumber, String customerName, String customerPhone) {
        usedCommandNumber = commandNumber;
        Category category = categoryRepository.save(new Category("Bebidas"));
        createdCategoryId = category.getId();
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("5.00"), category));
        createdProductId = product.getId();

        OrderWithItems result = orderService.create(
                commandNumber, List.of(new NewOrderItem(product.getId(), 1)), customerName, customerPhone);
        createdOrderId = result.order().getId();
        return result;
    }

    @Test
    void orderReadyWithPhoneEventuallyResultsInASentNotification() {
        OrderWithItems created = createOrder(COMMAND_WITH_PHONE, "Maria", "5511999999999");
        UUID orderId = created.order().getId();

        // OrderCreated event published by create() above.
        outboxWorker.processPendingEvents();

        orderService.markAsPreparing(orderId);
        orderService.markAsReady(orderId);

        // Drains the OrderReady event this test's whole point is about —
        // dispatches to OrderReadyNotificationService, creating a PENDING
        // Notification (a pure DB write, no HTTP call yet).
        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("OrderReady"));

        List<Notification> allNotifications = notificationRepository.findAll();
        Optional<Notification> maybeNotification = allNotifications.stream()
                .filter(n -> n.getRecipient().equals("5511999999999"))
                .findFirst();
        assertThat(maybeNotification).isPresent();

        Notification notification = maybeNotification.get();
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_READY);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getContent()).contains("Maria").contains(String.valueOf(COMMAND_WITH_PHONE));

        // Second, independent stage: NotificationWorker actually sends it
        // (real outbound HTTP call, to the local stub) and persists the
        // outcome — proving "eventually SENT" deterministically via one
        // direct call, not a wall-clock wait.
        notificationWorker.processPendingNotifications();

        Notification sent = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void orderReadyWithoutPhoneCreatesNoNotificationAndDoesNotFailTheTransition() {
        OrderWithItems created = createOrder(COMMAND_WITHOUT_PHONE, "João", null);
        UUID orderId = created.order().getId();

        outboxWorker.processPendingEvents();

        orderService.markAsPreparing(orderId);
        OrderWithItems ready = orderService.markAsReady(orderId);
        assertThat(ready.order().getStatus().name()).isEqualTo("READY");

        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("OrderReady"));

        // The OrderReady event itself must still end up PROCESSED (not
        // stuck PENDING) even though it produced no Notification — see
        // OrderReadyNotificationService's javadoc: "no customerPhone" is a
        // legitimate outcome, not a dispatch failure.
        boolean stillPending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .stream()
                .anyMatch(event -> event.getAggregateId().equals(orderId));
        assertThat(stillPending).isFalse();

        boolean anyNotificationForThisOrder = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getContent().contains(String.valueOf(COMMAND_WITHOUT_PHONE)));
        assertThat(anyNotificationForThisOrder).isFalse();
    }

    @Test
    void transitioningToPreparingOnlyDoesNotPublishOrderReadyOrCreateANotification() {
        OrderWithItems created = createOrder(COMMAND_OTHER_TRANSITION, "Ana", "5511977777777");
        UUID orderId = created.order().getId();

        outboxWorker.processPendingEvents();

        orderService.markAsPreparing(orderId);

        // markAsPreparing never publishes OrderReady — nothing new to
        // drain, and in particular no Notification gets created.
        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).isEmpty();

        boolean anyNotificationForThisRecipient = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getRecipient().equals("5511977777777"));
        assertThat(anyNotificationForThisRecipient).isFalse();
    }

}
