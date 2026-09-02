package com.farelo.api.printing.web;

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
import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobRepository;
import com.farelo.api.printing.PrintJobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/print-jobs} (FARELO-076), against
 * a real PostgreSQL instance (Testcontainers) — first REST endpoint of the
 * {@code printing} domain.
 *
 * <p>Unlike the kitchen queue tests in {@code OrderControllerIntegrationTests}
 * (which scope assertions to their own order ids because {@code orders} is a
 * table many other test classes also write to), this class clears the
 * {@code print_job} table itself in {@code @BeforeEach} for a deterministic
 * starting point on every test, including a literal empty-list assertion —
 * same rationale {@code CategoryControllerIntegrationTests} documents for
 * wiping the catalog tables. Safe here specifically because {@code
 * print_job} is a pure child table (nothing else references it), and
 * because Postgres is shared but test classes in this suite run
 * sequentially, not concurrently — so no other class's rows are still being
 * asserted on when this one clears the table.
 *
 * <p>Uses dedicated seeded command number 20 — the next free number after
 * every one already reserved elsewhere (see {@code
 * PrintJobServiceIntegrationTests}' javadoc: 1-19, 101, 999 already taken).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrintJobControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 20;

    @Autowired
    private MockMvc mockMvc;

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
    private PrintJobRepository printJobRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void clearPrintJobsTable() {
        printJobRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        for (UUID orderId : createdOrderIds) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            // Must go first: print_job.order_id is a NOT NULL FK to
            // orders(id), so deleting the order below while any of its
            // PrintJobs still exist would fail with a FK violation.
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

    // Creates a real order through OrderService (same OrderCreated outbox
    // wiring as production, FARELO-060/072) and immediately drains the
    // outbox so its PrintJob(s) exist by the time this method returns.
    private Order createOrderWithPendingPrintJobs(NewOrderItem... items) {
        OrderWithItems result = orderService.create(COMMAND_NUMBER, List.of(items), null, null);
        createdOrderIds.add(result.order().getId());
        outboxWorker.processPendingEvents();
        return result.order();
    }

    private List<PrintJobResponse> listPrintJobs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/print-jobs"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PrintJobResponse.class));
    }

    @Test
    void returnsEmptyListWhenNoPendingPrintJobsExist() throws Exception {
        mockMvc.perform(get("/api/v1/print-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsPendingPrintJobWithParsedContent() throws Exception {
        Product cappuccino = createProduct("Cappuccino", new BigDecimal("8.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(cappuccino.getId(), 2));

        List<PrintJob> persisted = printJobRepository.findByOrder(order);
        assertThat(persisted).hasSize(1);
        UUID printJobId = persisted.get(0).getId();

        List<PrintJobResponse> jobs = listPrintJobs();
        assertThat(jobs).hasSize(1);

        PrintJobResponse job = jobs.get(0);
        assertThat(job.id()).isEqualTo(printJobId);
        assertThat(job.orderId()).isEqualTo(order.getId());
        assertThat(job.status()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(job.createdAt()).isNotNull();

        // content deserialized into a real object (PrintJobContent), not a
        // raw/escaped JSON string re-nested inside the response.
        assertThat(job.content().commandNumber()).isEqualTo(COMMAND_NUMBER);
        assertThat(job.content().productionStation()).isEqualTo(ProductionStation.BAR);
        assertThat(job.content().items()).hasSize(1);
        assertThat(job.content().items().get(0).productName()).isEqualTo("Cappuccino");
        assertThat(job.content().items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void listsPendingPrintJobsOrderedByCreatedAtAsc() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);

        Order firstOrder = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        // Distinct, increasing createdAt for a deterministic FIFO assertion
        // (same pattern already used by OrderControllerIntegrationTests'
        // kitchen-queue ordering test).
        Thread.sleep(10);
        Order secondOrder = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));

        UUID firstJobId = printJobRepository.findByOrder(firstOrder).get(0).getId();
        UUID secondJobId = printJobRepository.findByOrder(secondOrder).get(0).getId();

        List<PrintJobResponse> jobs = listPrintJobs();
        assertThat(jobs).hasSize(2);
        assertThat(jobs.stream().map(PrintJobResponse::id).toList())
                .containsExactly(firstJobId, secondJobId);
    }

    @Test
    void excludesPrintedAndFailedPrintJobsFromTheListing() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Product croissant = createProduct("Croissant", new BigDecimal("9.00"), ProductionStation.KITCHEN);
        Product suco = createProduct("Suco Natural", new BigDecimal("7.00"), ProductionStation.BAR);

        Order printedOrder = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        Order failedOrder = createOrderWithPendingPrintJobs(new NewOrderItem(croissant.getId(), 1));
        Order stillPendingOrder = createOrderWithPendingPrintJobs(new NewOrderItem(suco.getId(), 1));

        markStatus(printedOrder, PrintJobStatus.PRINTED);
        markStatus(failedOrder, PrintJobStatus.FAILED);
        UUID stillPendingJobId = printJobRepository.findByOrder(stillPendingOrder).get(0).getId();

        List<PrintJobResponse> jobs = listPrintJobs();

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).id()).isEqualTo(stillPendingJobId);
        assertThat(jobs.get(0).status()).isEqualTo(PrintJobStatus.PENDING);
    }

    // No endpoint transitions a PrintJob's status yet (that's FARELO-077+)
    // — reaches into the repository directly, same "test setup via
    // repository" pattern already used elsewhere (e.g.
    // OrderControllerIntegrationTests#setOrderStatus).
    private void markStatus(Order order, PrintJobStatus status) {
        PrintJob job = printJobRepository.findByOrder(order).get(0);
        if (status == PrintJobStatus.PRINTED) {
            job.markPrinted();
        } else if (status == PrintJobStatus.FAILED) {
            job.markFailed();
        } else {
            throw new IllegalArgumentException("Unsupported status for test setup: " + status);
        }
        printJobRepository.save(job);
    }

}
