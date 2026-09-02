package com.farelo.api.printing;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.catalog.ProductionStation;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.outbox.OutboxWorker;
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

/**
 * Verifies {@link PrintJobService#createForOrder(UUID)}'s station-splitting
 * logic directly (FARELO-074) — one {@link PrintJob} per {@link
 * ProductionStation} present among an order's items, not one per order.
 *
 * <p>Deliberately calls {@link PrintJobService} directly instead of going
 * through {@code OutboxWorker#processPendingEvents()} (the way {@code
 * OutboxWorkerPrintJobIntegrationTests} does): this ticket's scope is only
 * the grouping/creation logic inside {@code PrintJobService}/{@link
 * PrintJobContent} — {@code OutboxWorker}'s dispatch and failure-rollback
 * behavior is unchanged and already covered there. Going through the
 * outbox layer here too would just add indirection without exercising
 * anything new.
 *
 * <p>Uses dedicated seeded command number 19 — distinct from every number
 * already spoken for elsewhere (see {@code
 * OutboxWorkerPrintJobIntegrationTests}' javadoc: 1-18, 101, 999 already
 * taken; this class takes 19).
 *
 * <p>Cleans up everything it creates, same pattern as {@code
 * OutboxWorkerPrintJobIntegrationTests} (including resetting the shared
 * seeded command back to {@code AVAILABLE} between tests) — a left-behind
 * {@code Product} still referenced by an {@code OrderItem}/{@code
 * PrintJob} would fail a later {@code deleteAll()} elsewhere in the suite
 * on a real FK violation.
 */
