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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/orders}, against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (10-13) — distinct from every
 * number already spoken for by other test classes sharing the singleton
 * Postgres container (see {@code CommandControllerIntegrationTests}: 1-7,
 * 999; {@code CommandRepositoryIntegrationTests}: 101;
 * {@code OrderRepositoryIntegrationTests}: 8;
 * {@code OrderItemRepositoryIntegrationTests}: 9) — and resets them back to
 * {@code AVAILABLE} in {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_AVAILABLE = 10;
    private static final int COMMAND_OPEN = 11;
    private static final int COMMAND_CLOSED = 12;

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

    private Product createActiveProduct(BigDecimal price) {
        Category category = categoryRepository.save(new Category("Bebidas"));
        return productRepository.save(new Product("Café Espresso", price, category));
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
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

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

}
