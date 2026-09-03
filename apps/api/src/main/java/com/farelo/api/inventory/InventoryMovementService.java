package com.farelo.api.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * FARELO-093 gave this class its first (and until now, only) method,
 * {@link #listByIngredient(UUID)}. FARELO-094 ("Criar entrada manual de
 * estoque") adds the first {@code create}: the manual-entry producer of
 * {@link InventoryMovement} rows this domain was missing — see {@link
 * InventoryMovement}'s javadoc and {@link InventoryMovementType}'s javadoc
 * for why {@code PURCHASE} was always earmarked for this ticket. Order
 * consumption (FARELO-096/097) and loss (FARELO-098) remain future
 * producers; until they land, {@code PURCHASE} is the only movement type
 * this service can create.
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

    /**
     * Records a manual stock entry: a human (e.g. a manager) confirming that
     * stock physically arrived. Always produces a {@code PURCHASE} row with
     * a positive {@code quantity} — {@code type} is never a parameter here,
     * deliberately (see {@code InventoryMovementRequest}'s javadoc for why
     * this endpoint doesn't let a client pick the movement type). Validates
     * the ingredient exists first (404 {@link IngredientNotFoundException}),
     * same reasoning/order as {@link #listByIngredient(UUID)} and {@link
     * RecipeItemService#create}. {@code quantity > 0} is enforced by
     * {@code @Positive} on the request DTO before this method ever runs, not
     * re-checked here — same division of labor as {@link
     * RecipeItemService#create} trusting its own {@code @Positive} request
     * field.
     *
     * <p>{@code @Transactional} even though this is a single {@code save}:
     * matches every other mutating method in this domain ({@link
     * RecipeItemService#create}, {@link IngredientService#update}) rather
     * than relying on Spring Data's own per-method transaction, so a future
     * addition here (e.g. touching a running balance) doesn't silently need
     * a second annotation added.
     */
    @Transactional
    public InventoryMovement create(UUID ingredientId, BigDecimal quantity) {
        Ingredient ingredient = ingredientService.getById(ingredientId);
        return inventoryMovementRepository.save(
                new InventoryMovement(ingredient, quantity, InventoryMovementType.PURCHASE));
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
