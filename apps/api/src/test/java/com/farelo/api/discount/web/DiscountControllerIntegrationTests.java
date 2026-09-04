package com.farelo.api.discount.web;

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
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code POST
 * /api/v1/commands/{number}/discounts} (FARELO-230/231/232), against a
 * real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers 93-97 — free per the registry
 * {@code PaymentControllerIntegrationTests}' javadoc maintains (checked at
 * the time this class was written).
 *
 * <p>Same role list as {@code PaymentController#record}: {@code
 * ADMIN}/{@code MANAGER}/{@code CASHIER} for {@link #apply}; {@link
 * #listByCommand} stays unprotected, same precedent as {@code
 * PaymentController#listByCommand}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiscountControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int FIXED_AMOUNT_NUMBER = 93;
    private static final int PERCENTAGE_NUMBER = 94;
    private static final int LIST_NUMBER = 95;
    private static final int VALIDATION_NUMBER = 96;
    private static final int RBAC_NUMBER = 97;
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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (int number : List.of(
                FIXED_AMOUNT_NUMBER, PERCENTAGE_NUMBER, LIST_NUMBER, VALIDATION_NUMBER, RBAC_NUMBER)) {
            Command command = command(number);
            discountRepository.findByCommandOrderByCreatedAtAsc(command).forEach(discountRepository::delete);
        }

        for (UUID orderId : createdOrderIds) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
            orderRepository.delete(order);
        }
        createdOrderIds.clear();

        createdProductIds.forEach(productRepository::deleteById);
        createdProductIds.clear();
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdCategoryIds.clear();

        for (int number : List.of(
                FIXED_AMOUNT_NUMBER, PERCENTAGE_NUMBER, LIST_NUMBER, VALIDATION_NUMBER, RBAC_NUMBER)) {
            Command command = command(number);
            command.setStatus(CommandStatus.AVAILABLE);
            commandRepository.save(command);
        }
    }

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    private void setOpen(int number) {
        Command command = command(number);
        command.setStatus(CommandStatus.OPEN);
        commandRepository.save(command);
    }

    private Product createActiveProduct(BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        createdCategoryIds.add(category.getId());
        Product product = productRepository.save(new Product("Café Espresso", price, category));
        createdProductIds.add(product.getId());
        return product;
    }

    // Same minimal "persist Order/OrderItem directly, bypass POST
    // /api/v1/orders" fixture PaymentControllerIntegrationTests#createOrder
    // uses — just enough for OrderService#getTotalOwed to report a
    // non-zero total.
    private void createOrder(int commandNumber, BigDecimal unitPrice, int quantity) {
        Command command = command(commandNumber);
        Product product = createActiveProduct(unitPrice);
        Order order = orderRepository.save(new Order(command));
        createdOrderIds.add(order.getId());
        orderItemRepository.save(new OrderItem(order, product, quantity, unitPrice));
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
    void appliesFixedAmountDiscount() throws Exception {
        setOpen(FIXED_AMOUNT_NUMBER);
        createOrder(FIXED_AMOUNT_NUMBER, new BigDecimal("50.00"), 1);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", FIXED_AMOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00, "reason": "Cliente fidelidade"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.percentage").doesNotExist())
                .andExpect(jsonPath("$.originalAmount").value(50.00))
                .andExpect(jsonPath("$.discountedAmount").value(10.00))
                .andExpect(jsonPath("$.reason").value("Cliente fidelidade"))
                .andExpect(jsonPath("$.appliedByUserName").value("Test User"));

        List<Discount> persisted = discountRepository.findByCommandOrderByCreatedAtAsc(command(FIXED_AMOUNT_NUMBER));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getDiscountedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void appliesPercentageDiscountComputedAgainstTotalOwed() throws Exception {
        setOpen(PERCENTAGE_NUMBER);
        createOrder(PERCENTAGE_NUMBER, new BigDecimal("95.00"), 1);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", PERCENTAGE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PERCENTAGE", "percentage": 10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.percentage").value(10))
                .andExpect(jsonPath("$.originalAmount").value(95.00))
                .andExpect(jsonPath("$.discountedAmount").value(9.50))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void listsDiscountsOldestFirst() throws Exception {
        setOpen(LIST_NUMBER);
        createOrder(LIST_NUMBER, new BigDecimal("100.00"), 1);
        String token = tokenFor(UserRole.CASHIER);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", LIST_NUMBER)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 5.00}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/commands/{number}/discounts", LIST_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].discountedAmount").value(5.00));
    }

    @Test
    void rejectsFixedAmountDiscountWithPercentagePresent() throws Exception {
        setOpen(VALIDATION_NUMBER);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00, "percentage": 5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(discountRepository.findByCommandOrderByCreatedAtAsc(command(VALIDATION_NUMBER))).isEmpty();
    }

    @Test
    void rejectsPercentageAbove100() throws Exception {
        setOpen(VALIDATION_NUMBER);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PERCENTAGE", "percentage": 150}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/discounts", RBAC_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsWhenCallerRoleIsNotAllowed() throws Exception {
        setOpen(RBAC_NUMBER);

        mockMvc.perform(post("/api/v1/commands/{number}/discounts", RBAC_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(discountRepository.findByCommandOrderByCreatedAtAsc(command(RBAC_NUMBER))).isEmpty();
    }

}
