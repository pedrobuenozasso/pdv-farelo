package com.farelo.api.printing;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandNotFoundException;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemCancelReason;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.outbox.OutboxWorker;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link PrintJobService#createCommandCheck(int)} (FARELO-211) —
 * the "conferência" job's content: which items it includes/excludes and
 * the total. Same "call the service directly, not through HTTP" style as
 * {@link PrintJobServiceIntegrationTests} (station-splitting), since this
 * ticket's scope is the aggregation logic itself; RBAC and the HTTP
 * contract are covered separately by {@code
 * com.farelo.api.printing.web.CommandPrintConferenceControllerIntegrationTests}.
 *
 * <p>Uses dedicated seeded command numbers 78-79 — free per the registry
 * each test class's javadoc maintains (checked against every {@code
 * COMMAND_NUMBER}/{@code SEEDED_COMMAND_NUMBER} constant in the suite at
 * the time this class was written).
 */
@SpringBootTest
class PrintJobServiceCommandCheckIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 78;
    private static final int UNKNOWN_COMMAND_NUMBER = 999_999;
    private static final String PASSWORD = "senha-forte-123";

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
    private PrintJobService printJobService;

    @Autowired
    private PrintJobRepository printJobRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        Command command = commandRepository.findByNumber(COMMAND_NUMBER).orElseThrow();
        // findByCommand, not findAll().filter(job -> job.getCommand()...) —
        // the latter would call .getNumber() on an unfetched lazy proxy
        // outside any session (findAll() has no JOIN FETCH), throwing
        // LazyInitializationException (the FARELO-055 lesson, again).
        printJobRepository.findByCommand(command).forEach(printJobRepository::delete);

        // Drains the OrderCreated outbox events createOrder() below leaves
        // PENDING (this class never calls OutboxWorker itself, unlike
        // production/PrintJobServiceIntegrationTests) — same reasoning as
        // that class's cleanUp javadoc: an event left pointing at an order
        // this cleanup is about to delete would fail a later, unrelated
        // test the moment it drains the real queue.
        outboxWorker.processPendingEvents();

        for (UUID orderId : createdOrderIds) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            printJobRepository.findByOrder(order).forEach(printJobRepository::delete);
            orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order)
                    .forEach(orderStatusHistoryRepository::delete);
            orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
            orderRepository.delete(order);
        }
        createdOrderIds.clear();

        createdProductIds.forEach(productRepository::deleteById);
        createdProductIds.clear();
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdCategoryIds.clear();

        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    private Product createProduct(String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category("Cardápio"));
        createdCategoryIds.add(category.getId());
        Product product = productRepository.save(new Product(name, price, category));
        createdProductIds.add(product.getId());
        return product;
    }

    private Order createOrder(NewOrderItem... items) {
        OrderWithItems result = orderService.create(COMMAND_NUMBER, List.of(items), null, null);
        createdOrderIds.add(result.order().getId());
        return result.order();
    }

    private UUID actorId() {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                UserRole.CASHIER));
        return user.getId();
    }

    @Test
    void createsCommandCheckWithItemsAcrossMultipleOrdersAndTotal() {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"));
        Product croissant = createProduct("Croissant", new BigDecimal("9.00"));

        createOrder(new NewOrderItem(espresso.getId(), 2));
        createOrder(new NewOrderItem(croissant.getId(), 1));

        PrintJob job = printJobService.createCommandCheck(COMMAND_NUMBER);

        assertThat(job.getType()).isEqualTo(PrintJobType.COMMAND_CHECK);
        assertThat(job.getOrder()).isNull();
        assertThat(job.getCommand().getNumber()).isEqualTo(COMMAND_NUMBER);
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);

        CommandCheckContent content = readContent(job);
        assertThat(content.commandNumber()).isEqualTo(COMMAND_NUMBER);
        assertThat(content.items()).hasSize(2);
        assertThat(content.items()).anySatisfy(item -> {
            assertThat(item.productName()).isEqualTo("Café Espresso");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("10.00");
        });
        assertThat(content.total()).isEqualByComparingTo("19.00");
    }

    @Test
    void excludesCancelledItemFromCommandCheck() {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"));
        Product croissant = createProduct("Croissant", new BigDecimal("9.00"));

        Order order = createOrder(
                new NewOrderItem(espresso.getId(), 1),
                new NewOrderItem(croissant.getId(), 1));
        OrderItem croissantItem = orderItemRepository.findByOrder(order).stream()
                .filter(item -> item.getProduct().getId().equals(croissant.getId()))
                .findFirst().orElseThrow();

        orderService.cancelItem(order.getId(), croissantItem.getId(), OrderItemCancelReason.ENTRY_ERROR, null, actorId());

        CommandCheckContent content = readContent(printJobService.createCommandCheck(COMMAND_NUMBER));

        assertThat(content.items()).hasSize(1);
        assertThat(content.items().get(0).productName()).isEqualTo("Café Espresso");
        assertThat(content.total()).isEqualByComparingTo("5.00");
    }

    @Test
    void excludesFullyCancelledOrderFromCommandCheck() {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"));
        Product croissant = createProduct("Croissant", new BigDecimal("9.00"));

        createOrder(new NewOrderItem(espresso.getId(), 1));
        Order cancelledOrder = createOrder(new NewOrderItem(croissant.getId(), 1));
        orderService.markAsCancelled(cancelledOrder.getId());

        CommandCheckContent content = readContent(printJobService.createCommandCheck(COMMAND_NUMBER));

        assertThat(content.items()).hasSize(1);
        assertThat(content.items().get(0).productName()).isEqualTo("Café Espresso");
        assertThat(content.total()).isEqualByComparingTo("5.00");
    }

    @Test
    void throwsCommandNotFoundForUnknownCommandNumber() {
        assertThatThrownBy(() -> printJobService.createCommandCheck(UNKNOWN_COMMAND_NUMBER))
                .isInstanceOf(CommandNotFoundException.class);
    }

    private CommandCheckContent readContent(PrintJob job) {
        try {
            return objectMapper.readValue(job.getContent(), CommandCheckContent.class);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse PrintJob content as CommandCheckContent", e);
        }
    }

}
