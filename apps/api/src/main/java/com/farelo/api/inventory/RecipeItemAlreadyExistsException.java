package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Thrown when adding a {@link RecipeItem} for an {@link Ingredient} that
 * already has a line on the given {@link Recipe}. Two rows for the same
 * ingredient on the same recipe isn't a real use case (it's a data-entry
 * mistake — the caller should update the existing line's quantity instead),
 * so it's rejected outright rather than allowed to silently duplicate/split
 * the recipe's consumption of that ingredient. 409 Conflict, same
 * reasoning/shape as {@link RecipeAlreadyExistsException}: the request is
 * well-formed, but conflicts with existing state — enforced here at the
 * service layer *and* by a {@code UNIQUE(recipe_id, ingredient_id)}
 * constraint at the DB level as the real source of truth (see
 * {@code V19__create_recipe_item_table.sql}), for the same
 * two-concurrent-requests race reason documented on {@link Recipe}'s
 * javadoc for its own active-recipe-per-product rule.
 */
public class RecipeItemAlreadyExistsException extends RuntimeException {

    private final UUID recipeId;
    private final UUID ingredientId;

    public RecipeItemAlreadyExistsException(UUID recipeId, UUID ingredientId) {
        super("Recipe " + recipeId + " already has an item for ingredient " + ingredientId);
        this.recipeId = recipeId;
        this.ingredientId = ingredientId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

}