@SpringBootTest
class PrintJobServiceIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 19;

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
    private ObjectMapper objectMapper;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private UUID createdOrderId;

    @AfterEach
    void cleanUp() {
        if (createdOrderId != null) {
            // orderService.create(...) publishes an OrderCreated outbox
            // event (FARELO-060) in the same transaction as the order —
            // this test class calls PrintJobService directly and never
            // goes through OutboxWorker, so that event would otherwise be
            // left PENDING forever, pointing at an order this cleanup is
            // about to delete. Draining it here (same effect as the real
            // worker eventually would) avoids leaking a PENDING event
            // whose aggregateId no longer resolves to any order, which
            // would fail a later, unrelated test the moment it drains the
            // real queue (OrderNotFoundException). Harmless double-creates
            // an extra set of PrintJobs for this order via PrintJobService
            // — cleaned up like any other, right below.
            outboxWorker.processPendingEvents();

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

    private Product createProduct(String name, BigDecimal price, ProductionStation station) {
        Category category = categoryRepository.save(new Category("Cardápio"));
        createdCategoryIds.add(category.getId());
        Product product = new Product(name, price, category);
        product.setProductionStation(station);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
    }

    @Test
    void orderWithItemsFromOneStationCreatesExactlyOnePrintJob() {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Product cappuccino = createProduct("Cappuccino", new BigDecimal("8.00"), ProductionStation.BAR);

        Order order = createOrder(
                new NewOrderItem(espresso.getId(), 1),
                new NewOrderItem(cappuccino.getId(), 2));

        List<PrintJob> printJobs = printJobService.createForOrder(order.getId());

        assertThat(printJobs).hasSize(1);
        assertThat(printJobRepository.findByOrder(order)).hasSize(1);

        JsonNode content = readTree(printJobs.get(0).getContent());
        assertThat(content.get("commandNumber").asInt()).isEqualTo(COMMAND_NUMBER);
        assertThat(content.get("productionStation").asText()).isEqualTo("BAR");

        List<JsonNode> items = itemsOf(content);
        assertThat(items).hasSize(2);
        assertThat(items).anySatisfy(item -> assertItem(item, "Café Espresso", 1));
        assertThat(items).anySatisfy(item -> assertItem(item, "Cappuccino", 2));
    }

    @Test
    void orderWithItemsFromDifferentStationsCreatesOnePrintJobPerStation() {
        // Prompt mestre seção 12, literal example: 2 Cappuccino + 1
        // Coca-Cola (BAR) and 1 Croissant (KITCHEN) should split into a
        // BAR ticket and a KITCHEN ticket.
        Product cappuccino = createProduct("Cappuccino", new BigDecimal("8.00"), ProductionStation.BAR);
        Product cocaCola = createProduct("Coca-Cola", new BigDecimal("6.00"), ProductionStation.BAR);
        Product croissant = createProduct("Croissant", new BigDecimal("9.00"), ProductionStation.KITCHEN);

        Order order = createOrder(
                new NewOrderItem(cappuccino.getId(), 2),
                new NewOrderItem(cocaCola.getId(), 1),
                new NewOrderItem(croissant.getId(), 1));

        List<PrintJob> printJobs = printJobService.createForOrder(order.getId());

        assertThat(printJobs).hasSize(2);
        assertThat(printJobRepository.findByOrder(order)).hasSize(2);

        List<JsonNode> contents = printJobs.stream().map(job -> readTree(job.getContent())).toList();

        JsonNode barContent = contents.stream()
                .filter(c -> "BAR".equals(c.get("productionStation").asText()))
                .findFirst().orElseThrow();
        List<JsonNode> barItems = itemsOf(barContent);
        assertThat(barItems).hasSize(2);
        assertThat(barItems).anySatisfy(item -> assertItem(item, "Cappuccino", 2));
        assertThat(barItems).anySatisfy(item -> assertItem(item, "Coca-Cola", 1));

        JsonNode kitchenContent = contents.stream()
                .filter(c -> "KITCHEN".equals(c.get("productionStation").asText()))
                .findFirst().orElseThrow();
        List<JsonNode> kitchenItems = itemsOf(kitchenContent);
        assertThat(kitchenItems).hasSize(1);
        assertItem(kitchenItems.get(0), "Croissant", 1);

        // Every content shares the same command number header.
        assertThat(contents).allSatisfy(c -> assertThat(c.get("commandNumber").asInt()).isEqualTo(COMMAND_NUMBER));
    }

    @Test
    void itemsWithoutAssignedStationAreGroupedIntoTheirOwnPrintJob() {
        Product cappuccino = createProduct("Cappuccino", new BigDecimal("8.00"), ProductionStation.BAR);
        // No station assigned — same as a freshly-created product before
        // staff picks one (Product.productionStation, FARELO-073).
        Product suco = createProduct("Suco Natural", new BigDecimal("7.00"), null);

        Order order = createOrder(
                new NewOrderItem(cappuccino.getId(), 1),
                new NewOrderItem(suco.getId(), 1));

        List<PrintJob> printJobs = printJobService.createForOrder(order.getId());

        assertThat(printJobs).hasSize(2);

        List<JsonNode> contents = printJobs.stream().map(job -> readTree(job.getContent())).toList();

        JsonNode barContent = contents.stream()
                .filter(c -> !c.get("productionStation").isNull())
                .findFirst().orElseThrow();
        assertThat(barContent.get("productionStation").asText()).isEqualTo("BAR");
        assertItem(itemsOf(barContent).get(0), "Cappuccino", 1);

        JsonNode unassignedContent = contents.stream()
                .filter(c -> c.get("productionStation").isNull())
                .findFirst().orElseThrow();
        // The field is present and explicitly null (not omitted) — see
        // PrintJobContent's javadoc for why that distinction matters to a
        // future reader.
        assertThat(unassignedContent.has("productionStation")).isTrue();
        assertItem(itemsOf(unassignedContent).get(0), "Suco Natural", 1);
    }

    private Order createOrder(NewOrderItem... items) {
        OrderWithItems result = orderService.create(COMMAND_NUMBER, List.of(items), null, null);
        createdOrderId = result.order().getId();
        return result.order();
    }

    private List<JsonNode> itemsOf(JsonNode content) {
        List<JsonNode> items = new ArrayList<>();
        content.get("items").forEach(items::add);
        return items;
    }

    private void assertItem(JsonNode item, String productName, int quantity) {
        assertThat(item.get("productName").asText()).isEqualTo(productName);
        assertThat(item.get("quantity").asInt()).isEqualTo(quantity);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse PrintJob content as JSON", e);
        }
    }

}
