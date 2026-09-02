package com.farelo.api.inventory.web;

import com.farelo.api.inventory.Recipe;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Recipe} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code productName} is a denormalized read convenience alongside
 * {@code productId}, same pattern as {@code
 * com.farelo.api.ordering.web.OrderItemResponse}. No recipe items
 * (ingredients/quantities) yet — that's {@code RecipeItem}, FARELO-092.
 */
public record RecipeResponse(
        UUID id,
        UUID productId,
        String productName,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getProduct().getId(),
                recipe.getProduct().getName(),
                recipe.isActive(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt());
    }

}
