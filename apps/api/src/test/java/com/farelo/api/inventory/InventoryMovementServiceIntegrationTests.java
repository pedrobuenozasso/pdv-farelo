package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link InventoryMovementService#create(UUID, BigDecimal)}
 * (FARELO-094, "Criar entrada manual de estoque") and {@link
 * InventoryMovementService#consumeForOrder(UUID, List)} (FARELO-096,
 * "Consumir receita ao criar pedido") directly, against a real PostgreSQL
 * instance — each producer's own business logic (ingredient existence
 * check / recipe lookup / quantity math / the rows actually landing in the
 * ledger) rather than HTTP concerns, which are {@code
 * InventoryMovementControllerIntegrationTests}' job for FARELO-094, and
 * {@code OrderInventoryConsumptionIntegrationTests}' job for FARELO-096
 * (the full HTTP order-creation flow producing these same rows as a side
 * effect).
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * InventoryMovementRepositoryIntegrationTests}: every test creates its own
 * fresh ingredient(s)/product(s)/recipe(s) with unique names and only
 * asserts on movements scoped to those specific ids, so leftover rows from
 * other test classes sharing the singleton Postgres container never affect
 * an assertion here. Same "don't touch shared product/category tables"
 * caution already documented for {@code Recipe}/{@code RecipeItem} tests in
 * docs/domain-model.md — no {@code productRepository.deleteAll()}/{@code
 * categoryRepository.deleteAll()} anywhere in this class either.
 *
 * <p><b>{@code @AfterEach} does exist here, but it's targeted, not a blind
 * wipe</b> — found in review while adding the FARELO-096 {@code
 * consumeForOrder} tests below: {@code RecipeRepositoryIntegrationTests}/
 * {@code RecipeControllerIntegrationTests}/{@code
 * IngredientControllerIntegrationTests} each do their own blind {@code
 * recipeRepository.deleteAll()}/{@code ingredientRepository.deleteAll()} in
 * their own {@code @BeforeEach} (see those classes), and this class sorts
 * alphabetically before all three within the shared Postgres container's
 * single test run — so any {@code Recipe}/{@code RecipeItem} row left
 * behind here is exactly the kind of leftover that makes one of those
 * blind deletes fail with a foreign key violation (recipe_item still
 * referencing a recipe/ingredient row being deleted). Deleting only the
 * specific {@code RecipeItem} rows this class itself created — not
 * touching the table generally — avoids that without reintroducing a blind
 * wipe of a table other tests (e.g. {@code RecipeItemRepositoryIntegrationTests})
 * still rely on being left alone.
 *
 * <p><b>FARELO-097</b> ("Implementar idempotência da baixa de estoque")
 * added the {@code idempotent*}/{@code aggregat*} tests below, covering
 * {@link InventoryMovementService#consumeForOrder}'s new behavior: calling
 * it twice for the same order is safe (a full no-op the second time),
 * calling it again after a simulated partial completion only writes the
 * still-missing ingredient, and two products sharing an ingredient in the
 * same order are aggregated into a single ledger row rather than two. See
 * that method's own javadoc for the full design reasoning.
 */
@SpringBootTest
class InventoryMovementServiceIntegrationTests extends AbstractIntegrationTest {

    // Command #34 from the FARELO-031 seed — the next free number after
    // InventoryMovementRepositoryIntegrationTests (30)/
    // InventoryMovementControllerIntegrationTests (31)/
    // OrderInventoryConsumptionIntegrationTests (32-33). Only used to
    // satisfy inventory_movement.order_id's DB-level FK to orders(id) (see
    // InventoryMovement's javadoc, "orderId" section) for the
    // consumeForOrder tests below — a real Order row must exist for any
    // orderId this test writes an ORDER_CONSUMPTION movement against.
    private static final int SEEDED_COMMAND_NUMBER = 34;

    @Autowired
    private InventoryMovementService inventoryMovementService;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Tracks every RecipeItem this test class creates, so cleanUpRecipeItems
    // below can delete exactly those rows — see this class's javadoc for
    // why that's necessary here specifically.
    private final List<RecipeItem> createdRecipeItems = new ArrayList<>();

    @AfterEach
    void cleanUpRecipeItems() {
        recipeItemRepository.deleteAll(createdRecipeItems);
    }

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    private Product createActiveProduct(String name) {
        Category category = categoryRepository.save(new Category(name + " categoria"));
        return productRepository.save(new Product(name, new BigDecimal("10.00"), category));
    }

    private Recipe createActiveRecipe(Product product) {
        return recipeRepository.save(new Recipe(product));
    }

    private RecipeItem addRecipeItem(Recipe recipe, Ingredient ingredient, BigDecimal quantity) {
        RecipeItem saved = recipeItemRepository.save(new RecipeItem(recipe, ingredient, quantity));
        createdRecipeItems.add(saved);
        return saved;
    }

    // A real, persisted Order id — see SEEDED_COMMAND_NUMBER's javadoc for
    // why a plain UUID.randomUUID() won't do here.
    private UUID createOrderId() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        return orderRepository.save(new Order(command)).getId();
    }

    @Test
    void createsPurchaseMovementLinkedToIngredient() {
        Ingredient coffee = createIngredient("Café em grão", IngredientUnit.GRAM);

        InventoryMovement created = inventoryMovementService.create(coffee.getId(), new BigDecimal("2000"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(created.getQuantity()).isEqualByComparingTo("2000");
        assertThat(created.getIngredient().getId()).isEqualTo(coffee.getId());
        assertThat(created.getOrderId()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(created.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("2000.000");
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(coffee.getId());
    }

    @Test
    void throwsIngredientNotFoundWhenCreatingMovementForUnknownIngredient() {
        UUID missingIngredientId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryMovementService.create(missingIngredientId, BigDecimal.TEN))
                .isInstanceOf(IngredientNotFoundException.class);

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(missingIngredientId)).isEmpty();
    }

    // FARELO-096 — quantity math is the core of this ticket: RecipeItem's
    // quantity is "per one unit of the product", so ordering 4 units must
    // multiply, not just negate. 3 UN eggs * 4 = 12 negated; 80 G bacon * 4
    // = 320 negated.
    @Test
    void consumesRecipeMultiplyingRecipeItemQuantityByOrderedQuantity() {
        Ingredient eggs = createIngredient("Ovos (FARELO-096 svc)", IngredientUnit.UNIT);
        Ingredient bacon = createIngredient("Bacon (FARELO-096 svc)", IngredientUnit.GRAM);
        Product sandwich = createActiveProduct("Pão com ovos e bacon (FARELO-096 svc)");
        Recipe recipe = createActiveRecipe(sandwich);
        addRecipeItem(recipe, eggs, new BigDecimal("3"));
        addRecipeItem(recipe, bacon, new BigDecimal("80"));

        UUID orderId = createOrderId();
        List<InventoryMovement> movements = inventoryMovementService.consumeForOrder(
                orderId, List.of(new OrderItemConsumption(sandwich.getId(), 4)));

        assertThat(movements).hasSize(2);
        assertThat(movements).allSatisfy(movement -> {
            assertThat(movement.getType()).isEqualTo(InventoryMovementType.ORDER_CONSUMPTION);
            assertThat(movement.getOrderId()).isEqualTo(orderId);
        });

        InventoryMovement eggsMovement = movements.stream()
                .filter(m -> m.getIngredient().getId().equals(eggs.getId()))
                .findFirst().orElseThrow();
        assertThat(eggsMovement.getQuantity()).isEqualByComparingTo("-12");

        InventoryMovement baconMovement = movements.stream()
                .filter(m -> m.getIngredient().getId().equals(bacon.getId()))
                .findFirst().orElseThrow();
        assertThat(baconMovement.getQuantity()).isEqualByComparingTo("-320");

        // Persisted, not just returned.
        List<InventoryMovement> eggsLedger = inventoryMovementRepository
                .findByIngredientIdOrderByCreatedAtAsc(eggs.getId());
        assertThat(eggsLedger).hasSize(1);
        assertThat(eggsLedger.get(0).getQuantity()).isEqualByComparingTo("-12");
    }

    @Test
    void producesNoMovementsForProductWithoutActiveRecipe() {
        Product noRecipeProduct = createActiveProduct("Suco (sem receita, FARELO-096 svc)");

        List<InventoryMovement> movements = inventoryMovementService.consumeForOrder(
                UUID.randomUUID(), List.of(new OrderItemConsumption(noRecipeProduct.getId(), 5)));

        assertThat(movements).isEmpty();
    }

    // A deactivated recipe must not be consumed either — same
    // "findByProductIdAndActiveTrue" natural key Recipe already enforces
    // elsewhere (RecipeService#create's duplicate check).
    @Test
    void producesNoMovementsForProductWithOnlyADeactivatedRecipe() {
        Ingredient milk = createIngredient("Leite (FARELO-096 svc, receita inativa)", IngredientUnit.MILLILITER);
        Product product = createActiveProduct("Café com leite (receita inativa, FARELO-096 svc)");
        Recipe recipe = createActiveRecipe(product);
        addRecipeItem(recipe, milk, new BigDecimal("100"));
        recipe.setActive(false);
        recipeRepository.save(recipe);

        List<InventoryMovement> movements = inventoryMovementService.consumeForOrder(
                UUID.randomUUID(), List.of(new OrderItemConsumption(product.getId(), 1)));

        assertThat(movements).isEmpty();
    }

    // Multiple items across multiple products, each with its own recipe,
    // must produce movements scoped correctly to each ingredient — no
    // cross-contamination between recipes/products in the same call.
    @Test
    void consumesMultipleProductsEachWithOwnRecipeScopedCorrectly() {
        Ingredient coffee = createIngredient("Café em grão (FARELO-096 svc multi)", IngredientUnit.GRAM);
        Ingredient milk = createIngredient("Leite (FARELO-096 svc multi)", IngredientUnit.MILLILITER);
        Ingredient bread = createIngredient("Pão (FARELO-096 svc multi)", IngredientUnit.UNIT);

        Product latte = createActiveProduct("Café com leite (FARELO-096 svc multi)");
        Recipe latteRecipe = createActiveRecipe(latte);
        addRecipeItem(latteRecipe, coffee, new BigDecimal("18"));
        addRecipeItem(latteRecipe, milk, new BigDecimal("150"));

        Product toast = createActiveProduct("Torrada (FARELO-096 svc multi)");
        Recipe toastRecipe = createActiveRecipe(toast);
        addRecipeItem(toastRecipe, bread, new BigDecimal("2"));

        Product noRecipeProduct = createActiveProduct("Água (sem receita, FARELO-096 svc multi)");

        UUID orderId = createOrderId();
        List<InventoryMovement> movements = inventoryMovementService.consumeForOrder(orderId, List.of(
                new OrderItemConsumption(latte.getId(), 2),
                new OrderItemConsumption(toast.getId(), 3),
                new OrderItemConsumption(noRecipeProduct.getId(), 1)));

        // 2 (latte) + 1 (toast) = 3 movements; noRecipeProduct contributes none.
        assertThat(movements).hasSize(3);

        InventoryMovement coffeeMovement = movements.stream()
                .filter(m -> m.getIngredient().getId().equals(coffee.getId()))
                .findFirst().orElseThrow();
        assertThat(coffeeMovement.getQuantity()).isEqualByComparingTo("-36");
        assertThat(coffeeMovement.getOrderId()).isEqualTo(orderId);

        InventoryMovement milkMovement = movements.stream()
                .filter(m -> m.getIngredient().getId().equals(milk.getId()))
                .findFirst().orElseThrow();
        assertThat(milkMovement.getQuantity()).isEqualByComparingTo("-300");

        InventoryMovement breadMovement = movements.stream()
                .filter(m -> m.getIngredient().getId().equals(bread.getId()))
                .findFirst().orElseThrow();
        assertThat(breadMovement.getQuantity()).isEqualByComparingTo("-6");
    }

    // FARELO-097 — core idempotency guarantee: calling consumeForOrder
    // twice with the exact same arguments must not double-deduct stock.
    // First call writes the real movements; second call must write nothing
    // new (empty return list) and the ledger must still only contain the
    // first call's rows.
    @Test
    void secondCallForSameOrderAndItemsProducesNoAdditionalMovements() {
        Ingredient eggs = createIngredient("Ovos (FARELO-097 svc idempotent)", IngredientUnit.UNIT);
        Ingredient bacon = createIngredient("Bacon (FARELO-097 svc idempotent)", IngredientUnit.GRAM);
        Product sandwich = createActiveProduct("Pão com ovos e bacon (FARELO-097 svc idempotent)");
        Recipe recipe = createActiveRecipe(sandwich);
        addRecipeItem(recipe, eggs, new BigDecimal("3"));
        addRecipeItem(recipe, bacon, new BigDecimal("80"));

        UUID orderId = createOrderId();
        List<OrderItemConsumption> items = List.of(new OrderItemConsumption(sandwich.getId(), 4));

        List<InventoryMovement> firstCall = inventoryMovementService.consumeForOrder(orderId, items);
        assertThat(firstCall).hasSize(2);

        List<InventoryMovement> secondCall = inventoryMovementService.consumeForOrder(orderId, items);
        assertThat(secondCall).isEmpty();

        // The ledger itself — not just the return value — must still only
        // hold the first call's two rows: one per ingredient, not two.
        assertThat(inventoryMovementRepository.findByOrderId(orderId)).hasSize(2);
        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(eggs.getId())).hasSize(1);
        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(bacon.getId())).hasSize(1);
    }

    // Calling a third time (or any further time) must remain just as safe
    // — not a "first retry only" guarantee.
    @Test
    void thirdCallForSameOrderAndItemsAlsoProducesNoAdditionalMovements() {
        Ingredient milk = createIngredient("Leite (FARELO-097 svc idempotent x3)", IngredientUnit.MILLILITER);
        Product latte = createActiveProduct("Café com leite (FARELO-097 svc idempotent x3)");
        Recipe recipe = createActiveRecipe(latte);
        addRecipeItem(recipe, milk, new BigDecimal("150"));

        UUID orderId = createOrderId();
        List<OrderItemConsumption> items = List.of(new OrderItemConsumption(latte.getId(), 2));

        inventoryMovementService.consumeForOrder(orderId, items);
        inventoryMovementService.consumeForOrder(orderId, items);
        List<InventoryMovement> thirdCall = inventoryMovementService.consumeForOrder(orderId, items);

        assertThat(thirdCall).isEmpty();
        assertThat(inventoryMovementRepository.findByOrderId(orderId)).hasSize(1);
    }

    // FARELO-097 — partial-completion scenario: simulates a previous call
    // that wrote ingredient A's row and then crashed before reaching
    // ingredient B (both belong to the same recipe/order here). A retry
    // must skip A (already recorded — no double deduction) and write only
    // B (still missing) — not silently no-op the whole call (which would
    // leave B permanently un-deducted) and not error out.
    @Test
    void retryAfterPartialCompletionOnlyWritesTheMissingIngredient() {
        Ingredient eggs = createIngredient("Ovos (FARELO-097 svc partial)", IngredientUnit.UNIT);
        Ingredient bacon = createIngredient("Bacon (FARELO-097 svc partial)", IngredientUnit.GRAM);
        Product sandwich = createActiveProduct("Pão com ovos e bacon (FARELO-097 svc partial)");
        Recipe recipe = createActiveRecipe(sandwich);
        addRecipeItem(recipe, eggs, new BigDecimal("3"));
        addRecipeItem(recipe, bacon, new BigDecimal("80"));

        UUID orderId = createOrderId();

        // Simulate "a previous call already wrote eggs' row, then crashed
        // before writing bacon's" by writing exactly that row directly via
        // the repository, bypassing the service.
        inventoryMovementRepository.saveAndFlush(new InventoryMovement(
                eggs, new BigDecimal("-12"), InventoryMovementType.ORDER_CONSUMPTION, orderId));

        List<InventoryMovement> retryResult = inventoryMovementService.consumeForOrder(
                orderId, List.of(new OrderItemConsumption(sandwich.getId(), 4)));

        // Only bacon's row is newly written by this call.
        assertThat(retryResult).hasSize(1);
        assertThat(retryResult.get(0).getIngredient().getId()).isEqualTo(bacon.getId());
        assertThat(retryResult.get(0).getQuantity()).isEqualByComparingTo("-320");

        // The ledger now has both: the pre-existing eggs row (untouched,
        // not duplicated) and the newly-completed bacon row.
        List<InventoryMovement> allMovements = inventoryMovementRepository.findByOrderId(orderId);
        assertThat(allMovements).hasSize(2);
        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(eggs.getId())).hasSize(1);
        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(bacon.getId())).hasSize(1);
    }

    // FARELO-097 — aggregation: two different products in the same order
    // that both consume the same ingredient must produce exactly ONE
    // ORDER_CONSUMPTION row for that ingredient (summed quantity), not one
    // row per product/recipe line — required by the (type, orderId,
    // ingredientId) idempotency key itself (see consumeForOrder's javadoc).
    @Test
    void aggregatesQuantityWhenTwoProductsInSameOrderShareAnIngredient() {
        Ingredient milk = createIngredient("Leite (FARELO-097 svc aggregate)", IngredientUnit.MILLILITER);

        Product latte = createActiveProduct("Café com leite (FARELO-097 svc aggregate)");
        Recipe latteRecipe = createActiveRecipe(latte);
        addRecipeItem(latteRecipe, milk, new BigDecimal("150"));

        Product cappuccino = createActiveProduct("Cappuccino (FARELO-097 svc aggregate)");
        Recipe cappuccinoRecipe = createActiveRecipe(cappuccino);
        addRecipeItem(cappuccinoRecipe, milk, new BigDecimal("100"));

        UUID orderId = createOrderId();
        List<InventoryMovement> movements = inventoryMovementService.consumeForOrder(orderId, List.of(
                new OrderItemConsumption(latte.getId(), 2),
                new OrderItemConsumption(cappuccino.getId(), 1)));

        // 2 * 150 (latte) + 1 * 100 (cappuccino) = 400, negated: -400.
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getIngredient().getId()).isEqualTo(milk.getId());
        assertThat(movements.get(0).getQuantity()).isEqualByComparingTo("-400");

        // Persisted as a single ledger row too, not two.
        assertThat(inventoryMovementRepository.findByOrderId(orderId)).hasSize(1);
    }

}
