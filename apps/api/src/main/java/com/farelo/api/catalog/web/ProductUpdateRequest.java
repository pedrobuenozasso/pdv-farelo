package com.farelo.api.catalog.web;

import com.farelo.api.catalog.ProductionStation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code PUT /api/v1/products/{id}}.
 *
 * <p>Deliberately a separate record from {@link ProductRequest} rather than
 * reusing it: {@code PUT} is a full replace and needs {@code active} to be
 * settable, but {@code active} doesn't belong on creation (it always starts
 * {@code true}, see {@link com.farelo.api.catalog.Category}/{@link
 * com.farelo.api.catalog.Product}). Adding it to the shared record would
 * either force every create request to specify it (unnecessary noise), or
 * leave it optional as a primitive {@code boolean} — which is unsafe, since
 * Jackson defaults a missing primitive field on a record to {@code false},
 * silently creating inactive products whenever a client omits it. A
 * dedicated {@code active} of type {@code Boolean} (wrapper, {@code
 * @NotNull}) here avoids both problems.
 *
 * <p>{@code productionStation} (FARELO-073) stays optional here too, unlike
 * {@code active}/{@code availableOnMenu}/{@code availableOnPos} — those are
 * {@code @NotNull} precisely because they have one unambiguous correct
 * default ({@code true}) that an omitted field must not silently apply
 * instead. {@code productionStation} has no such default: {@code null} is
 * itself a legitimate, intentional value ("no station assigned"), and a
 * full-replace {@code PUT} must be able to send it to explicitly clear a
 * previously-assigned station — forcing {@code @NotNull} here would make
 * "unassign the station" impossible to express.
 *
 * <p>{@code fiscalProfileId} (FARELO-151) stays optional here too, same
 * "no safe default, {@code null} clears a previously-assigned value"
 * reasoning as {@code productionStation} — see
 * {@link com.farelo.api.catalog.Product#getFiscalProfile()}'s javadoc.
 */
public record ProductUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal price,
        @NotNull UUID categoryId,
        String imageUrl,
        @NotNull Boolean active,
        @NotNull Boolean availableOnMenu,
        @NotNull Boolean availableOnPos,
        ProductionStation productionStation,
        UUID fiscalProfileId) {
}
