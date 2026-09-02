package com.farelo.api.outbox;

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
import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobRepository;
import com.farelo.api.printing.PrintJobStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link OutboxWorker}'s first real consumer wiring (FARELO-072):
 * dispatching an {@code OrderCreated} event results in a {@code PENDING}
 * {@link PrintJob} for that order, with the expected content — and proves
 * the documented failure behavior (whole-batch rollback, see {@code
 * OutboxWorker}'s "Failure handling" javadoc).
 *
 * <p>Relies on {@link AbstractIntegrationTest} disabling {@code
 * OutboxWorker}'s real {@code @Scheduled} trigger suite-wide — without
 * that, a worker from another cached context could race this test's
 * explicit {@link OutboxWorker#processPendingEvents()} calls over the same
 * rows (including the deliberately-broken event seeded below), making both
 * the "found exactly one PrintJob" and "the event stayed PENDING after a
 * failure" assertions nondeterministic. This test class is exactly what
 * surfaced that (see that class's javadoc for the full story).
 *
 * <p>Uses dedicated seeded command number 18 — distinct from every number
 * already spoken for elsewhere (see {@code
 * CommandOrdersControllerIntegrationTests}' javadoc: 1-7, 8, 9, 10-12, 14,
 * 15, 16, 17, 101, 999).
 *
 * <p><strong>Cleans up everything it creates</strong> (print job, order
 * item, order status history, order, products, categories) — unlike some
 * older tests in this suite (e.g. {@code
 * CommandOrdersControllerIntegrationTests}), which leave their {@code
 * Product} rows behind. Those got away with it only because of incidental
 * test-class run order (they happen to run after {@code
 * ProductControllerIntegrationTests}' {@code @BeforeEach} wipes {@code
 * product}/{@code category}). This class doesn't rely on that: a
 * left-behind {@code Product} still referenced by an {@code OrderItem}/
 * {@code PrintJob} makes that {@code deleteAll()} fail on a real {@code FK}
 * violation the moment run order shifts even slightly — which is exactly
 * what happened while writing this test.
 */
@SpringBootTest
class OutboxWorkerPrintJobIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 18;

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
    private PrintJobRepository printJobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private UUID createdOrderId;

    @AfterEach
    void cleanUp() {
        if (createdOrderId != null) {
            Order order = orderRepository.findById(createdOrderId).orElseThrow();
            printJobRepository.findByOrder(order).forEach(printJobRepository::delete);
            orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order)
                    .forEach(orderStatusHistoryRepository::delete);
            orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
            orderRepository.delete(order);
            createdOrderId = null;
        }
        createdProductIds.forEach(productRepository::deleteById);
        createdProductIds.clear();
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdCategoryIds.clear();

        Command command = commandRepository.findByNumber(COMMAND_NUMBER).orElseThrow();
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    private Product createActiveProduct(String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        createdCategoryIds.add(category.getId());
        Product product = productRepository.save(new Product(name, price, category));
        createdProductIds.add(product.getId());
        return product;
    }

    @Test
    void dispatchingOrderCreatedEventCreatesPendingPrintJobWithOrderContent() {
        Product espresso = createActiveProduct("Café Espresso", new BigDecimal("5.00"));
        Product croissant = createActiveProduct("Croissant", new BigDecimal("9.00"));

        OrderWithItems result = orderService.create(
                COMMAND_NUMBER,
                List.of(new NewOrderItem(espresso.getId(), 2), new NewOrderItem(croissant.getId(), 1)),
                null, null);
        Order order = result.order();
        createdOrderId = order.getId();

        outboxWorker.processPendingEvents();

        List<PrintJob> printJobs = printJobRepository.findByOrder(order);
        assertThat(printJobs).hasSize(1);

        PrintJob printJob = printJobs.get(0);
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(printJob.getOrder().getId()).isEqualTo(order.getId());

        JsonNode content = readTree(printJob.getContent());
        assertThat(content.get("commandNumber").asInt()).isEqualTo(COMMAND_NUMBER);

        List<JsonNode> items = new ArrayList<>();
        content.get("items").forEach(items::add);
        assertThat(items).hasSize(2);
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("productName").asText()).isEqualTo("Café Espresso");
            assertThat(item.get("quantity").asInt()).isEqualTo(2);
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("productName").asText()).isEqualTo("Croissant");
            assertThat(item.get("quantity").asInt()).isEqualTo(1);
        });

        // The dispatched OrderCreated event itself ends PROCESSED — same
        // generic effect OutboxWorkerIntegrationTests already proves, now
        // confirmed alongside the real side effect it triggers.
        boolean stillPending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .stream()
                .anyMatch(event -> event.getAggregateId().equals(order.getId()));
        assertThat(stillPending).isFalse();
    }

    // FARELO-072 design decision 3: a dispatch failure (here, an
    // OrderCreated event whose aggregateId matches no real order) is left
    // to propagate out of processPendingEvents() and roll back the whole
    // batch's transaction, rather than being caught/isolated per event —
    // see OutboxWorker's "Failure handling" javadoc for the full
    // rationale. This proves that documented behavior: the exception
    // surfaces to the caller, and the offending event is left PENDING
    // (never committed as PROCESSED) for the next poll cycle to retry.
    @Test
    void dispatchFailureRollsBackTheBatchLeavingTheEventPending() {
        OutboxEvent orphanEvent = outboxEventRepository.saveAndFlush(
                new OutboxEvent("Order", UUID.randomUUID(), "OrderCreated", "{}"));

        try {
            assertThatThrownBy(() -> outboxWorker.processPendingEvents())
                    .isInstanceOf(OrderNotFoundException.class);

            OutboxEvent reloaded = outboxEventRepository.findById(orphanEvent.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(reloaded.getProcessedAt()).isNull();
        } finally {
            outboxEventRepository.deleteById(orphanEvent.getId());
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse PrintJob content as JSON", e);
        }
    }

}
