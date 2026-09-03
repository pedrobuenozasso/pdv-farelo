package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/ingredients}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code active} field here, same as {@code CategoryRequest}/{@code
 * ProductRequest} — a new ingredient always starts {@code true} (see
 * {@code Ingredient}'s field default), so there's nothing for the client to
 * decide on creation.
 *
 * <p>{@code minimumStock} (FARELO-099) is optional, defaulting to {@code
 * null} ("no threshold configured") when omitted — same shape as {@code
 * ProductRequest.productionStation}: unlike {@code active}/{@code
 * availableOnMenu}, there's no single unambiguous default value to silently
 * apply here (a missing threshold isn't the same as "threshold zero" — see
 * {@code Ingredient.minimumStock}'s javadoc), so leaving it unset must
 * genuinely mean "not configured", not "configured as zero". {@code
 * @DecimalMin} without {@code @NotNull}: Bean Validation only runs a
 * constraint against a non-null value, so this rejects a negative threshold
 * when sent while still allowing the field to be entirely absent.
 */
public record IngredientRequest(
        @NotBlank String name,
        @NotNull IngredientUnit unit,
        @DecimalMin(value = "0.00", inclusive = true) BigDecimal minimumStock) {
}
