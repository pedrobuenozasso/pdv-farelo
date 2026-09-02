package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Thrown when an operation references an {@link Ingredient} id that does not
 * exist (e.g. updating an ingredient by an unknown id). Same pattern as
 * {@code com.farelo.api.catalog.CategoryNotFoundException}/{@code
 * ProductNotFoundException}.
 */
public class IngredientNotFoundException extends RuntimeException {

    private final UUID ingredientId;

    public IngredientNotFoundException(UUID ingredientId) {
        super("Ingredient not found: " + ingredientId);
        this.ingredientId = ingredientId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

}
