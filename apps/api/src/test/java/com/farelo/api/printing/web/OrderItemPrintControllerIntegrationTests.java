package com.farelo.api.printing.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemCancelReason;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.printing.PrintJobRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST
 * /api/v1/orders/{orderId}/items/{itemId}/resend} (FARELO-318,
 * "Renotificar item") and {@code POST /api/v1/orders/{orderId}/resend}
 * (FARELO-319, "Enviar novamente os itens para produção"), against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command number 65 — free per the registry
 * {@code DiscountControllerIntegrationTests}/{@code
 * OrderItemDiscountControllerIntegrationTests}' javadoc maintains (60-64
 * were the highest reserved range at the time this class was written).
 *
 * <p>Unlike {@code PrintJobControllerIntegrationTests}, this class does not
 * clear the whole {@code print_job} table — it only asserts against the
 * jobs it creates for its own order, so it doesn't need to.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderItemPrintControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 65;
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
    private PrintJobRepository printJobRepository;

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
        for (UUID orderId : createdOrderIds) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            printJobRepository.findByOrder(order).forEach(printJobRepository::delete);
            orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
            orderRepository.delete(order);
        }
        createdOrderIds.clear();

        createdProductIds.forEach(productRepository::deleteById);
        createdProductIds.clear();
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdCategoryIds.clear();
    }

    private Command command() {
        return commandRepository.findByNumber(COMMAND_NUMBER).orElseThrow();
    }

    private Product createProduct(String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category("Cardápio"));
        createdCategoryIds.add(category.getId());
        Product product = productRepository.save(new Product(name, price, category));
        createdProductIds.add(product.getId());
        return product;
    }

    private OrderItem createOrderWithTwoItems() {
        Order order = orderRepository.save(new Order(command()));
        createdOrderIds.add(order.getId());

        Product espresso = createProduct("Café Espresso", new BigDecimal("7.50"));
        Product croissant = createProduct("Croissant", new BigDecimal("12.00"));
        orderItemRepository.save(new OrderItem(order, croissant, 2, new BigDecimal("12.00")));
        return orderItemRepository.save(new OrderItem(order, espresso, 1, new BigDecimal("7.50")));
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
    void resendsPendingPrintJobForOneItemOnly() throws Exception {
        OrderItem item = createOrderWithTwoItems();

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/resend", item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("KITCHEN_TICKET"))
                .andExpect(jsonPath("$.orderId").value(item.getOrder().getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.content.commandNumber").value(COMMAND_NUMBER))
                .andExpect(jsonPath("$.content.items", hasSize(1)))
                .andExpect(jsonPath("$.content.items[0].productName").value("Café Espresso"))
                .andExpect(jsonPath("$.content.items[0].quantity").value(1));
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/resend", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenItemBelongsToDifferentOrder() throws Exception {
        OrderItem item = createOrderWithTwoItems();
        // A second, real order — item.getId() doesn't belong to it, which
        // is exactly the case this test targets (as opposed to
        // returnsNotFoundForUnknownOrder above, where the order itself
        // doesn't exist at all).
        OrderItem itemFromOtherOrder = createOrderWithTwoItems();

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/resend",
                        itemFromOtherOrder.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsWithNoAuthorizationHeader() throws Exception {
        OrderItem item = createOrderWithTwoItems();

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/resend", item.getOrder().getId(), item.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWhenCallerRoleIsNotAllowed() throws Exception {
        OrderItem item = createOrderWithTwoItems();

        mockMvc.perform(post(
                        "/api/v1/orders/{orderId}/items/{itemId}/resend", item.getOrder().getId(), item.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isForbidden());
    }

    // FARELO-319: "Enviar novamente os itens para produção" — the whole-
    // order counterpart, reusing PrintJobService#createForOrder wholesale.
    @Test
    void resendAllCreatesOnePrintJobPerStation() throws Exception {
        OrderItem espresso = createOrderWithTwoItems();
        Order order = espresso.getOrder();

        mockMvc.perform(post("/api/v1/orders/{orderId}/resend", order.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("KITCHEN_TICKET"))
                .andExpect(jsonPath("$[0].content.items", hasSize(2)));
    }

    @Test
    void resendAllExcludesCancelledItems() throws Exception {
        OrderItem espresso = createOrderWithTwoItems();
        Order order = espresso.getOrder();

        // Cancel the other item (croissant) — resendAll must not include it.
        OrderItem croissant = orderItemRepository.findByOrder(order).stream()
                .filter(candidate -> !candidate.getId().equals(espresso.getId()))
                .findFirst().orElseThrow();
        croissant.cancel(UUID.randomUUID(), "Alguém", OrderItemCancelReason.ENTRY_ERROR, null);
        orderItemRepository.save(croissant);

        mockMvc.perform(post("/api/v1/orders/{orderId}/resend", order.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content.items", hasSize(1)))
                .andExpect(jsonPath("$[0].content.items[0].productName").value("Café Espresso"));
    }

    @Test
    void resendAllReturnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/resend", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

}
