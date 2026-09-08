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
import com.farelo.api.ordering.OrderItemCancelReason;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST
 * /api/v1/orders/{orderId}/items/{itemId}/discounts} (FARELO-317), against
 * a real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers 60-64 — free per the registry
 * {@code DiscountControllerIntegrationTests}' javadoc maintains (93-97 was
 * the highest reserved range at the time this class was written; 60-64
 * checked free against every other test class's own javadoc).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderItemDiscountControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int FIXED_AMOUNT_NUMBER = 60;
    private static final int PERCENTAGE_NUMBER = 61;
    private static final int VALIDATION_NUMBER = 62;
    private static final int RBAC_NUMBER = 63;
    private static final int CANCELLED_ITEM_NUMBER = 64;
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
                FIXED_AMOUNT_NUMBER, PERCENTAGE_NUMBER, VALIDATION_NUMBER, RBAC_NUMBER, CANCELLED_ITEM_NUMBER)) {
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
                FIXED_AMOUNT_NUMBER, PERCENTAGE_NUMBER, VALIDATION_NUMBER, RBAC_NUMBER, CANCELLED_ITEM_NUMBER)) {
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
    // /api/v1/orders" fixture DiscountControllerIntegrationTests#createOrder
    // uses.
    private OrderItem createOrderItem(int commandNumber, BigDecimal unitPrice, int quantity) {
        Command command = command(commandNumber);
        Product product = createActiveProduct(unitPrice);
        Order order = orderRepository.save(new Order(command));
        createdOrderIds.add(order.getId());
        return orderItemRepository.save(new OrderItem(order, product, quantity, unitPrice));
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
    void appliesFixedAmountDiscountToOneItemOnly() throws Exception {
        setOpen(FIXED_AMOUNT_NUMBER);
        OrderItem discountedItem = createOrderItem(FIXED_AMOUNT_NUMBER, new BigDecimal("50.00"), 1);
        OrderItem untouchedItem = createOrderItem(FIXED_AMOUNT_NUMBER, new BigDecimal("30.00"), 1);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        discountedItem.getOrder().getId(), discountedItem.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00, "reason": "Item amassado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.commandNumber").value(FIXED_AMOUNT_NUMBER))
                .andExpect(jsonPath("$.orderItemId").value(discountedItem.getId().toString()))
                .andExpect(jsonPath("$.originalAmount").value(50.00))
                .andExpect(jsonPath("$.discountedAmount").value(10.00))
                .andExpect(jsonPath("$.reason").value("Item amassado"));

        List<Discount> persisted = discountRepository.findByCommandOrderByCreatedAtAsc(command(FIXED_AMOUNT_NUMBER));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getOrderItem().getId()).isEqualTo(discountedItem.getId());

        // The comanda-level aggregate (used by PaymentService#getBalance)
        // already includes this item-scoped row with zero query change —
        // see DiscountRepository#sumDiscountedAmountByCommand's comment.
        assertThat(discountRepository.sumDiscountedAmountByCommand(command(FIXED_AMOUNT_NUMBER)))
                .isEqualByComparingTo("10.00");

        assertThat(untouchedItem.getId()).isNotEqualTo(discountedItem.getId());
    }

    @Test
    void appliesPercentageDiscountAgainstItemSubtotal() throws Exception {
        setOpen(PERCENTAGE_NUMBER);
        OrderItem item = createOrderItem(PERCENTAGE_NUMBER, new BigDecimal("40.00"), 2);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PERCENTAGE", "percentage": 10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalAmount").value(80.00))
                .andExpect(jsonPath("$.discountedAmount").value(8.00));
    }

    @Test
    void rejectsDiscountOnAlreadyCancelledItem() throws Exception {
        setOpen(CANCELLED_ITEM_NUMBER);
        OrderItem item = createOrderItem(CANCELLED_ITEM_NUMBER, new BigDecimal("20.00"), 1);
        item.cancel(UUID.randomUUID(), "Alguém", OrderItemCancelReason.ENTRY_ERROR, null);
        orderItemRepository.save(item);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 5.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_ALREADY_CANCELLED"));
    }

    @Test
    void returnsNotFoundWhenItemBelongsToDifferentOrder() throws Exception {
        setOpen(VALIDATION_NUMBER);
        OrderItem item = createOrderItem(VALIDATION_NUMBER, new BigDecimal("20.00"), 1);
        UUID otherOrderId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts", otherOrderId, item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 5.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsFixedAmountDiscountWithPercentagePresent() throws Exception {
        setOpen(VALIDATION_NUMBER);
        OrderItem item = createOrderItem(VALIDATION_NUMBER, new BigDecimal("20.00"), 1);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00, "percentage": 5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsWithNoAuthorizationHeader() throws Exception {
        setOpen(RBAC_NUMBER);
        OrderItem item = createOrderItem(RBAC_NUMBER, new BigDecimal("20.00"), 1);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        item.getOrder().getId(), item.getId())
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
        OrderItem item = createOrderItem(RBAC_NUMBER, new BigDecimal("20.00"), 1);

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts",
                        item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 10.00}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returnsConflictForBalcaoOrderItem() throws Exception {
        Product product = createActiveProduct(new BigDecimal("15.00"));
        Order balcaoOrder = orderRepository.save(new Order("Cliente Balcão", null, null));
        createdOrderIds.add(balcaoOrder.getId());
        OrderItem item = orderItemRepository.save(new OrderItem(balcaoOrder, product, 1, new BigDecimal("15.00")));

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/discounts", balcaoOrder.getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "FIXED_AMOUNT", "amount": 5.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_DISCOUNT_NOT_SUPPORTED"));
    }

}
