package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link InventoryMovement} maps correctly onto the table
 * created by {@code V21__create_inventory_movement_table.sql}, against a
 * real PostgreSQL instance — including that both positive and negative
 * {@code quantity} values round-trip, that {@code findByIngredientIdOrderByCreatedAtAsc}
 * returns rows oldest-first and scoped to the right ingredient, and that
 * {@code sumQuantityByIngredientId} computes the running balance the way
 * {@link InventoryMovement}'s javadoc defines it (plain {@code SUM}).
 *
 * <p>No {@code @BeforeEach} table cleanup, same reasoning as {@code
 * RecipeItemRepositoryIntegrationTests}: every test creates its own fresh
 * ingredient and only ever queries/asserts scoped to that specific
 * ingredient's id, so leftover rows from other test classes sharing the
 * singleton Postgres container (see {@link AbstractIntegrationTest}) never
 * affect an assertion here.
 *
 * <p><b>FARELO-097</b> ("Implementar idempotência da baixa de estoque")
 * added the {@code idempotencyPartialUniqueIndex*} tests below, verifying
 * {@code idx_inventory_movement_order_consumption} (see
 * {@code V23__add_inventory_movement_order_consumption_unique_index.sql})
 * directly at the JPA/DB mapping level — the same layer this class already
 * tests everything else at. Service-level idempotency behavior of {@link
 * InventoryMovementService#consumeForOrder} (the pre-check, the
 * partial-completion retry, the aggregation across recipe lines) is {@code
 * InventoryMovementServiceIntegrationTests}' job instead, same division of
 * labor as every other repository/service test pair in this domain.
 */
@SpringBootTest
class InventoryMovementRepositoryIntegrationTests extends AbstractIntegrationTest {

