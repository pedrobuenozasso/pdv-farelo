package com.farelo.api.payment.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountRepository;
import com.farelo.api.discount.DiscountType;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code POST
 * /api/v1/commands/{number}/payments} (FARELO-140/141), {@code GET
 * /api/v1/commands/{number}/payments/total} (FARELO-142), and {@code POST
 * /api/v1/commands/{number}/close} (FARELO-143), against a real PostgreSQL
 * instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (46-47, 49) for the {@code GET
 * .../payments} tests, 60-66 for the {@code POST} ({@link #record}) tests,
 * 70-72 for the {@code GET .../payments/total} ({@link #totalPaid}) tests,
 * and 4-7, 73-77, 92 for the {@code POST .../close} ({@link #close}) tests
 * below (FARELO-143) — distinct from every number already reserved
 * elsewhere in this suite, including {@link com.farelo.api.payment.PaymentRepositoryIntegrationTests}'
 * own 44-45, 48, 67-69 (see that class's javadoc for the fuller registry)
 * and, critically, {@code CommandSeedIntegrationTests}' sampled numbers (1,
 * 50, 100 — asserted to stay {@code AVAILABLE} forever; 50 was the first
 * choice here and had to move once that collision surfaced as a test
 * failure). Each mutated number is reset back to {@code AVAILABLE} in {@code
 * resetMutatedTestCommands()} below, same {@code @AfterEach} pattern {@code
 * CommandControllerIntegrationTests} uses, since these commands are shared
 * state across this whole suite via the singleton Postgres container (see
 * {@link AbstractIntegrationTest}). {@code COMMAND_WITHOUT_PAYMENTS} (47) is
 * asserted to have zero payments and must never be written to by any test
 * here — the "must not leak into another command's list" fixture below uses
 * a separate number (49) instead of 47, specifically so that assertion stays
 * true regardless of JUnit's (unspecified) method execution order within
 * this class. Each of 60-66, 70-72, 4-7, 73-77 and 92 is used by exactly one
 * test method (never shared), for the same order-independence reason.
 *
 * <p><b>{@link #close} (FARELO-143) — moved from {@code
 * CommandControllerIntegrationTests}.</b> Every {@code POST .../close} test
 * that existed before this ticket now lives here, unchanged in behavior
 * (numbers 4-7 and 92 carried over verbatim, since {@code
 * CommandControllerIntegrationTests} no longer uses them) — see {@code
 * PaymentController}'s javadoc for why the endpoint itself moved. New below:
 * fully-paid/underpaid/overpaid ({@code >=} semantics) and cancelled-order
 * exclusion tests (73-77), covering {@code
 * com.farelo.api.payment.PaymentService#closeCommand(int)}'s new validation.
 *
 * <p><b>{@link #listByCommand} and {@link #totalPaid} stay unprotected</b> —
 * see {@link PaymentController}'s javadoc for why, same precedent as {@code
 * NotificationControllerIntegrationTests}/{@code
 * AuditLogControllerIntegrationTests}. No token/{@code Authorization} header
 * is sent by any {@code GET} test below, and none is required.
 *
 * <p><b>{@link #record} and {@link #close} require a token</b> (FARELO-141/
 * FARELO-034: {@code ADMIN}/{@code MANAGER}/{@code CASHIER}) — every {@code
 * POST} test below mints one via {@link #tokenFor}, same {@code
 * tokenFor(UserRole)} pattern {@code CommandControllerIntegrationTests}
 * established for FARELO-124.
 *
 * <p><b>Test-isolation landmine hit while writing the FARELO-143
 * payment-sufficiency tests, same family already documented in
 * docs/domain-model.md for {@code Recipe}/{@code RecipeItem}/{@code
 * InventoryMovement} (and {@code OrderInventoryConsumptionIntegrationTests}'
 * own note)</b>: this class's new {@code close()} tests create real {@code
 * Product}/{@code Order}/{@code OrderItem} rows (via {@link
 * #createActiveProduct}/{@link #createOrder}) to give {@code
 * OrderService#getTotalOwed} something non-zero to sum. {@code
 * ProductControllerIntegrationTests}/{@code CategoryControllerIntegrationTests}
 * both run a blind {@code productRepository.deleteAll()}/{@code
 * categoryRepository.deleteAll()} in their own {@code @BeforeEach}, which
 * fails with a foreign key violation against any {@code order_item} row
 * still pointing at one of those products — exactly what leaving this
 * class's fixtures behind would cause, depending on Surefire's actual
 * (non-alphabetical) run order. {@link #cleanUpOrderFixtures()} below
 * deletes exactly the {@code OrderItem}/{@code Order}/{@code Product}/{@code
 * Category} rows this class itself created, every {@code @AfterEach}, same
 * "delete only what I made, never the whole shared table" fix already
 * applied for {@code RecipeItem} in {@code
 * OrderInventoryConsumptionIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_PAYMENTS = 46;
    private static final int COMMAND_WITHOUT_PAYMENTS = 47;
    private static final int OTHER_COMMAND_FOR_LEAK_CHECK = 49;

    // FARELO-141 (POST .../payments) — see class javadoc.
    private static final int RECORD_OPEN_NUMBER = 60;
    private static final int RECORD_PAYMENT_REQUESTED_NUMBER = 61;
    private static final int RECORD_AVAILABLE_NUMBER = 62;
    private static final int RECORD_CLOSED_NUMBER = 63;
    private static final int RECORD_VALIDATION_NUMBER = 64;
    private static final int RECORD_RBAC_NUMBER = 65;
    private static final int RECORD_BLOCKED_NUMBER = 66;

    // FARELO-142 (GET .../payments/total) — see class javadoc.
    private static final int TOTAL_COMMAND_WITH_PAYMENTS = 70;
    private static final int TOTAL_OTHER_COMMAND_FOR_LEAK_CHECK = 71;
    private static final int TOTAL_COMMAND_WITHOUT_PAYMENTS = 72;

    // FARELO-223: GET .../payments/balance tests below — 82-84, free per
    // this class's own registry above (46-49, 60-72, 4-7, 73-77, 92
    // already taken).
    private static final int BALANCE_EMPTY_NUMBER = 82;
    private static final int BALANCE_PARTIAL_NUMBER = 83;
    private static final int BALANCE_OVERPAID_NUMBER = 84;

    // FARELO-225: POST .../payments (record) with amountReceived/changeGiven
    // tests below — 85-88, free per this class's own registry above.
    private static final int RECORD_CHANGE_GIVEN_NUMBER = 85;
    private static final int RECORD_NO_CHANGE_NUMBER = 86;
    private static final int RECORD_CHANGE_WRONG_METHOD_NUMBER = 87;
    private static final int RECORD_CHANGE_BELOW_AMOUNT_NUMBER = 88;

    // FARELO-230/231/232: balance/close discount-awareness tests below —
    // 98-99, free per this class's own registry above.
    private static final int BALANCE_WITH_DISCOUNT_NUMBER = 98;
    private static final int CLOSE_WITH_DISCOUNT_NUMBER = 99;

    // FARELO-143 (POST .../close) — moved verbatim from
    // CommandControllerIntegrationTests (4-7, 92), plus new numbers (73-77)
    // for the payment-sufficiency tests this ticket adds. See class javadoc.
    private static final int CLOSE_FROM_OPEN_NUMBER = 4;
    private static final int CLOSE_FROM_PAYMENT_REQUESTED_NUMBER = 5;
    private static final int CLOSE_FROM_AVAILABLE_NUMBER = 6;
    private static final int CLOSE_ALREADY_CLOSED_NUMBER = 7;
    private static final int RBAC_CLOSE_NUMBER = 92;
    private static final int CLOSE_FULLY_PAID_NUMBER = 73;
    private static final int CLOSE_UNDERPAID_NUMBER = 74;
    private static final int CLOSE_OVERPAID_NUMBER = 75;
    private static final int CLOSE_CANCELLED_ORDER_ONLY_NUMBER = 76;
    private static final int CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER = 77;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    // Tracks every Order/OrderItem/Product/Category this class creates for
    // the FARELO-143 close() fixtures — see this class's javadoc for why
    // cleanUpOrderFixtures() below deletes exactly these rows.
    private final List<OrderItem> createdOrderItems = new ArrayList<>();
    private final List<Order> createdOrders = new ArrayList<>();
    private final List<Product> createdProducts = new ArrayList<>();
    private final List<Category> createdCategories = new ArrayList<>();

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    // FARELO-141/FARELO-143: the record()/close()-related tests mutate
    // command status on 60-66 and 4-7, 73-77, 92 (see class javadoc for why
    // those specific ranges) — reset them back to the seed default after
    // every test, same @AfterEach pattern CommandControllerIntegrationTests
    // uses, so no other test in this suite (e.g. CommandSeedIntegrationTests)
    // ever observes a leftover non-AVAILABLE status on one of these numbers.
    @AfterEach
    void resetMutatedTestCommands() {
        resetToAvailable(RECORD_OPEN_NUMBER);
        resetToAvailable(RECORD_PAYMENT_REQUESTED_NUMBER);
        resetToAvailable(RECORD_AVAILABLE_NUMBER);
        resetToAvailable(RECORD_CLOSED_NUMBER);
        resetToAvailable(RECORD_VALIDATION_NUMBER);
        resetToAvailable(RECORD_RBAC_NUMBER);
        resetToAvailable(RECORD_BLOCKED_NUMBER);
        resetToAvailable(RECORD_CHANGE_GIVEN_NUMBER);
        resetToAvailable(RECORD_NO_CHANGE_NUMBER);
        resetToAvailable(RECORD_CHANGE_WRONG_METHOD_NUMBER);
        resetToAvailable(RECORD_CHANGE_BELOW_AMOUNT_NUMBER);
        resetToAvailable(CLOSE_FROM_OPEN_NUMBER);
        resetToAvailable(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER);
        resetToAvailable(CLOSE_FROM_AVAILABLE_NUMBER);
        resetToAvailable(CLOSE_ALREADY_CLOSED_NUMBER);
        resetToAvailable(RBAC_CLOSE_NUMBER);
        resetToAvailable(CLOSE_FULLY_PAID_NUMBER);
        resetToAvailable(CLOSE_UNDERPAID_NUMBER);
        resetToAvailable(CLOSE_OVERPAID_NUMBER);
        resetToAvailable(CLOSE_CANCELLED_ORDER_ONLY_NUMBER);
        resetToAvailable(CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER);
        resetToAvailable(CLOSE_WITH_DISCOUNT_NUMBER);
    }

    private void resetToAvailable(int number) {
        Command command = command(number);
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    // Test-only setup: sets a command straight to a given status, bypassing
    // the API — same helper CommandControllerIntegrationTests uses to arrange
    // scenarios before exercising a status-dependent endpoint.
    private void setStatus(int number, CommandStatus status) {
        Command command = command(number);
        command.setStatus(status);
        commandRepository.save(command);
    }

    // Test-only fixture: a minimal active Product to attach OrderItems to —
    // same helper CommandOrdersControllerIntegrationTests uses for the
    // analogous "attach orders to a command" setup. Tracked in
    // createdCategories/createdProducts for cleanUpOrderFixtures() below.
    private Product createActiveProduct(BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        createdCategories.add(category);
        Product product = productRepository.save(new Product("Café Espresso", price, category));
        createdProducts.add(product);
        return product;
    }

    // Test-only fixture: persists an Order (defaulting to CREATED, or the
    // given status) with a single OrderItem of the given quantity/price
    // against `command` — the minimal shape needed to make
    // OrderService#getTotalOwed report a non-zero total for these close()
    // tests, bypassing the full POST /api/v1/orders flow (which also
    // touches inventory/outbox, irrelevant here). Tracked in
    // createdOrders/createdOrderItems for cleanUpOrderFixtures() below.
    private void createOrder(int commandNumber, BigDecimal unitPrice, int quantity, OrderStatus status) {
        Command command = command(commandNumber);
        Product product = createActiveProduct(unitPrice);
        Order order = orderRepository.save(new Order(command));
        if (status != OrderStatus.CREATED) {
            order.setStatus(status);
            order = orderRepository.save(order);
        }
        createdOrders.add(order);
        createdOrderItems.add(orderItemRepository.save(new OrderItem(order, product, quantity, unitPrice)));
    }

    // See this class's javadoc ("Test-isolation landmine") for why this
    // exists: deletes only the rows this class itself created, in FK-safe
    // order (children before parents), so no order_item row is ever left
    // pointing at a product/category by the time
    // ProductControllerIntegrationTests/CategoryControllerIntegrationTests'
    // blind deleteAll() runs.
    @AfterEach
    void cleanUpOrderFixtures() {
        orderItemRepository.deleteAll(createdOrderItems);
        orderRepository.deleteAll(createdOrders);
        productRepository.deleteAll(createdProducts);
        categoryRepository.deleteAll(createdCategories);
    }

    // FARELO-230/231/232: discounts applied against BALANCE_WITH_DISCOUNT_NUMBER/
    // CLOSE_WITH_DISCOUNT_NUMBER by the tests in that section below — same
    // "clean up only what this class itself created" reasoning as
    // cleanUpOrderFixtures above.
    @AfterEach
    void cleanUpDiscountFixtures() {
        discountRepository.findByCommandOrderByCreatedAtAsc(command(BALANCE_WITH_DISCOUNT_NUMBER))
                .forEach(discountRepository::delete);
        discountRepository.findByCommandOrderByCreatedAtAsc(command(CLOSE_WITH_DISCOUNT_NUMBER))
                .forEach(discountRepository::delete);
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    @Test
    void returnsEmptyListWhenCommandHasNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments", COMMAND_WITHOUT_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsPaymentsOldestFirstScopedToCommand() throws Exception {
        Command command = command(COMMAND_WITH_PAYMENTS);
        Command otherCommand = command(OTHER_COMMAND_FOR_LEAK_CHECK);

        Payment first = paymentRepository.save(new Payment(command, new BigDecimal("12.00"), PaymentMethod.CASH));
        // Distinct, increasing createdAt for a deterministic order
        // assertion — same pattern already used throughout this suite
        // (e.g. PrintJobControllerIntegrationTests' ordering test).
        Thread.sleep(10);
        Payment second = paymentRepository.save(
                new Payment(command, new BigDecimal("8.75"), PaymentMethod.PIX));
        // A payment on a different command must not leak into this list.
        paymentRepository.save(new Payment(otherCommand, new BigDecimal("50.00"), PaymentMethod.OTHER));

        mockMvc.perform(get("/api/v1/commands/{number}/payments", COMMAND_WITH_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].commandNumber").value(COMMAND_WITH_PAYMENTS))
                .andExpect(jsonPath("$[0].amount").value(12.00))
                .andExpect(jsonPath("$[0].method").value("CASH"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$[1].amount").value(8.75))
                .andExpect(jsonPath("$[1].method").value("PIX"));
    }

    // --- FARELO-141: POST .../payments -------------------------------------

    @Test
    void recordsPaymentWhenCommandIsOpen() throws Exception {
        setStatus(RECORD_OPEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 25.50, "method": "PIX"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.commandNumber").value(RECORD_OPEN_NUMBER))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.method").value("PIX"))
                .andExpect(jsonPath("$.createdAt").exists());

        List<Payment> persisted = paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_OPEN_NUMBER));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getAmount()).isEqualByComparingTo("25.50");
        assertThat(persisted.get(0).getMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void recordsPaymentWhenCommandIsPaymentRequested() throws Exception {
        setStatus(RECORD_PAYMENT_REQUESTED_NUMBER, CommandStatus.PAYMENT_REQUESTED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_PAYMENT_REQUESTED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 40.00, "method": "CASH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandNumber").value(RECORD_PAYMENT_REQUESTED_NUMBER))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.method").value("CASH"));

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_PAYMENT_REQUESTED_NUMBER)))
                .hasSize(1);
    }

    // --- FARELO-225: "Tratar troco em dinheiro" -----------------------------

    @Test
    void computesChangeGivenWhenCashReceivedExceedsAmount() throws Exception {
        setStatus(RECORD_CHANGE_GIVEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_CHANGE_GIVEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 90.00, "method": "CASH", "amountReceived": 100.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(90.00))
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.changeGiven").value(10.00));

        // Only the applied amount is persisted — the change itself is never
        // part of the ledger (see PaymentRequest's javadoc).
        List<Payment> persisted = paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_CHANGE_GIVEN_NUMBER));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void changeGivenIsZeroWhenAmountReceivedOmitted() throws Exception {
        setStatus(RECORD_NO_CHANGE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_NO_CHANGE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 40.00, "method": "CASH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeGiven").value(0));
    }

    @Test
    void rejectsAmountReceivedForNonCashMethod() throws Exception {
        setStatus(RECORD_CHANGE_WRONG_METHOD_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_CHANGE_WRONG_METHOD_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 50.00, "method": "PIX", "amountReceived": 60.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_CHANGE_WRONG_METHOD_NUMBER)))
                .isEmpty();
    }

    @Test
    void rejectsAmountReceivedBelowAmount() throws Exception {
        setStatus(RECORD_CHANGE_BELOW_AMOUNT_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_CHANGE_BELOW_AMOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 50.00, "method": "CASH", "amountReceived": 40.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_CHANGE_BELOW_AMOUNT_NUMBER)))
                .isEmpty();
    }

    @Test
    void rejectsPaymentWhenCommandIsAvailable() throws Exception {
        // RECORD_AVAILABLE_NUMBER is left at its seed default (AVAILABLE) —
        // never opened, so there's nothing to pay for.
        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_AVAILABLE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_AVAILABLE_NUMBER))).isEmpty();
    }

    @Test
    void rejectsPaymentWhenCommandIsClosed() throws Exception {
        setStatus(RECORD_CLOSED_NUMBER, CommandStatus.CLOSED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_CLOSED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsPaymentWhenCommandIsBlocked() throws Exception {
        setStatus(RECORD_BLOCKED_NUMBER, CommandStatus.BLOCKED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_BLOCKED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"));
    }

    @Test
    void returnsCommandNotFoundWhenRecordingPaymentForUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/payments", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void rejectsNonPositiveAmountWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "method": "CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_VALIDATION_NUMBER))).isEmpty();
    }

    @Test
    void rejectsNegativeAmountWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -5.00, "method": "CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingMethodWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- FARELO-141: RBAC on record() ---------------------------------------

    @Test
    void rejectsRecordWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ATTENDANT has no business recording payments — see PaymentController's
    // javadoc (cash-handling action, same exclusion CommandController#close
    // already makes).
    @Test
    void rejectsRecordWhenCallerRoleIsNotAllowed() throws Exception {
        setStatus(RECORD_RBAC_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_RBAC_NUMBER))).isEmpty();
    }

    @Test
    void allowsRecordAsCashier() throws Exception {
        setStatus(RECORD_RBAC_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isCreated());
    }

    // --- FARELO-142: GET .../payments/total ---------------------------------

    @Test
    void totalPaidReturnsZeroWhenCommandHasNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", TOTAL_COMMAND_WITHOUT_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandNumber").value(TOTAL_COMMAND_WITHOUT_PAYMENTS))
                .andExpect(jsonPath("$.totalPaid").value(0));
    }

    @Test
    void totalPaidSumsMultiplePaymentsScopedToCommand() throws Exception {
        Command command = command(TOTAL_COMMAND_WITH_PAYMENTS);
        Command otherCommand = command(TOTAL_OTHER_COMMAND_FOR_LEAK_CHECK);

        paymentRepository.save(new Payment(command, new BigDecimal("12.00"), PaymentMethod.CASH));
        paymentRepository.save(new Payment(command, new BigDecimal("8.75"), PaymentMethod.PIX));
        // A payment on a different command must not leak into this total.
        paymentRepository.save(new Payment(otherCommand, new BigDecimal("500.00"), PaymentMethod.OTHER));

        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", TOTAL_COMMAND_WITH_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandNumber").value(TOTAL_COMMAND_WITH_PAYMENTS))
                .andExpect(jsonPath("$.totalPaid").value(20.75));
    }

    @Test
    void totalPaidReturnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // --- FARELO-223: GET .../payments/balance -------------------------------

    @Test
    void balanceReturnsZeroTotalsWhenCommandHasNoOrdersOrPayments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/balance", BALANCE_EMPTY_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandNumber").value(BALANCE_EMPTY_NUMBER))
                .andExpect(jsonPath("$.totalOwed").value(0))
                .andExpect(jsonPath("$.totalPaid").value(0))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void balanceComputesRemainingAsOwedMinusPaid() throws Exception {
        createOrder(BALANCE_PARTIAL_NUMBER, new BigDecimal("25.00"), 1, OrderStatus.DELIVERED);
        paymentRepository.save(new Payment(command(BALANCE_PARTIAL_NUMBER), new BigDecimal("10.00"), PaymentMethod.CASH));

        mockMvc.perform(get("/api/v1/commands/{number}/payments/balance", BALANCE_PARTIAL_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(25.00))
                .andExpect(jsonPath("$.totalPaid").value(10.00))
                .andExpect(jsonPath("$.remaining").value(15.00));
    }

    // Same >= / floor-at-zero semantics as PaymentService#closeCommand — an
    // overpaid comanda reports remaining = 0, never a negative amount.
    @Test
    void balanceFloorsRemainingAtZeroWhenOverpaid() throws Exception {
        createOrder(BALANCE_OVERPAID_NUMBER, new BigDecimal("20.00"), 1, OrderStatus.DELIVERED);
        paymentRepository.save(new Payment(command(BALANCE_OVERPAID_NUMBER), new BigDecimal("30.00"), PaymentMethod.CASH));

        mockMvc.perform(get("/api/v1/commands/{number}/payments/balance", BALANCE_OVERPAID_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(20.00))
                .andExpect(jsonPath("$.totalPaid").value(30.00))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void balanceReturnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/balance", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    // FARELO-230/231/232: balance subtracts totalDiscount, in addition to
    // totalPaid, from totalOwed — see PaymentBalance's javadoc. Inserts the
    // Discount directly via the repository (bypassing POST
    // .../discounts) — this section's scope is PaymentService#getBalance's
    // aggregation, already covered end-to-end (including the HTTP layer)
    // by DiscountControllerIntegrationTests.
    @Test
    void balanceSubtractsTotalDiscountFromRemaining() throws Exception {
        createOrder(BALANCE_WITH_DISCOUNT_NUMBER, new BigDecimal("50.00"), 1, OrderStatus.DELIVERED);
        Command command = command(BALANCE_WITH_DISCOUNT_NUMBER);
        discountRepository.save(new Discount(
                command, DiscountType.FIXED_AMOUNT, null, new BigDecimal("50.00"), new BigDecimal("10.00"),
                "Teste", UUID.randomUUID(), "Test User"));

        mockMvc.perform(get("/api/v1/commands/{number}/payments/balance", BALANCE_WITH_DISCOUNT_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(50.00))
                .andExpect(jsonPath("$.totalDiscount").value(10.00))
                .andExpect(jsonPath("$.totalPaid").value(0))
                .andExpect(jsonPath("$.remaining").value(40.00));
    }

    // --- FARELO-143: POST .../close -----------------------------------------
    //
    // Moved from CommandControllerIntegrationTests (see this class's
    // javadoc): status-only scenarios below (no orders, no payments —
    // totalOwed and totalPaid both 0, so 0 >= 0 holds trivially) are
    // unchanged from before this ticket, verifying FARELO-034's original
    // "comanda opened but never ordered against still closes fine" behavior
    // survives FARELO-143's new check untouched.

    @Test
    void closesOpenCommand() throws Exception {
        setStatus(CLOSE_FROM_OPEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(CLOSE_FROM_OPEN_NUMBER))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        Optional<Command> persisted = commandRepository.findByNumber(CLOSE_FROM_OPEN_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.CLOSED);
    }

    @Test
    void closesPaymentRequestedCommand() throws Exception {
        setStatus(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER, CommandStatus.PAYMENT_REQUESTED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_PAYMENT_REQUESTED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        Optional<Command> persisted = commandRepository.findByNumber(CLOSE_FROM_PAYMENT_REQUESTED_NUMBER);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(CommandStatus.CLOSED);
    }

    @Test
    void returnsConflictWhenClosingAvailableCommand() throws Exception {
        // CLOSE_FROM_AVAILABLE_NUMBER is left at its seed default
        // (AVAILABLE) — never opened, so it can't be closed. Status is
        // checked before payment sufficiency (PaymentService#closeCommand),
        // so this still reports COMMAND_CANNOT_BE_CLOSED, not a payment error.
        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FROM_AVAILABLE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenClosingAlreadyClosedCommand() throws Exception {
        setStatus(CLOSE_ALREADY_CLOSED_NUMBER, CommandStatus.CLOSED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_ALREADY_CLOSED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCommandNotFoundWhenClosingUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/close", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    // --- FARELO-143: payment-sufficiency validation -------------------------

    @Test
    void closesCommandWhenTotalPaidExactlyMatchesTotalOwed() throws Exception {
        setStatus(CLOSE_FULLY_PAID_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_FULLY_PAID_NUMBER, new BigDecimal("12.50"), 2, OrderStatus.DELIVERED);
        Command command = command(CLOSE_FULLY_PAID_NUMBER);
        paymentRepository.save(new Payment(command, new BigDecimal("25.00"), PaymentMethod.CASH));

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_FULLY_PAID_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // FARELO-230/231/232: a discount covering the gap between totalPaid and
    // totalOwed lifts the CommandNotFullyPaidException block — same
    // "insert Discount directly" scope-boundary reasoning as
    // balanceSubtractsTotalDiscountFromRemaining above.
    @Test
    void closesCommandWhenDiscountCoversTheRemainingGap() throws Exception {
        setStatus(CLOSE_WITH_DISCOUNT_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_WITH_DISCOUNT_NUMBER, new BigDecimal("20.00"), 1, OrderStatus.DELIVERED);
        Command command = command(CLOSE_WITH_DISCOUNT_NUMBER);
        paymentRepository.save(new Payment(command, new BigDecimal("15.00"), PaymentMethod.CASH));
        discountRepository.save(new Discount(
                command, DiscountType.FIXED_AMOUNT, null, new BigDecimal("20.00"), new BigDecimal("5.00"),
                null, UUID.randomUUID(), "Test User"));

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_WITH_DISCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void rejectsClosingUnderpaidCommand() throws Exception {
        setStatus(CLOSE_UNDERPAID_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_UNDERPAID_NUMBER, new BigDecimal("50.00"), 1, OrderStatus.DELIVERED);
        Command command = command(CLOSE_UNDERPAID_NUMBER);
        paymentRepository.save(new Payment(command, new BigDecimal("20.00"), PaymentMethod.PIX));

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_UNDERPAID_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FULLY_PAID"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(command(CLOSE_UNDERPAID_NUMBER).getStatus()).isEqualTo(CommandStatus.OPEN);
    }

    // >= semantics: overpaying (e.g. a tip, declined change) still closes.
    @Test
    void closesOverpaidCommand() throws Exception {
        setStatus(CLOSE_OVERPAID_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_OVERPAID_NUMBER, new BigDecimal("18.00"), 1, OrderStatus.DELIVERED);
        Command command = command(CLOSE_OVERPAID_NUMBER);
        paymentRepository.save(new Payment(command, new BigDecimal("20.00"), PaymentMethod.CASH));

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_OVERPAID_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // A comanda whose only order was cancelled owes nothing (totalOwed = 0)
    // — closes fine with zero payments, same as a comanda with no orders at
    // all.
    @Test
    void closesCommandWhenOnlyOrderIsCancelled() throws Exception {
        setStatus(CLOSE_CANCELLED_ORDER_ONLY_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_CANCELLED_ORDER_ONLY_NUMBER, new BigDecimal("30.00"), 1, OrderStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_CANCELLED_ORDER_ONLY_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // A cancelled order's items are excluded from totalOwed even when other,
    // non-cancelled orders exist: paying only for the delivered order is
    // enough to close, despite a much larger cancelled order also on the
    // comanda.
    @Test
    void closesCommandWhenCancelledOrderIsExcludedFromTotalOwed() throws Exception {
        setStatus(CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER, CommandStatus.OPEN);
        createOrder(CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER, new BigDecimal("15.00"), 1, OrderStatus.DELIVERED);
        createOrder(CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER, new BigDecimal("200.00"), 1, OrderStatus.CANCELLED);
        Command command = command(CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER);
        paymentRepository.save(new Payment(command, new BigDecimal("15.00"), PaymentMethod.CASH));

        mockMvc.perform(post("/api/v1/commands/{number}/close", CLOSE_CANCELLED_ORDER_EXCLUDED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // --- FARELO-124: RBAC on close() -----------------------------------------

    @Test
    void rejectsCloseWithNoAuthorizationHeader() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ATTENDANT can open() but, deliberately, not close() — see
    // CommandController's/PaymentController's javadoc for why closing is
    // narrower (cash-handling action).
    @Test
    void rejectsCloseWhenCallerRoleIsNotAllowed() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsCloseAsCashier() throws Exception {
        setStatus(RBAC_CLOSE_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/close", RBAC_CLOSE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk());
    }

}
