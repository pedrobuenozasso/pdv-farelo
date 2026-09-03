package com.farelo.api.ordering.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.ordering.OrderStatusHistory;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.outbox.OutboxEvent;
import com.farelo.api.outbox.OutboxEventRepository;
import com.farelo.api.outbox.OutboxEventStatus;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/orders} and the order-lifecycle
 * transition endpoints, against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (10-13) — distinct from every
 * number already spoken for by other test classes sharing the singleton
 * Postgres container (see {@code CommandControllerIntegrationTests}: 1-7,
 * 91-92, 999; {@code CommandRepositoryIntegrationTests}: 101;
 * {@code OrderRepositoryIntegrationTests}: 8;
 * {@code OrderItemRepositoryIntegrationTests}: 9) — and resets them back to
 * {@code AVAILABLE} in {@code @AfterEach}.
 *
 * <p><b>FARELO-124</b>: every method here except {@code create} now
 * requires a caller role (see {@code OrderController}'s javadoc for exactly
 * which). {@code POST /api/v1/orders} itself stays <b>without</b> a header
 * anywhere in this class — it's the public "Cardápio QR" checkout
 * dependency (see the controller's javadoc). Every call to
 * {@code /preparing}/{@code /ready}/{@code /deliver}/{@code /cancel} below
 * uses a {@code CASHIER} token (the one role allowed on all four — see
 * {@code OrderController}'s javadoc), minted via {@link #tokenFor}, same
 * pattern {@code ProductControllerIntegrationTests}/
 * {@code UserControllerIntegrationTests} established at FARELO-123; the
 * kitchen queue ({@code GET}) uses a {@code KITCHEN} token instead, since
 * {@code CASHIER} isn't allowed there.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_AVAILABLE = 10;
    private static final int COMMAND_OPEN = 11;
    private static final int COMMAND_CLOSED = 12;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetTestCommands() {
        resetToAvailable(COMMAND_AVAILABLE);
        resetToAvailable(COMMAND_OPEN);
        resetToAvailable(COMMAND_CLOSED);
    }

    private void resetToAvailable(int number) {
        setCommandStatus(number, CommandStatus.AVAILABLE);
    }

    private void setCommandStatus(int number, CommandStatus status) {
        Command command = commandRepository.findByNumber(number).orElseThrow();
        command.setStatus(status);
        commandRepository.save(command);
    }

    // Sets an order's status directly via the repository, bypassing the
    // service's transition validation — used only to reach CONFIRMED for
    // test setup below, since nothing in the system transitions an order
    // into CONFIRMED yet (see OrderStatus/markAsPreparing's javadoc). Same
    // "reach into the repository for test setup" pattern as
    // setCommandStatus above.
    private void setOrderStatus(UUID orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);
    }

    private Product createActiveProduct(BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        return productRepository.save(new Product("Café Espresso", price, category));
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    // FARELO-124: CASHIER is the one role allowed on every one of
    // preparing/ready/deliver/cancel (see OrderController's javadoc) — used
    // throughout this class for setup/assertion calls that aren't
    // themselves testing the RBAC boundary (those live in their own
    // dedicated tests below).
    private String cashierToken() {
        return tokenFor(UserRole.CASHIER);
    }

    // Creates a real order (status CREATED) through the actual creation
    // endpoint, so its first OrderStatusHistory entry (FARELO-056) is
    // written the normal way — used as setup for the FARELO-057/058
    // transition tests below. POST /api/v1/orders stays unprotected
    // (FARELO-124), so no Authorization header here.
    private UUID createOrder(Product product) throws Exception {
        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_AVAILABLE, product.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), OrderResponse.class).id();
    }

    @Test
    void createsOrderWithItemsAndTransitionsAvailableCommandToOpen() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("7.50"));
        Product suco = createActiveProduct(new BigDecimal("9.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [
                    {"productId": "%s", "quantity": 2},
                    {"productId": "%s", "quantity": 1}
                  ]
                }
                """.formatted(COMMAND_AVAILABLE, espresso.getId(), suco.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.commandNumber").value(COMMAND_AVAILABLE))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.customerName").value(nullValue()))
                .andExpect(jsonPath("$.customerPhone").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        // customerName/customerPhone weren't sent in the request body above
        // — both optional, and must come back null rather than fail.
        assertThat(response.customerName()).isNull();
        assertThat(response.customerPhone()).isNull();

        OrderItemResponse espressoItem = response.items().stream()
                .filter(item -> item.productId().equals(espresso.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(espressoItem.quantity()).isEqualTo(2);
        assertThat(espressoItem.unitPrice()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(espressoItem.productName()).isEqualTo("Café Espresso");

        // AVAILABLE -> OPEN transition, as a side effect of the first order.
        Optional<Command> command = commandRepository.findByNumber(COMMAND_AVAILABLE);
        assertThat(command).isPresent();
        assertThat(command.get().getStatus()).isEqualTo(CommandStatus.OPEN);

        // Price snapshot: changing the product's price afterwards must NOT
        // change the already-created OrderItem's unitPrice.
        Product updatedEspresso = productRepository.findById(espresso.getId()).orElseThrow();
        updatedEspresso.setPrice(new BigDecimal("99.99"));
        productRepository.save(updatedEspresso);

        OrderItem persistedItem = orderItemRepository.findById(espressoItem.id()).orElseThrow();
        assertThat(persistedItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    void createsOrderWithCustomerNameAndPhone() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("7.50"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}],
                  "customerName": "Maria",
                  "customerPhone": "+55 11 91234-5678"
                }
                """.formatted(COMMAND_AVAILABLE, espresso.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Maria"))
                .andExpect(jsonPath("$.customerPhone").value("+55 11 91234-5678"))
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        assertThat(response.customerName()).isEqualTo("Maria");
        assertThat(response.customerPhone()).isEqualTo("+55 11 91234-5678");

        // Persisted, not just echoed back in the response.
        Order order = orderRepository.findById(response.id()).orElseThrow();
        assertThat(order.getCustomerName()).isEqualTo("Maria");
        assertThat(order.getCustomerPhone()).isEqualTo("+55 11 91234-5678");
    }

    @Test
    void createsOrderOnOpenCommandWithoutFurtherTransition() throws Exception {
        setCommandStatus(COMMAND_OPEN, CommandStatus.OPEN);
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_OPEN, espresso.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandNumber").value(COMMAND_OPEN));

        Optional<Command> command = commandRepository.findByNumber(COMMAND_OPEN);
        assertThat(command).isPresent();
        assertThat(command.get().getStatus()).isEqualTo(CommandStatus.OPEN);
    }

    @Test
    void returnsCommandNotFoundForUnknownCommandNumber() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": 999,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(espresso.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenCommandCannotAcceptOrders() throws Exception {
        setCommandStatus(COMMAND_CLOSED, CommandStatus.CLOSED);
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_CLOSED, espresso.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_ORDERS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsProductNotFoundForUnknownProduct() throws Exception {
        UUID missingProductId = UUID.randomUUID();

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_AVAILABLE, missingProductId);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenProductIsInactive() throws Exception {
        Category category = categoryRepository.save(new Category("Doces"));
        Product inactiveProduct = new Product("Brigadeiro", new BigDecimal("4.00"), category);
        inactiveProduct.setActive(false);
        productRepository.save(inactiveProduct);

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_AVAILABLE, inactiveProduct.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsEmptyItemsListWithStandardErrorFormat() throws Exception {
        String body = """
                {
                  "commandNumber": %d,
                  "items": []
                }
                """.formatted(COMMAND_AVAILABLE);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWithStandardErrorFormat() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 0}]
                }
                """.formatted(COMMAND_AVAILABLE, espresso.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingCommandNumberWithStandardErrorFormat() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(espresso.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsExactlyOneCreatedHistoryEntryOnOrderCreation() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_AVAILABLE, espresso.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        Order order = orderRepository.findById(response.id()).orElseThrow();

        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isNull();
        assertThat(history.get(0).getToStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.get(0).getChangedAt()).isNotNull();
    }

    // FARELO-060: the reference Transactional Outbox integration —
    // OrderService#create publishes OrderCreated in the same transaction
    // as the order/items/history above. Generic atomicity (rollback also
    // rolls back the outbox row) is covered by
    // OutboxPublisherIntegrationTests; this proves the real production
    // wiring end to end.
    @Test
    void publishesOrderCreatedOutboxEventOnOrderCreation() throws Exception {
        Product espresso = createActiveProduct(new BigDecimal("5.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 3}]
                }
                """.formatted(COMMAND_AVAILABLE, espresso.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        OutboxEvent event = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .stream()
                .filter(e -> e.getAggregateId().equals(response.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected OrderCreated outbox event was not published"));

        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getEventType()).isEqualTo("OrderCreated");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // Parsed rather than raw-string-matched: jsonb's exact text
        // representation (spacing, key order) isn't part of the contract.
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("orderId").asText()).isEqualTo(response.id().toString());
        assertThat(payload.get("commandNumber").asInt()).isEqualTo(COMMAND_AVAILABLE);
        assertThat(payload.get("items")).hasSize(1);
        assertThat(payload.get("items").get(0).get("productId").asText()).isEqualTo(espresso.getId().toString());
        assertThat(payload.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
    }

    @Test
    void marksOrderAsPreparingAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PREPARING"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getFromStatus()).isNull();
        assertThat(history.get(0).getToStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.get(1).getFromStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.get(1).getToStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void returnsConflictWhenMarkingNonCreatedOrderAsPreparing() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // already PREPARING — marking it as preparing again is invalid.
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsOrderNotFoundWhenMarkingUnknownOrderAsPreparing() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", UUID.randomUUID())
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void marksOrderAsReadyAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("READY"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(3);
        assertThat(history.get(2).getFromStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(history.get(2).getToStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    void returnsConflictWhenMarkingCreatedOrderAsReadyDirectly() throws Exception {
        // skips PREPARING entirely — still CREATED.
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsOrderNotFoundWhenMarkingUnknownOrderAsReady() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/ready", UUID.randomUUID())
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void marksOrderAsDeliveredAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(4);
        assertThat(history.get(3).getFromStatus()).isEqualTo(OrderStatus.READY);
        assertThat(history.get(3).getToStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void returnsConflictWhenMarkingNonReadyOrderAsDelivered() throws Exception {
        // still CREATED — skips PREPARING/READY entirely.
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsOrderNotFoundWhenMarkingUnknownOrderAsDelivered() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", UUID.randomUUID())
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelsOrderFromCreatedAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(2);
        assertThat(history.get(1).getFromStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.get(1).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // CONFIRMED has no transition into it anywhere in the system yet (see
    // OrderStatus/markAsPreparing's javadoc) — set directly via
    // setOrderStatus, purely to exercise markAsCancelled's origin-status
    // check for this otherwise-unreachable case, same as the other three
    // origins below.
    @Test
    void cancelsOrderFromConfirmedAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        setOrderStatus(orderId, OrderStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);
        OrderStatusHistory lastEntry = history.get(history.size() - 1);
        assertThat(lastEntry.getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(lastEntry.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelsOrderFromPreparingAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(3);
        assertThat(history.get(2).getFromStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(history.get(2).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelsOrderFromReadyAndRecordsHistory() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);

        assertThat(history).hasSize(4);
        assertThat(history.get(3).getFromStatus()).isEqualTo(OrderStatus.READY);
        assertThat(history.get(3).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void returnsConflictWhenCancellingDeliveredOrder() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenCancellingAlreadyCancelledOrder() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsOrderNotFoundWhenCancellingUnknownOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // Confirms the side effect documented in docs/api.md's GET /api/v1/orders
    // section rather than assuming it: DELIVERED/CANCELLED are terminal, so
    // an order reaching either must disappear from the kitchen queue, same
    // as the already-covered READY case above.
    @Test
    void kitchenQueueExcludesDeliveredAndCancelledOrders() throws Exception {
        Product product = createActiveProduct(new BigDecimal("6.00"));
        String cashierToken = cashierToken();

        UUID deliveredOrderId = createOrder(product);
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", deliveredOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", deliveredOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", deliveredOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        UUID cancelledOrderId = createOrder(product);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", cancelledOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk())
                .andReturn();

        List<OrderResponse> queue = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, OrderResponse.class));
        List<UUID> queueIds = queue.stream().map(OrderResponse::id).toList();

        assertThat(queueIds).doesNotContain(deliveredOrderId, cancelledOrderId);
    }

    // FARELO-059: GET /api/v1/orders (kitchen queue). Postgres is a
    // singleton container shared by every test class in the run (see
    // AbstractIntegrationTest) — other classes may have their own
    // CREATED/PREPARING orders sitting in the table already, so this
    // checks relative order/(non-)membership among *this test's own*
    // orders rather than an absolute response size (same reasoning as
    // OrderRepositoryIntegrationTests' equivalent query test).
    @Test
    void listsKitchenQueueOrderedByCreatedAtAscExcludingReadyOrders() throws Exception {
        Product product = createActiveProduct(new BigDecimal("6.00"));
        String cashierToken = cashierToken();

        UUID createdOrderId = createOrder(product);

        // Distinct, increasing createdAt for a deterministic FIFO
        // assertion (same pattern as CommandOrdersControllerIntegrationTests).
        Thread.sleep(10);
        UUID preparingOrderId = createOrder(product);
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", preparingOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        UUID readyOrderId = createOrder(product);
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", readyOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", readyOrderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk())
                .andReturn();

        List<OrderResponse> queue = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, OrderResponse.class));
        List<UUID> queueIds = queue.stream().map(OrderResponse::id).toList();

        // READY orders never show up in the kitchen queue.
        assertThat(queueIds).doesNotContain(readyOrderId);

        List<UUID> ourQueueIds = queueIds.stream()
                .filter(id -> id.equals(createdOrderId) || id.equals(preparingOrderId))
                .toList();
        assertThat(ourQueueIds).containsExactly(createdOrderId, preparingOrderId);

        OrderResponse createdResponse = queue.stream()
                .filter(o -> o.id().equals(createdOrderId)).findFirst().orElseThrow();
        assertThat(createdResponse.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(createdResponse.items()).hasSize(1);
        assertThat(createdResponse.items().get(0).productName()).isEqualTo("Café Espresso");

        OrderResponse preparingResponse = queue.stream()
                .filter(o -> o.id().equals(preparingOrderId)).findFirst().orElseThrow();
        assertThat(preparingResponse.status()).isEqualTo(OrderStatus.PREPARING);
    }

    // --- FARELO-124: RBAC on queue()/markAsPreparing/markAsReady/
    //     markAsDelivered/markAsCancelled -------------------------------

    @Test
    void rejectsQueueWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // CASHIER is allowed on preparing/ready/deliver/cancel, but not on the
    // kitchen queue itself — see OrderController's javadoc.
    @Test
    void rejectsQueueWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsQueueAsKitchen() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPreparingWithNoAuthorizationHeader() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ATTENDANT is allowed on deliver/cancel but not preparing/ready — see
    // OrderController's javadoc.
    @Test
    void rejectsPreparingWhenCallerRoleIsNotAllowed() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsPreparingAsKitchen() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsReadyWithNoAuthorizationHeader() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsReadyWhenCallerRoleIsNotAllowed() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsDeliverWithNoAuthorizationHeader() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String token = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // KITCHEN is allowed on preparing/ready but not deliver/cancel — see
    // OrderController's javadoc.
    @Test
    void rejectsDeliverWhenCallerRoleIsNotAllowed() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String setupToken = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + setupToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + setupToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsDeliverAsAttendant() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));
        String setupToken = cashierToken();
        mockMvc.perform(post("/api/v1/orders/{id}/preparing", orderId)
                        .header("Authorization", "Bearer " + setupToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/ready", orderId)
                        .header("Authorization", "Bearer " + setupToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsCancelWithNoAuthorizationHeader() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsCancelWhenCallerRoleIsNotAllowed() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsCancelAsAttendant() throws Exception {
        UUID orderId = createOrder(createActiveProduct(new BigDecimal("5.00")));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk());
    }

}
