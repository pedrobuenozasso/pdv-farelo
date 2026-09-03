package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

}
