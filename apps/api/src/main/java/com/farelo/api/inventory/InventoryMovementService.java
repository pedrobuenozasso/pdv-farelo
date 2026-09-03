package com.farelo.api.inventory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * FARELO-093 scope: only reading the ledger back. No {@code create} method
 * exists here — nothing in this ticket produces an {@link InventoryMovement}
 * yet (see its javadoc); a manual-entry producer is FARELO-094, order
 * consumption is FARELO-096/097, loss is FARELO-098. Until one of those
 * lands, rows only ever get into this table via
 * {@link InventoryMovementRepository#save} called directly by tests.
 */
@Service
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final IngredientService ingredientService;

    public InventoryMovementService(
            InventoryMovementRepository inventoryMovementRepository, IngredientService ingredientService) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.ingredientService = ingredientService;
    }

    // Validates the ingredient exists first (404 INGREDIENT_NOT_FOUND) so
    // that listing movements for an unknown ingredient id is distinguishable
    // from a real ingredient that simply has no movements yet (both would
    // otherwise return an identical empty list) — same reasoning as
    // RecipeItemService#listByRecipe.
    public List<InventoryMovement> listByIngredient(UUID ingredientId) {
        ingredientService.getById(ingredientId);
        return inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(ingredientId);
    }

}
