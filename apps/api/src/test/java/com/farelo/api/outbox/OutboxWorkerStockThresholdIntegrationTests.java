package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.InventoryMovementService;
import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationRepository;
import com.farelo.api.notification.NotificationStatus;
import com.farelo.api.notification.NotificationType;
import com.farelo.api.notification.NotificationWorker;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Verifies the full FARELO-113 pipeline end to end, against real Postgres
 * (Testcontainers) and a local HTTP stub standing in for the Meta WhatsApp
 * Cloud API: a stock-reducing movement that crosses a threshold ({@link
 * InventoryMovementService#recordLoss}, already publishing {@code
 * STOCK_LOW}/{@code OUT_OF_STOCK} outbox events since FARELO-100/101)
 * dispatches via {@link OutboxWorker#processPendingEvents()} to {@code
 * StockThresholdNotificationService}, creating a {@code PENDING} {@link
 * Notification} addressed to the configured {@code
 * notification.internal-alert-recipient}; and {@link
 * NotificationWorker#processPendingNotifications()} drains it to {@code
 * SENT} via the real (stub-backed) {@code WhatsAppCloudApiClient} bean —
 * same overall shape as {@code OutboxWorkerOrderReadyIntegrationTests}
 * (FARELO-112), adapted for this ticket's own trigger and recipient source.
 *
 * <p>Every stage is exercised via an explicit, direct method call — never a
 * real-time wait for the background {@code @Scheduled} triggers (both
 * disabled suite-wide, see {@code AbstractIntegrationTest}'s javadoc).
 */
@SpringBootTest
class OutboxWorkerStockThresholdIntegrationTests extends AbstractIntegrationTest {

    private static final String ALERT_RECIPIENT = "5511900000000";

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
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.api.base-url", () -> "http://localhost:" + stubServer.getAddress().getPort());
        registry.add("whatsapp.api.phone-number-id", () -> "654321");
        registry.add("whatsapp.api.access-token", () -> "test-access-token");
        registry.add("notification.internal-alert-recipient", () -> ALERT_RECIPIENT);
    }

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private InventoryMovementService inventoryMovementService;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationWorker notificationWorker;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID createdIngredientId;

    // FARELO-127: InventoryMovementService#create/#recordLoss now require a
    // real actorId (see InventoryMovementServiceIntegrationTests' javadoc
    // for why) — this class's own tests are about the outbox/notification
    // pipeline, not auditing, so a single reused actor is enough.
    private UUID actorId;

    @BeforeEach
    void resetStubToSucceed() {
        nextResponseStatus.set(200);
    }

    @BeforeEach
    void createActor() {
        User actor = userRepository.save(new User(
                "Test Actor (OutboxWorkerStockThresholdIntegrationTests)",
                "test-actor-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode("senha-forte-123"),
                UserRole.ADMIN));
        actorId = actor.getId();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().equals(ALERT_RECIPIENT))
                .forEach(notificationRepository::delete);
        // InventoryMovement rows for createdIngredientId are intentionally
        // left in place — same "append-only, never cleaned up by tests"
        // posture already established by InventoryMovementRepositoryIntegrationTests
        // (each test scopes its own assertions to its own ingredient id).
        createdIngredientId = null;
    }

    private Ingredient createIngredientWithMinimumStock(String name, IngredientUnit unit, BigDecimal minimumStock) {
        Ingredient ingredient = new Ingredient(name, unit);
        ingredient.setMinimumStock(minimumStock);
        Ingredient saved = ingredientRepository.save(ingredient);
        createdIngredientId = saved.getId();
        return saved;
    }

    @Test
    void stockLowEventuallyResultsInASentInternalNotification() {
        Ingredient coffee = createIngredientWithMinimumStock(
                "Café em grão (FARELO-113 outbox stock-low)", IngredientUnit.GRAM, new BigDecimal("500"));
        inventoryMovementService.create(coffee.getId(), new BigDecimal("1000"), actorId);

        // Drains the PURCHASE-caused OrderCreated/other events, if any —
        // none expected here (create() never publishes a stock-threshold
        // event, see publishStockThresholdEventIfNeeded's javadoc), but
        // draining first keeps this test's own dispatch below scoped to
        // exactly the event the LOSS below produces.
        outboxWorker.processPendingEvents();

        // 1000 - 600 = 400, below the 500 threshold but still positive —
        // publishes STOCK_LOW (see InventoryMovementServiceIntegrationTests'
        // equivalent unit-level test for the payload shape itself).
        inventoryMovementService.recordLoss(coffee.getId(), new BigDecimal("600"), actorId);

        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("STOCK_LOW"));

        Optional<Notification> maybeNotification = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().equals(ALERT_RECIPIENT))
                .filter(n -> n.getContent().contains(coffee.getName()))
                .findFirst();
        assertThat(maybeNotification).isPresent();

        Notification notification = maybeNotification.get();
        assertThat(notification.getType()).isEqualTo(NotificationType.STOCK_LOW);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getContent()).contains("400").contains("500");

        notificationWorker.processPendingNotifications();

        Notification sent = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void outOfStockEventuallyResultsInASentInternalNotification() {
        Ingredient milk = createIngredientWithMinimumStock(
                "Leite (FARELO-113 outbox out-of-stock)", IngredientUnit.MILLILITER, new BigDecimal("500"));
        inventoryMovementService.create(milk.getId(), new BigDecimal("1000"), actorId);
        outboxWorker.processPendingEvents();

        // Drains the balance to exactly 0 — OUT_OF_STOCK, not STOCK_LOW
        // (precedence already proven at the service-level test; this test's
        // job is only the outbox-to-notification wiring).
        inventoryMovementService.recordLoss(milk.getId(), new BigDecimal("1000"), actorId);

        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("OUT_OF_STOCK"));

        Optional<Notification> maybeNotification = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().equals(ALERT_RECIPIENT))
                .filter(n -> n.getContent().contains(milk.getName()))
                .findFirst();
        assertThat(maybeNotification).isPresent();
        assertThat(maybeNotification.get().getType()).isEqualTo(NotificationType.OUT_OF_STOCK);

        notificationWorker.processPendingNotifications();

        Notification sent = notificationRepository.findById(maybeNotification.get().getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void purchaseNeverPublishesAStockThresholdEventOrCreatesANotification() {
        Ingredient tea = createIngredientWithMinimumStock(
                "Chá (FARELO-113 outbox purchase)", IngredientUnit.GRAM, new BigDecimal("500"));

        // A PURCHASE only ever increases stock — can never cross into
        // low/out-of-stock territory (see publishStockThresholdEventIfNeeded's
        // javadoc: create() deliberately never calls it).
        inventoryMovementService.create(tea.getId(), new BigDecimal("100"), actorId);

        List<OutboxEvent> processed = outboxWorker.processPendingEvents();
        assertThat(processed).noneSatisfy(
                event -> assertThat(event.getEventType()).isIn("STOCK_LOW", "OUT_OF_STOCK"));

        boolean anyNotificationForThisIngredient = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getContent().contains(tea.getName()));
        assertThat(anyNotificationForThisIngredient).isFalse();
    }

}
