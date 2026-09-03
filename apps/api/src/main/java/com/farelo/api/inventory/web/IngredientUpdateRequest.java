package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

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
 *
 * <p>{@code minimumStock} (FARELO-099) stays optional here too, unlike
 * {@code active} — same reasoning as {@code
 * ProductUpdateRequest.productionStation}: it has no single unambiguous
 * default a missing field could safely stand in for, and a {@code null}
 * value is itself legitimate, deliberate input ("clear this ingredient's
 * threshold back to not-configured") that a full-replace {@code PUT} must be
 * able to express — forcing {@code @NotNull} here would make "unconfigure
 * the threshold" impossible to send. See {@code Ingredient.minimumStock}'s
 * javadoc for the full null-vs-zero reasoning.
 */
public record IngredientUpdateRequest(
        @NotBlank String name,
        @NotNull IngredientUnit unit,
        @NotNull Boolean active,
        @DecimalMin(value = "0.00", inclusive = true) BigDecimal minimumStock) {
}
