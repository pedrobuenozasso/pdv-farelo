package com.farelo.api.fiscal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/fiscal-profiles}. Never expose the
 * JPA entity directly on the API (see AGENTS.md) — this is the boundary
 * DTO.
 *
 * <p>No {@code active} field here, same as {@code CategoryRequest}/{@code
 * IngredientRequest} — a new fiscal profile always starts {@code true} (see
 * {@code FiscalProfile}'s field default), so there's nothing for the client
 * to decide on creation.
 *
 * <p>{@code description} is optional (no {@code @NotBlank}/{@code @NotNull}
 * — a plain nullable {@code String}, same shape as {@code
 * ProductRequest.description}): a fiscal profile is meaningfully
 * identifiable by {@code name} alone (e.g. "Isento"), so a description is a
 * convenience, not a requirement.
 */
public record FiscalProfileRequest(
        @NotBlank String name,
        String description) {
}
