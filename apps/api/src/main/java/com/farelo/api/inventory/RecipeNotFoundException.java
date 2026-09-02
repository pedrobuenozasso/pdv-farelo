package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link Recipe} id that does not
 * exist. Same pattern as {@link IngredientNotFoundException}.
 */
public class RecipeNotFoundException extends RuntimeException {

    private final UUID recipeId;

    public RecipeNotFoundException(UUID recipeId) {
        super("Recipe not found: " + recipeId);
        this.recipeId = recipeId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

}
