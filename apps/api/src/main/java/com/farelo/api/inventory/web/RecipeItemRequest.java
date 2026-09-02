package com.farelo.api.inventory.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/recipes/{recipeId}/items}. Never
 * expose the JPA entity directly on the API (see AGENTS.md) — this is the
 * boundary DTO.
 *
 * <p>No {@code recipeId} field here — it comes from the URL path, not the
 * body (the endpoint is already scoped to one recipe). {@code quantity}
 * must be strictly positive ({@code @Positive}, same validation-layer
 * pattern as {@code OrderItemRequest#quantity}) — zero or negative
 * consumption per unit sold isn't a meaningful recipe line.
 */
public record RecipeItemRequest(
        @NotNull UUID ingredientId,
        @NotNull @Positive BigDecimal quantity) {
}
