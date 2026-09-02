package com.farelo.api.catalog.web;

import com.farelo.api.catalog.ProductionStation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/products}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>{@code availableOnMenu}/{@code availableOnPos} (FARELO-017) are
 * optional, defaulting to {@code true} when absent — same default as
 * {@link com.farelo.api.catalog.Product}'s {@code active}. Deliberately
 * {@code Boolean} (wrapper), not a primitive {@code boolean}: Jackson leaves
 * a missing wrapper field as {@code null} on a record, so the service layer
 * can distinguish "not sent" from "sent as false" and apply the {@code true}
 * default explicitly. A primitive here would have the same silent-false
 * problem documented on {@link ProductUpdateRequest}'s {@code active}.
 *
 * <p>{@code productionStation} (FARELO-073) is optional with no default at
 * all — unlike the two booleans above, {@code null} ("not yet assigned") is
 * itself the correct value for a product with no obvious station, not just
 * an absent-field placeholder (see {@link com.farelo.api.catalog.Product}'s
 * javadoc on the field). No wrapper-vs-primitive gotcha applies here: an
 * enum is already a reference type, so a missing JSON field simply
 * deserializes to {@code null} with no silent-default risk.
 */
public record ProductRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal price,
        @NotNull UUID categoryId,
        String imageUrl,
        Boolean availableOnMenu,
        Boolean availableOnPos,
        ProductionStation productionStation) {
}
