package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/ingredients}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code active} field here, same as {@code CategoryRequest}/{@code
 * ProductRequest} — a new ingredient always starts {@code true} (see
 * {@code Ingredient}'s field default), so there's nothing for the client to
 * decide on creation.
 */
public record IngredientRequest(
        @NotBlank String name,
        @NotNull IngredientUnit unit) {
}
