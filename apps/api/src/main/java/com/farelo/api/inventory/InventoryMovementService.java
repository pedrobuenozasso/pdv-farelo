package com.farelo.api.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FARELO-093 gave this class its first (and until now, only) method,
 * {@link #listByIngredient(UUID)}. FARELO-094 ("Criar entrada manual de
 * estoque") added {@link #create(UUID, BigDecimal)}: the manual-entry
 * producer of {@link InventoryMovement} rows this domain was missing — see
 * {@link InventoryMovement}'s javadoc and {@link InventoryMovementType}'s
 * javadoc for why {@code PURCHASE} was always earmarked for that ticket.
 * FARELO-095 ("Calcular saldo do ingrediente") added {@link
 * #getBalance(UUID)}, reading the ledger this class already owns rather
 * than mutating it. FARELO-096 ("Consumir receita ao criar pedido") adds
 * {@link #consumeForOrder(UUID, List)}, the second real producer — see its
 * own javadoc. Loss (FARELO-098) and idempotency on order consumption
 * (FARELO-097) remain future work.
 */
@Service
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final IngredientService ingredientService;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;

    public InventoryMovementService(
            InventoryMovementRepository inventoryMovementRepository,
            IngredientService ingredientService,
            RecipeRepository recipeRepository,
            RecipeItemRepository recipeItemRepository) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.ingredientService = ingredientService;
        this.recipeRepository = recipeRepository;
        this.recipeItemRepository = recipeItemRepository;
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

    /**
     * FARELO-096 ("Consumir receita ao criar pedido", prompt mestre seção
     * 16): called by {@code com.farelo.api.ordering.OrderService#create}
     * right after an order and its items are persisted (so {@code orderId}
     * is available), in the same transaction. For each sold item, looks up
     * the product's active {@link Recipe} (if any — see below) and writes
     * one {@code ORDER_CONSUMPTION} {@link InventoryMovement} per {@link
     * RecipeItem} in it.
     *
     * <p><b>Quantity math</b>: {@link RecipeItem#getQuantity()} is "how much
     * of this ingredient for ONE unit of the product" (its own javadoc).
     * The movement written here is that quantity times how many units of
     * the product were sold ({@link OrderItemConsumption#quantity()}),
     * negated — stock going out, same sign convention documented on {@link
     * InventoryMovement#getQuantity()}. {@code BigDecimal} throughout
     * (AGENTS.md): {@code RecipeItem.quantity} (scale 3) is multiplied by
     * an exact integer count via {@link BigDecimal#valueOf(long)}, so the
     * product keeps the same scale — no rounding, no {@code double}
     * anywhere.
     *
     * <p><b>Products without a recipe are silently skipped, not an
     * error</b>: not every product has one yet (a recipe is optional per
     * {@link Recipe}'s own design — {@code Product} carries no required
     * back-reference to it). {@link RecipeRepository#findByProductIdAndActiveTrue}
     * returning empty for a given item is simply "nothing to consume for
     * this line", the same way {@code Recipe}/{@code RecipeItem}'s own
     * javadoc already anticipates for this exact ticket.
     *
     * <p><b>No stock-sufficiency check</b> (prompt mestre seção 16 doesn't
     * ask for one here — going negative is allowed for now, plausibly
     * FARELO-099 "estoque mínimo"'s concern later, not this one's) and
     * <b>no idempotency guard</b> against this method running twice for the
     * same order — deliberately deferred to FARELO-097, exactly as {@link
     * InventoryMovement}'s own javadoc ("orderId" section) anticipates: this
     * ticket only adds the producer, not the "don't double-process"
     * constraint the future ticket will need the {@code orderId} column
     * for.
     *
     * <p><b>{@code @Transactional}</b>: called from within {@code
     * OrderService#create}'s own {@code @Transactional} method, so this
     * annotation mainly documents the requirement (Spring's default
     * propagation, {@code REQUIRED}, joins the caller's existing
     * transaction rather than starting a new one) — same "one more thing
     * happens after order creation, same transaction" shape already
     * established by that method's {@code OutboxPublisher} call
     * (FARELO-060): if writing a movement fails, the whole order creation
     * rolls back rather than leaving an order with a missing/partial stock
     * deduction.
     */
    @Transactional
    public List<InventoryMovement> consumeForOrder(UUID orderId, List<OrderItemConsumption> items) {
        List<InventoryMovement> movements = new ArrayList<>();

        for (OrderItemConsumption item : items) {
            Optional<Recipe> recipe = recipeRepository.findByProductIdAndActiveTrue(item.productId());
            if (recipe.isEmpty()) {
                continue;
            }

            List<RecipeItem> recipeItems = recipeItemRepository.findByRecipeId(recipe.get().getId());
            for (RecipeItem recipeItem : recipeItems) {
                BigDecimal quantity = recipeItem.getQuantity()
                        .multiply(BigDecimal.valueOf(item.quantity()))
                        .negate();

                InventoryMovement movement = new InventoryMovement(
                        recipeItem.getIngredient(), quantity, InventoryMovementType.ORDER_CONSUMPTION, orderId);
                movements.add(inventoryMovementRepository.save(movement));
            }
        }

        return movements;
    }

}
