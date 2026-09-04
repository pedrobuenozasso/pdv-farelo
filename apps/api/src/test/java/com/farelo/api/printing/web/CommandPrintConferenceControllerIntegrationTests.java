package com.farelo.api.printing.web;

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
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.ordering.OrderWithItems;
import com.farelo.api.outbox.OutboxWorker;
import com.farelo.api.printing.PrintJobRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/commands/{number}/print-conference}
 * (FARELO-211/212), against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers 80-81 — free per the registry
 * each test class's javadoc maintains (checked at the time this class was
 * written; see {@code PrintJobServiceCommandCheckIntegrationTests} for the
 * service-level aggregation tests, 78-79).
 *
 * <p>Same role list as {@code CommandController}'s {@code open}/{@code
 * close}: {@code ADMIN}/{@code MANAGER}/{@code CASHIER}/{@code ATTENDANT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandPrintConferenceControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 80;
    private static final int UNKNOWN_COMMAND_NUMBER = 999_999;
    private static final String PASSWORD = "senha-forte-123";

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

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
        // PENDING (this class never calls OutboxWorker in its own tests) —
        // same reasoning as PrintJobServiceIntegrationTests' cleanUp
        // javadoc: an event left pointing at an order this cleanup is about
        // to delete would fail a later, unrelated test the moment it
        // drains the real queue.
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

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    private String cashierToken() {
        return tokenFor(UserRole.CASHIER);
    }

    private Product createProduct(String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category("Cardápio"));
        createdCategoryIds.add(category.getId());
        Product product = productRepository.save(new Product(name, price, category));
        createdProductIds.add(product.getId());
        return product;
    }

    private void createOrder(NewOrderItem... items) {
        OrderWithItems result = orderService.create(COMMAND_NUMBER, List.of(items), null, null);
        createdOrderIds.add(result.order().getId());
    }

    @Test
    void printsConferenceAndReturnsCommandCheckJob() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"));
        createOrder(new NewOrderItem(espresso.getId(), 2));

        MvcResult result = mockMvc.perform(post("/api/v1/commands/{number}/print-conference", COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COMMAND_CHECK"))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.commandNumber").value(COMMAND_NUMBER))
                .andExpect(jsonPath("$.content").value(nullValue()))
                .andExpect(jsonPath("$.commandCheckContent.commandNumber").value(COMMAND_NUMBER))
                .andExpect(jsonPath("$.commandCheckContent.items", hasSize(1)))
                .andExpect(jsonPath("$.commandCheckContent.total").value(10.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        PrintJobResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), PrintJobResponse.class);
        assertThat(response.id()).isNotNull();
    }

    // Regression test for the LEFT JOIN FETCH fix in
    // PrintJobRepository#findByStatusOrderByCreatedAtAsc (FARELO-210/211):
    // before that fix, an inner JOIN FETCH on p.order would have silently
    // dropped every COMMAND_CHECK row (order_id IS NULL) from the Edge
    // Agent's poll — this proves a queued conferência actually reaches
    // GET /api/v1/print-jobs, not just that createCommandCheck persists a
    // row.
    @Test
    void printedConferenceAppearsInPendingPrintJobsQueue() throws Exception {
        Product espresso = createProduct("Café Espresso", new BigDecimal("5.00"));
        createOrder(new NewOrderItem(espresso.getId(), 1));

        MvcResult printResult = mockMvc.perform(
                        post("/api/v1/commands/{number}/print-conference", COMMAND_NUMBER)
                                .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andReturn();
        PrintJobResponse created = objectMapper.readValue(
                printResult.getResponse().getContentAsString(), PrintJobResponse.class);

        MvcResult pendingResult = mockMvc.perform(get("/api/v1/print-jobs"))
                .andExpect(status().isOk())
                .andReturn();
        List<PrintJobResponse> pending = objectMapper.readValue(
                pendingResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PrintJobResponse.class));

        assertThat(pending.stream().map(PrintJobResponse::id)).contains(created.id());
    }

    @Test
    void returnsCommandNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/print-conference", UNKNOWN_COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void rejectsWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/print-conference", COMMAND_NUMBER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/print-conference", COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsAsAttendant() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/print-conference", COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk());
    }

}
