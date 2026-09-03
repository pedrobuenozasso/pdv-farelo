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
 * this service can create. FARELO-095 ("Calcular saldo do ingrediente")
 * adds {@link #getBalance(UUID)}, reading the ledger this class already
 * owns rather than mutating it.
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

    /**
     * FARELO-095 ("Calcular saldo do ingrediente"): an ingredient's current
     * stock balance — the sum of every {@link InventoryMovement} row
     * recorded for it (prompt mestre seção 13: "O saldo deve ser
     * rastreável (derivado do ledger, nunca editado diretamente)"). Reuses
     * {@link InventoryMovementRepository#sumQuantityByIngredientId} exactly
     * as FARELO-093 laid it down for this purpose — the database already
     * computes {@code COALESCE(SUM(quantity), 0)}, so re-summing in Java
     * would be a slower, riskier duplicate of the same logic (and the
     * project convention, per AGENTS.md, is {@code BigDecimal} end to end
     * for exactly this kind of quantity, never a primitive re-derivation).
     *
     * <p>Same "ingredient exists first" validation and ordering as {@link
     * #listByIngredient(UUID)} (404 {@link IngredientNotFoundException}
     * before anything else): a balance of {@code 0} because the ingredient
     * genuinely has no movements yet must stay distinguishable from an
     * ingredient id that doesn't exist at all — same reasoning {@link
     * #listByIngredient(UUID)} already documents for an empty movement
     * list.
     */
    public IngredientBalance getBalance(UUID ingredientId) {
        Ingredient ingredient = ingredientService.getById(ingredientId);
        BigDecimal balance = inventoryMovementRepository.sumQuantityByIngredientId(ingredientId);
        return new IngredientBalance(ingredient, balance);
    }

}
