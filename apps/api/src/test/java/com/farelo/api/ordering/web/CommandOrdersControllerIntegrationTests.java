package com.farelo.api.ordering.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/commands/{number}/orders},
 * against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (14, 15) — distinct from every
 * number already spoken for elsewhere in the command/ordering domains'
 * tests ({@code CommandControllerIntegrationTests}: 1-7, 999;
 * {@code CommandRepositoryIntegrationTests}: 101;
 * {@code OrderRepositoryIntegrationTests}: 8;
 * {@code OrderItemRepositoryIntegrationTests}: 9;
 * {@code OrderControllerIntegrationTests}: 10-12). No command status is
 * mutated here, only orders/items are added under these two dedicated
 * commands, so no {@code @AfterEach} cleanup is needed (same reasoning as
 * {@code OrderRepositoryIntegrationTests}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandOrdersControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_ORDERS = 14;
    private static final int COMMAND_WITHOUT_ORDERS = 15;

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

    private Product createActiveProduct(BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        return productRepository.save(new Product("Café Espresso", price, category));
    }

    @Test
    void listsOrdersForCommandInCreationOrderWithItems() throws Exception {
        Command command = commandRepository.findByNumber(COMMAND_WITH_ORDERS).orElseThrow();
        Product product = createActiveProduct(new BigDecimal("7.50"));

        Order firstOrder = orderRepository.save(new Order(command));
        orderItemRepository.save(new OrderItem(firstOrder, product, 1, product.getPrice()));

        // Guarantees a distinct, later createdAt for the second order, so
        // the "correct order" (oldest first) assertion below is
        // deterministic rather than relying on clock resolution alone.
        Thread.sleep(10);

        Order secondOrder = orderRepository.save(new Order(command));
        orderItemRepository.save(new OrderItem(secondOrder, product, 2, product.getPrice()));

        mockMvc.perform(get("/api/v1/commands/{number}/orders", COMMAND_WITH_ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(firstOrder.getId().toString()))
                .andExpect(jsonPath("$[0].commandNumber").value(COMMAND_WITH_ORDERS))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].quantity").value(1))
                .andExpect(jsonPath("$[1].id").value(secondOrder.getId().toString()))
                .andExpect(jsonPath("$[1].items", hasSize(1)))
                .andExpect(jsonPath("$[1].items[0].quantity").value(2));
    }

    @Test
    void returnsEmptyListForCommandWithoutOrders() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/orders", COMMAND_WITHOUT_ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsCommandNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/orders", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

}
