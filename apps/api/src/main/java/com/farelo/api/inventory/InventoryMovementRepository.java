package com.farelo.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    // Backs GET /api/v1/ingredients/{ingredientId}/movements
    // (InventoryMovementService#listByIngredient) — oldest first, same
    // ordering direction as RecipeItemRepository#findByRecipeId. A plain
    // Spring Data derived query (ingredientId resolves through the
    // ingredient association's id, standard Spring Data JPA "property path
    // through a relation" support). No JOIN FETCH ingredient needed: unlike
    // RecipeItemResponse (which reads ingredient.getName()/getUnit()),
    // InventoryMovementResponse only reads ingredient.getId() — already
    // present on the lazy proxy without initializing it, so no
    // LazyInitializationException risk even with open-in-view=false.
    List<InventoryMovement> findByIngredientIdOrderByCreatedAtAsc(UUID ingredientId);

    // Not used by any endpoint yet — no endpoint in this ticket exposes a
    // stock balance (that's FARELO-095's job, "Calcular saldo do
    // ingrediente"). Added now, per this ticket's own scope, as the
    // query infrastructure a future balance feature will reuse rather than
    // reimplement: an ingredient's balance is defined as the sum of all its
    // ledger rows (see InventoryMovement's javadoc), so this is that sum,
    // nothing more. COALESCE(..., 0) so an ingredient with zero movements
    // reports a balance of 0 instead of null.
    @Query("SELECT COALESCE(SUM(im.quantity), 0) FROM InventoryMovement im WHERE im.ingredient.id = :ingredientId")
    BigDecimal sumQuantityByIngredientId(@Param("ingredientId") UUID ingredientId);

    // FARELO-096 ("Consumir receita ao criar pedido"): the query a caller
    // needs to see every movement a single order produced (e.g. every
    // ORDER_CONSUMPTION row {@link InventoryMovementService#consumeForOrder}
    // wrote for it), across every ingredient it touched — a plain derived
    // query on the (indexed, see V21__create_inventory_movement_table.sql)
    // order_id column. Not ordered by createdAt: unlike
    // findByIngredientIdOrderByCreatedAtAsc, no consumer needs a specific
    // order here yet (tests just assert on the set of rows, filtering by
    // ingredient id themselves).
    List<InventoryMovement> findByOrderId(UUID orderId);

}
