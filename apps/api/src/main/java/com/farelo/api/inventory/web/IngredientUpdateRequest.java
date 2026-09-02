package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/v1/ingredients/{id}}.
 *
 * <p>Deliberately a separate record from {@link IngredientRequest}, same
 * reasoning as {@code ProductUpdateRequest} vs {@code ProductRequest}:
 * {@code PUT} is a full replace and needs {@code active} to be settable, but
 * {@code active} doesn't belong on creation (it always starts {@code true}).
 * {@code active} is {@code Boolean} (wrapper, {@code @NotNull}) rather than a
 * primitive {@code boolean} to force the client to send it explicitly —
 * Jackson would otherwise silently default a missing primitive field on a
 * record to {@code false}, deactivating the ingredient whenever a client
 * omits it.
 */
public record IngredientUpdateRequest(
        @NotBlank String name,
        @NotNull IngredientUnit unit,
        @NotNull Boolean active) {
}
