package com.farelo.api.inventory.web;

import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientUnit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Ingredient} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record IngredientResponse(
        UUID id,
        String name,
        IngredientUnit unit,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static IngredientResponse from(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.isActive(),
                ingredient.getCreatedAt(),
                ingredient.getUpdatedAt());
    }

}
