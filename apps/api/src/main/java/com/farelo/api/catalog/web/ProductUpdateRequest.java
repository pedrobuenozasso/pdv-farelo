package com.farelo.api.catalog.web;

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
 */
public record ProductUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal price,
        @NotNull UUID categoryId,
        String imageUrl,
        @NotNull Boolean active,
        @NotNull Boolean availableOnMenu,
        @NotNull Boolean availableOnPos) {
}
