package com.farelo.api.inventory.web;

import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Ingredient} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code minimumStock} (FARELO-099) is {@code null} when the ingredient
 * has no threshold configured — see {@link Ingredient#getMinimumStock()}'s
 * javadoc. Whether a specific balance is currently below that threshold is
 * not this response's concern: that's {@code
 * IngredientBalanceResponse.belowMinimum}, computed against a live
 * ledger-derived balance ({@code GET
 * /api/v1/ingredients/{ingredientId}/balance}, FARELO-095/099) — this
 * response only echoes the configured threshold value itself, the same way
 * it echoes every other plain field of {@code Ingredient}.
 */
public record IngredientResponse(
        UUID id,
        String name,
        IngredientUnit unit,
        boolean active,
        BigDecimal minimumStock,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static IngredientResponse from(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.isActive(),
                ingredient.getMinimumStock(),
                ingredient.getCreatedAt(),
                ingredient.getUpdatedAt());
    }

}
