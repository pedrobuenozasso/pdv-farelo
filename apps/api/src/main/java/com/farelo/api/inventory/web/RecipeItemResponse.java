package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.RecipeItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link RecipeItem} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code ingredientName}/{@code ingredientUnit} are denormalized read
 * conveniences alongside {@code ingredientId}, same pattern as {@code
 * RecipeResponse#productName} alongside {@code productId}. {@code unit} is
 * intentionally omitted from {@code quantity}'s own representation — it's
 * always the ingredient's own unit (see {@link RecipeItem}'s javadoc), so
 * {@code ingredientUnit} already tells the caller how to read
 * {@code quantity} without repeating it on every field.
 */
public record RecipeItemResponse(
        UUID id,
        UUID recipeId,
        UUID ingredientId,
        String ingredientName,
        IngredientUnit ingredientUnit,
        BigDecimal quantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RecipeItemResponse from(RecipeItem item) {
        return new RecipeItemResponse(
                item.getId(),
                item.getRecipe().getId(),
                item.getIngredient().getId(),
                item.getIngredient().getName(),
                item.getIngredient().getUnit(),
                item.getQuantity(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

}
