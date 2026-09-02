package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link RecipeItem} id that does not
 * exist (or does not belong to the given recipe — see
 * {@link RecipeItemService#delete(UUID, UUID)}). Same pattern as
 * {@link RecipeNotFoundException}/{@link IngredientNotFoundException}.
 */
public class RecipeItemNotFoundException extends RuntimeException {

    private final UUID recipeItemId;

    public RecipeItemNotFoundException(UUID recipeItemId) {
        super("Recipe item not found: " + recipeItemId);
        this.recipeItemId = recipeItemId;
    }

    public UUID getRecipeItemId() {
        return recipeItemId;
    }

}
