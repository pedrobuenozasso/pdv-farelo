package com.farelo.api.inventory.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/recipes}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code active} field, same reasoning as {@code IngredientRequest}:
 * a new recipe always starts {@code true} (see {@link
 * com.farelo.api.inventory.Recipe}'s field default) — nothing for the
 * client to decide on creation.
 */
public record RecipeRequest(@NotNull UUID productId) {
}
