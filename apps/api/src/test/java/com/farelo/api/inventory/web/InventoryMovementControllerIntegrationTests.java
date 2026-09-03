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
import org.springframework.http.MediaType;
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
 * Integration test for {@code POST}/{@code GET
 * /api/v1/ingredients/{ingredientId}/movements}, {@code GET
 * /api/v1/ingredients/{ingredientId}/balance}, and {@code POST
 * /api/v1/ingredients/{ingredientId}/losses}, against a real PostgreSQL
 * instance (Testcontainers). {@code POST .../movements} is FARELO-094
 * ("Criar entrada manual de estoque"); {@code GET .../movements} is
 * FARELO-093; {@code GET .../balance} is FARELO-095 ("Calcular saldo do
 * ingrediente"); {@code POST .../losses} is FARELO-098 ("Criar movimento de
 * perda") (see the class javadoc history above/git log for that original
 * scope).
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
    void createsPurchaseMovementAndPersistsIt() throws Exception {
        Ingredient beans = createIngredient("Feijão", IngredientUnit.GRAM);

        MvcResult result = mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 3000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ingredientId").value(beans.getId().toString()))
                .andExpect(jsonPath("$.quantity").value(3000))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        InventoryMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), InventoryMovementResponse.class);

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(beans.getId());
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("3000.000");
    }

    @Test
    void returnsIngredientNotFoundWhenCreatingMovementForUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", missingIngredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWithStandardErrorFormat() throws Exception {
        Ingredient rice = createIngredient("Arroz", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", rice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(rice.getId())).isEmpty();
    }

    @Test
    void rejectsNegativeQuantityWithStandardErrorFormat() throws Exception {
        Ingredient salt = createIngredient("Sal", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", salt.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": -50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingQuantityWithStandardErrorFormat() throws Exception {
        Ingredient pepper = createIngredient("Pimenta", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", pepper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
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

    @Test
    void returnsZeroBalanceWhenIngredientHasNoMovements() throws Exception {
        Ingredient ingredient = createIngredient("Cardamomo", IngredientUnit.GRAM);

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(ingredient.getId().toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

    @Test
    void returnsBalanceAsSumOfMovementsScopedToIngredient() throws Exception {
        Ingredient cocoa = createIngredient("Cacau em pó", IngredientUnit.GRAM);
        Ingredient vanilla = createIngredient("Baunilha", IngredientUnit.MILLILITER);
        UUID orderId = createOrder().getId();

        inventoryMovementRepository.save(
                new InventoryMovement(cocoa, new BigDecimal("2000"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.save(
                new InventoryMovement(cocoa, new BigDecimal("500"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.save(new InventoryMovement(
                cocoa, new BigDecimal("-300"), InventoryMovementType.ORDER_CONSUMPTION, orderId));
        // A movement on a different ingredient must not affect cocoa's balance.
        inventoryMovementRepository.save(
                new InventoryMovement(vanilla, new BigDecimal("1000"), InventoryMovementType.PURCHASE));

        // 2000 + 500 - 300 = 2200
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", cocoa.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(cocoa.getId().toString()))
                .andExpect(jsonPath("$.balance").value(2200))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

    @Test
    void returnsIngredientNotFoundWhenGettingBalanceOfUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", missingIngredientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-098 — "Criar movimento de perda". POST .../losses takes a
    // POSITIVE quantity (how much was lost) and must persist a LOSS row
    // with that quantity negated, no orderId.
    @Test
    void createsLossMovementWithNegatedQuantityAndNoOrderId() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-098)", IngredientUnit.GRAM);

        MvcResult result = mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", beans.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 250}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ingredientId").value(beans.getId().toString()))
                .andExpect(jsonPath("$.quantity").value(-250))
                .andExpect(jsonPath("$.type").value("LOSS"))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        InventoryMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), InventoryMovementResponse.class);

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.LOSS);
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(beans.getId());
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("-250.000");
        assertThat(persisted.get().getOrderId()).isNull();
    }

    @Test
    void returnsIngredientNotFoundWhenRecordingLossForUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", missingIngredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient rice = createIngredient("Arroz (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", rice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(rice.getId())).isEmpty();
    }

    @Test
    void rejectsNegativeQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient salt = createIngredient("Sal (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", salt.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": -50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient pepper = createIngredient("Pimenta (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", pepper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // Confirms the balance endpoint (FARELO-095) correctly reflects a
    // recorded loss — it must go down, since balance is a plain
    // SUM(quantity) over the ledger and a LOSS row is negative.
    @Test
    void balanceReflectsRecordedLoss() throws Exception {
        Ingredient sugar = createIngredient("Açúcar (FARELO-098 balance)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", sugar.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 5000}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", sugar.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 800}
                                """))
                .andExpect(status().isCreated());

        // 5000 - 800 = 4200
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", sugar.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(sugar.getId().toString()))
                .andExpect(jsonPath("$.balance").value(4200))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

}