    // Command #30 from the FARELO-031 seed — distinct from every command
    // number already spoken for by other repository tests (see e.g. the
    // comment in PrintJobRepositoryIntegrationTests: 8/9/16/17 already
    // taken).
    private static final int SEEDED_COMMAND_NUMBER = 30;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    // order_id carries a real DB-level FK to orders(id) (see
    // InventoryMovement's javadoc) — a random UUID is rejected by that
    // constraint, so tests exercising a non-null orderId need an actual
    // persisted Order, same fixture pattern as
    // PrintJobRepositoryIntegrationTests#savesAndFindsPrintJobLinkedToOrder.
    private Order createOrder() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        return orderRepository.save(new Order(command));
    }

    @Test
    void savesAndFindsPositiveMovement() {
        Ingredient milk = createIngredient("Leite", IngredientUnit.MILLILITER);

        InventoryMovement saved = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(milk, new BigDecimal("1000"), InventoryMovementType.PURCHASE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getIngredient().getId()).isEqualTo(milk.getId());
        assertThat(saved.getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(saved.getOrderId()).isNull();

        Optional<InventoryMovement> found = inventoryMovementRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualByComparingTo("1000.000");
    }

    @Test
    void savesAndFindsNegativeMovementWithOrderId() {
        Ingredient egg = createIngredient("Ovo", IngredientUnit.UNIT);
        UUID orderId = createOrder().getId();

        InventoryMovement saved = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(egg, new BigDecimal("-3"), InventoryMovementType.ORDER_CONSUMPTION, orderId));

        Optional<InventoryMovement> found = inventoryMovementRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualByComparingTo("-3.000");
        assertThat(found.get().getType()).isEqualTo(InventoryMovementType.ORDER_CONSUMPTION);
        assertThat(found.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void findByIngredientIdOrderByCreatedAtAscReturnsOldestFirstScopedToIngredient() {
        Ingredient flour = createIngredient("Farinha de trigo", IngredientUnit.GRAM);
        Ingredient sugar = createIngredient("Açúcar", IngredientUnit.GRAM);

        InventoryMovement first = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(flour, new BigDecimal("5000"), InventoryMovementType.PURCHASE));
        InventoryMovement second = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(flour, new BigDecimal("-500"), InventoryMovementType.ORDER_CONSUMPTION));
        // A movement on a different ingredient must not leak into flour's list.
        inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(sugar, new BigDecimal("2000"), InventoryMovementType.PURCHASE));

        List<InventoryMovement> movements = inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(
                flour.getId());

        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).getId()).isEqualTo(first.getId());
        assertThat(movements.get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    void sumQuantityByIngredientIdComputesRunningBalance() {
        Ingredient butter = createIngredient("Manteiga", IngredientUnit.GRAM);
        inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(butter, new BigDecimal("1000"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(butter, new BigDecimal("-150"), InventoryMovementType.ORDER_CONSUMPTION));
        inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(butter, new BigDecimal("-50"), InventoryMovementType.LOSS));

        BigDecimal balance = inventoryMovementRepository.sumQuantityByIngredientId(butter.getId());

        assertThat(balance).isEqualByComparingTo("800");
    }

    @Test
    void sumQuantityByIngredientIdReturnsZeroForIngredientWithNoMovements() {
        Ingredient cinnamon = createIngredient("Canela", IngredientUnit.GRAM);

        BigDecimal balance = inventoryMovementRepository.sumQuantityByIngredientId(cinnamon.getId());

        assertThat(balance).isEqualByComparingTo("0");
    }

    // FARELO-097: the core guarantee of idx_inventory_movement_order_consumption
    // — a second ORDER_CONSUMPTION row for the exact same (order_id,
    // ingredient_id) pair is rejected at the DB level, not just by
    // application-level logic.
    @Test
    void idempotencyPartialUniqueIndexRejectsDuplicateOrderConsumptionForSameOrderAndIngredient() {
        Ingredient egg = createIngredient("Ovo (FARELO-097 idx)", IngredientUnit.UNIT);
        UUID orderId = createOrder().getId();

        inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(egg, new BigDecimal("-3"), InventoryMovementType.ORDER_CONSUMPTION, orderId));

        assertThatThrownBy(() -> inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(egg, new BigDecimal("-3"), InventoryMovementType.ORDER_CONSUMPTION, orderId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The partial index only applies WHERE type = 'ORDER_CONSUMPTION' — a
    // PURCHASE row (order_id always null, per FARELO-094's design) must not
    // be affected at all: two PURCHASE rows for the same ingredient (both
    // with order_id = null) must both succeed, proving the manual-entry
    // flow (FARELO-094) is untouched by this constraint. This also
    // incidentally demonstrates the Postgres NULL-uniqueness behavior
    // documented in V23's migration comment — two NULL order_ids never
    // collide — though it's exercised here for PURCHASE specifically
    // rather than as a standalone NULL-behavior test, since that's the
    // real-world case that matters.
    @Test
    void idempotencyPartialUniqueIndexAllowsDuplicatePurchaseRowsForSameIngredientWithNullOrderId() {
        Ingredient coffee = createIngredient("Café em grão (FARELO-097 idx)", IngredientUnit.GRAM);

        InventoryMovement first = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(coffee, new BigDecimal("1000"), InventoryMovementType.PURCHASE));
        InventoryMovement second = inventoryMovementRepository.saveAndFlush(
                new InventoryMovement(coffee, new BigDecimal("500"), InventoryMovementType.PURCHASE));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(coffee.getId())).hasSize(2);
    }

    // Same ingredient, two DIFFERENT orders — the (order_id, ingredient_id)
    // pair differs even though ingredient_id repeats, so both rows must be
    // allowed. This is exactly the case that makes a plain
    // UNIQUE(ingredient_id) (without order_id in the key) the wrong shape —
    // the same ingredient legitimately gets consumed by many different
    // orders over time.
    @Test
    void idempotencyPartialUniqueIndexAllowsSameIngredientConsumedByTwoDifferentOrders() {
        Ingredient milk = createIngredient("Leite (FARELO-097 idx)", IngredientUnit.MILLILITER);
        UUID firstOrderId = createOrder().getId();
        UUID secondOrderId = createOrder().getId();

        InventoryMovement first = inventoryMovementRepository.saveAndFlush(new InventoryMovement(
                milk, new BigDecimal("-150"), InventoryMovementType.ORDER_CONSUMPTION, firstOrderId));
        InventoryMovement second = inventoryMovementRepository.saveAndFlush(new InventoryMovement(
                milk, new BigDecimal("-150"), InventoryMovementType.ORDER_CONSUMPTION, secondOrderId));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(inventoryMovementRepository.findByOrderId(firstOrderId)).hasSize(1);
        assertThat(inventoryMovementRepository.findByOrderId(secondOrderId)).hasSize(1);
    }

}
