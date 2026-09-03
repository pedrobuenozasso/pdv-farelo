package com.farelo.api.ordering.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementRepository;
import com.farelo.api.inventory.InventoryMovementType;
import com.farelo.api.inventory.Recipe;
import com.farelo.api.inventory.RecipeItem;
import com.farelo.api.inventory.RecipeItemRepository;
import com.farelo.api.inventory.RecipeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FARELO-096 ("Consumir receita ao criar pedido", prompt mestre seção 16):
 * verifies the full HTTP order-creation flow ({@code POST /api/v1/orders},
 * the same production entry point {@code OrderControllerIntegrationTests}
 * already exercises for FARELO-052/053/060) also produces the correct
 * negative {@code ORDER_CONSUMPTION} {@link InventoryMovement} rows as a
 * side effect, against a real PostgreSQL instance. {@code
 * InventoryMovementServiceIntegrationTests} already covers {@link
 * com.farelo.api.inventory.InventoryMovementService#consumeForOrder}'s own
 * quantity-math/no-recipe/multi-product logic in isolation — this class
 * instead proves the wiring: that {@code OrderService#create} actually
 * calls it, with the real {@code orderId} the HTTP endpoint just created.
 *
 * <p>Uses dedicated seeded command numbers (32-33) — the next free numbers
 * after {@code InventoryMovementRepositoryIntegrationTests}/{@code
 * InventoryMovementControllerIntegrationTests} (30-31); see those classes'
 * {@code SEEDED_COMMAND_NUMBER} and {@code OrderControllerIntegrationTests}'
 * class javadoc for the full registry of numbers already claimed by other
 * test classes sharing the singleton Postgres container — and resets them
 * back to {@code AVAILABLE} in {@code @AfterEach}, same pattern as {@code
 * OrderControllerIntegrationTests}.
 *
 * <p>No {@code @BeforeEach} table cleanup, and no {@code
 * productRepository.deleteAll()}/{@code categoryRepository.deleteAll()}
 * anywhere in this class — same test-isolation caution already documented
 * in docs/domain-model.md for {@code Recipe}/{@code RecipeItem}/{@code
 * InventoryMovement} tests (a blind {@code deleteAll()} on shared {@code
 * product}/{@code category} tables can hit a still-referenced {@code
 * order_item} row from another test class, depending on run order). Every
 * test here creates its own uniquely-named ingredient/product/recipe and
 * only asserts on movements scoped to the order it itself created.
 *
 * <p><b>{@code @AfterEach} does delete this class's own {@code RecipeItem}
 * rows, though — found in review</b>: {@code RecipeRepositoryIntegrationTests}/
 * {@code RecipeControllerIntegrationTests}/{@code
 * IngredientControllerIntegrationTests} each run their own blind {@code
 * recipeRepository.deleteAll()}/{@code ingredientRepository.deleteAll()} in
 * their {@code @BeforeEach}, and — depending on Surefire's actual run
 * order, which is <em>not</em> simple package/class-name alphabetical — this
 * class can execute before them in the same suite run. A {@code Recipe}/
 * {@code RecipeItem} row left behind here is then exactly what makes one of
 * those blind deletes fail with a foreign key violation (recipe_item still
 * referencing the recipe/ingredient row being deleted) — see {@code
 * InventoryMovementServiceIntegrationTests}' equivalent note, which hit the
 * same failure first. Deleting only the specific {@code RecipeItem} rows
 * this class itself created avoids that without touching the table for
 * anyone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderInventoryConsumptionIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_RECIPE = 32;
    private static final int COMMAND_WITHOUT_RECIPE = 33;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Tracks every RecipeItem this test class creates — see this class's
    // javadoc for why cleanUp below deletes exactly these rows.
    private final List<RecipeItem> createdRecipeItems = new ArrayList<>();

    @AfterEach
    void resetTestCommands() {
        resetToAvailable(COMMAND_WITH_RECIPE);
        resetToAvailable(COMMAND_WITHOUT_RECIPE);
    }

    @AfterEach
    void cleanUpRecipeItems() {
        recipeItemRepository.deleteAll(createdRecipeItems);
    }

    private void resetToAvailable(int number) {
        Command command = commandRepository.findByNumber(number).orElseThrow();
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    private Product createActiveProduct(String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category(name + " categoria"));
        return productRepository.save(new Product(name, price, category));
    }

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    private Recipe createActiveRecipe(Product product) {
        return recipeRepository.save(new Recipe(product));
    }

    private void addRecipeItem(Recipe recipe, Ingredient ingredient, BigDecimal quantity) {
        createdRecipeItems.add(recipeItemRepository.save(new RecipeItem(recipe, ingredient, quantity)));
    }

    private UUID createOrderAndGetId(int commandNumber, Product product, int quantity) throws Exception {
        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": %d}]
                }
                """.formatted(commandNumber, product.getId(), quantity);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), OrderResponse.class).id();
    }

    // Core scenario: order quantity > 1 to prove RecipeItem.quantity is
    // multiplied, not just negated (prompt mestre seção 15's own example —
    // "pão com ovos e bacon" — scaled by an order of 4).
    @Test
    void createsNegativeOrderConsumptionMovementsMultipliedByOrderedQuantity() throws Exception {
        Ingredient eggs = createIngredient("Ovos (FARELO-096 http)", IngredientUnit.UNIT);
        Ingredient bacon = createIngredient("Bacon (FARELO-096 http)", IngredientUnit.GRAM);
        Ingredient butter = createIngredient("Manteiga (FARELO-096 http)", IngredientUnit.GRAM);
        Product sandwich = createActiveProduct("Pão com ovos e bacon (FARELO-096 http)", new BigDecimal("15.00"));
        Recipe recipe = createActiveRecipe(sandwich);
        addRecipeItem(recipe, eggs, new BigDecimal("3"));
        addRecipeItem(recipe, bacon, new BigDecimal("80"));
        addRecipeItem(recipe, butter, new BigDecimal("10"));

        UUID orderId = createOrderAndGetId(COMMAND_WITH_RECIPE, sandwich, 4);

        List<InventoryMovement> movements = inventoryMovementRepository.findByOrderId(orderId);
        assertThat(movements).hasSize(3);
        assertThat(movements).allSatisfy(movement -> {
            assertThat(movement.getType()).isEqualTo(InventoryMovementType.ORDER_CONSUMPTION);
            assertThat(movement.getOrderId()).isEqualTo(orderId);
        });

        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(eggs.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-12");
        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(bacon.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-320");
        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(butter.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-40");
    }

    @Test
    void createsNoInventoryMovementsForProductWithoutRecipe() throws Exception {
        Product noRecipeProduct = createActiveProduct("Suco (sem receita, FARELO-096 http)", new BigDecimal("8.00"));

        UUID orderId = createOrderAndGetId(COMMAND_WITHOUT_RECIPE, noRecipeProduct, 3);

        assertThat(inventoryMovementRepository.findByOrderId(orderId)).isEmpty();
    }

    // Multiple items on the same order, across multiple products each with
    // their own recipe — plus one product with no recipe mixed in — must
    // produce movements correctly scoped per product/ingredient, and only
    // for the items that actually have an active recipe.
    @Test
    void createsScopedMovementsForOrderWithMultipleProductsAcrossMultipleRecipes() throws Exception {
        Ingredient coffee = createIngredient("Café em grão (FARELO-096 http multi)", IngredientUnit.GRAM);
        Ingredient milk = createIngredient("Leite (FARELO-096 http multi)", IngredientUnit.MILLILITER);
        Ingredient bread = createIngredient("Pão (FARELO-096 http multi)", IngredientUnit.UNIT);

        Product latte = createActiveProduct("Café com leite (FARELO-096 http multi)", new BigDecimal("9.00"));
        Recipe latteRecipe = createActiveRecipe(latte);
        addRecipeItem(latteRecipe, coffee, new BigDecimal("18"));
        addRecipeItem(latteRecipe, milk, new BigDecimal("150"));

        Product toast = createActiveProduct("Torrada (FARELO-096 http multi)", new BigDecimal("6.00"));
        Recipe toastRecipe = createActiveRecipe(toast);
        addRecipeItem(toastRecipe, bread, new BigDecimal("2"));

        Product water = createActiveProduct("Água (sem receita, FARELO-096 http multi)", new BigDecimal("4.00"));

        String body = """
                {
                  "commandNumber": %d,
                  "items": [
                    {"productId": "%s", "quantity": 2},
                    {"productId": "%s", "quantity": 3},
                    {"productId": "%s", "quantity": 1}
                  ]
                }
                """.formatted(COMMAND_WITH_RECIPE, latte.getId(), toast.getId(), water.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID orderId = objectMapper.readValue(result.getResponse().getContentAsString(), OrderResponse.class).id();

        List<InventoryMovement> movements = inventoryMovementRepository.findByOrderId(orderId);
        // 2 (latte: coffee + milk) + 1 (toast: bread) = 3; water contributes none.
        assertThat(movements).hasSize(3);

        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(coffee.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-36");
        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(milk.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-300");
        assertThat(movements.stream()
                .filter(m -> m.getIngredient().getId().equals(bread.getId()))
                .findFirst().orElseThrow().getQuantity()).isEqualByComparingTo("-6");
    }

}
