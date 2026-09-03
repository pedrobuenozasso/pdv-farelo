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
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/print-jobs} (FARELO-076), for
 * {@code POST /api/v1/print-jobs/{id}/printed}/{@code /failed} (FARELO-077),
 * and for {@code POST /api/v1/print-jobs/{id}/retry} (FARELO-079), against a
 * real PostgreSQL instance (Testcontainers) — the {@code printing} domain's
 * REST endpoints.
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
 *
 * <p><b>FARELO-124</b>: only {@code POST .../retry} now requires a caller
 * role (see {@code PrintJobController}'s javadoc for why {@code GET} and
 * {@code .../printed}/{@code .../failed} stay unprotected — Edge Agent
 * machine endpoints) — so only the {@code .../retry} calls below mint a
 * token via {@link #tokenFor} and send it as
 * {@code Authorization: Bearer <token>}, same pattern
 * {@code ProductControllerIntegrationTests} established at FARELO-123.
 * Every {@code GET}/{@code .../printed}/{@code .../failed} call keeps
 * <b>no</b> header, exactly as before this ticket.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrintJobControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 20;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

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

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
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

    // Used to seed PRINTED/FAILED jobs directly for tests that don't care
    // how they got there (e.g. the listing-exclusion test above) — reaches
    // into the repository directly rather than going through the FARELO-077
    // endpoints under test below, same "test setup via repository" pattern
    // already used elsewhere (e.g. OrderControllerIntegrationTests#setOrderStatus).
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

    // --- POST /api/v1/print-jobs/{id}/printed, POST .../failed (FARELO-077) ---

    @Test
    void marksPendingPrintJobAsPrinted() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();

        mockMvc.perform(post("/api/v1/print-jobs/{id}/printed", printJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(printJobId.toString()))
                .andExpect(jsonPath("$.status").value("PRINTED"));

        PrintJob job = printJobRepository.findById(printJobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PRINTED);
    }

    @Test
    void marksPendingPrintJobAsFailed() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();

        mockMvc.perform(post("/api/v1/print-jobs/{id}/failed", printJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(printJobId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"));

        PrintJob job = printJobRepository.findById(printJobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.FAILED);
    }

    @Test
    void returnsConflictWhenMarkingAnAlreadyPrintedJobAsPrintedAgain() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.PRINTED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/printed", printJobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenMarkingAnAlreadyFailedJobAsPrinted() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.FAILED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/printed", printJobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenMarkingAnAlreadyPrintedJobAsFailed() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.PRINTED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/failed", printJobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsNotFoundWhenMarkingUnknownPrintJobAsPrinted() throws Exception {
        mockMvc.perform(post("/api/v1/print-jobs/{id}/printed", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenMarkingUnknownPrintJobAsFailed() throws Exception {
        mockMvc.perform(post("/api/v1/print-jobs/{id}/failed", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
    }

    // --- POST /api/v1/print-jobs/{id}/retry (FARELO-079) ---

    // Mirrors PrintJobService.MAX_RETRY_COUNT (package-private, not visible
    // from this .web test package) — see that constant's javadoc for why
    // this exact value was chosen.
    private static final int MAX_RETRY_COUNT = 3;

    @Test
    void marksFailedPrintJobAsPendingViaRetryAndItReappearsInTheListing() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.FAILED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(printJobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.retryCount").value(1));

        PrintJob job = printJobRepository.findById(printJobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);

        // Retried job is PENDING again, so it must reappear in the Edge
        // Agent's poll — the entire point of the retry endpoint.
        List<PrintJobResponse> jobs = listPrintJobs();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).id()).isEqualTo(printJobId);
    }

    @Test
    void returnsConflictWhenRetryingAPendingPrintJob() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenRetryingAPrintedPrintJob() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.PRINTED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsNotFoundWhenRetryingUnknownPrintJob() throws Exception {
        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenRetryingAPrintJobPastTheRetryLimit() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        String token = tokenFor(UserRole.ATTENDANT);

        // Cycle FAILED -> retry (PENDING) -> FAILED again, MAX_RETRY_COUNT
        // times: each retry succeeds and bumps retryCount by one.
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            markStatus(order, PrintJobStatus.FAILED);

            mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.retryCount").value(attempt));
        }

        // retryCount is now MAX_RETRY_COUNT — one more FAILED->retry attempt
        // must be rejected instead of retried again.
        markStatus(order, PrintJobStatus.FAILED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_RETRY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        PrintJob job = printJobRepository.findById(printJobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.FAILED);
        assertThat(job.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
    }

    // --- FARELO-124: RBAC on retry() only ----------------------------------

    @Test
    void rejectsRetryWithNoAuthorizationHeader() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.FAILED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // retry() deliberately allows all five operational roles (see
    // PrintJobController's javadoc) — KITCHEN is exercised here as the
    // least front-of-house-sounding one, to prove the list really is "any
    // staff role", not just the cashier/attendant roles already used above.
    @Test
    void allowsRetryAsKitchen() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"), ProductionStation.BAR);
        Order order = createOrderWithPendingPrintJobs(new NewOrderItem(espresso.getId(), 1));
        UUID printJobId = printJobRepository.findByOrder(order).get(0).getId();
        markStatus(order, PrintJobStatus.FAILED);

        mockMvc.perform(post("/api/v1/print-jobs/{id}/retry", printJobId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

}
