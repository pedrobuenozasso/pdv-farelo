package com.farelo.api.inventory.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementRepository;
import com.farelo.api.inventory.InventoryMovementType;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/ingredients/{ingredientId}/movements},
 * against a real PostgreSQL instance (Testcontainers).
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * RecipeItemControllerIntegrationTests}: every test creates its own fresh
 * ingredient and only asserts on data scoped to that specific ingredient's
 * id, so leftover rows from other test classes never affect an assertion
 * here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryMovementControllerIntegrationTests extends AbstractIntegrationTest {

    // Command #31 from the FARELO-031 seed — distinct from
    // InventoryMovementRepositoryIntegrationTests' #30 and every other
    // command number already spoken for in this suite.
    private static final int SEEDED_COMMAND_NUMBER = 31;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    // order_id carries a real DB-level FK to orders(id) — a random UUID is
    // rejected by that constraint, so a real persisted Order is needed (see
    // InventoryMovementRepositoryIntegrationTests#createOrder).
    private Order createOrder() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        return orderRepository.save(new Order(command));
    }

    @Test
    void returnsEmptyListWhenIngredientHasNoMovements() throws Exception {
        Ingredient ingredient = createIngredient("Canela", IngredientUnit.GRAM);

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsIngredientNotFoundWhenListingMovementsOfUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", missingIngredientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsMovementsOldestFirstScopedToIngredientWithOrderIdWhenPresent() throws Exception {
        Ingredient flour = createIngredient("Farinha de trigo", IngredientUnit.GRAM);
        Ingredient sugar = createIngredient("Açúcar", IngredientUnit.GRAM);
        UUID orderId = createOrder().getId();

        InventoryMovement purchase = inventoryMovementRepository.save(
                new InventoryMovement(flour, new BigDecimal("5000"), InventoryMovementType.PURCHASE));
        InventoryMovement consumption = inventoryMovementRepository.save(new InventoryMovement(
                flour, new BigDecimal("-500"), InventoryMovementType.ORDER_CONSUMPTION, orderId));
        // A movement on a different ingredient must not leak into flour's list.
        inventoryMovementRepository.save(
                new InventoryMovement(sugar, new BigDecimal("2000"), InventoryMovementType.PURCHASE));

        MvcResult result = mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", flour.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(purchase.getId().toString()))
                .andExpect(jsonPath("$[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$[0].quantity").value(5000))
                .andExpect(jsonPath("$[0].orderId").value(nullValue()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].id").value(consumption.getId().toString()))
                .andExpect(jsonPath("$[1].type").value("ORDER_CONSUMPTION"))
                .andExpect(jsonPath("$[1].quantity").value(-500))
                .andExpect(jsonPath("$[1].orderId").value(orderId.toString()))
                .andReturn();

        List<InventoryMovementResponse> movements = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InventoryMovementResponse.class));

        assertThat(movements).extracting(InventoryMovementResponse::ingredientId)
                .containsOnly(flour.getId());
    }

}
