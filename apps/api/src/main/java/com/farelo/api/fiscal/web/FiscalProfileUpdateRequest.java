package com.farelo.api.fiscal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PUT /api/v1/fiscal-profiles/{id}}.
 *
 * <p>Deliberately a separate record from {@link FiscalProfileRequest}, same
 * reasoning as {@code IngredientUpdateRequest} vs {@code IngredientRequest}:
 * {@code PUT} is a full replace and needs {@code active} to be settable, but
 * {@code active} doesn't belong on creation (it always starts {@code true}).
 * {@code active} is {@code Boolean} (wrapper, {@code @NotNull}) rather than a
 * primitive {@code boolean} to force the client to send it explicitly —
 * Jackson would otherwise silently default a missing primitive field on a
 * record to {@code false}, deactivating the profile whenever a client omits
 * it.
 *
 * <p>{@code description} stays optional here too, same as {@link
 * FiscalProfileRequest} — a full-replace {@code PUT} that omits it (or sends
 * {@code null}) clears a previously-set description back to "none", the same
 * "PUT is a full replace" behavior already used elsewhere in this codebase
 * (e.g. {@code Product.productionStation} via {@code
 * ProductUpdateRequest}).
 *
 * <p>{@code ncm} (FARELO-152) is optional too, same reasoning and same
 * {@code @Pattern} as {@link FiscalProfileRequest#ncm()} — and, following
 * the same "PUT is a full replace" convention as {@code description} above
 * (and {@code IngredientUpdateRequest.minimumStock}), omitting it (or
 * sending {@code null}) clears a previously-set NCM back to "not
 * configured". Not {@code @NotNull}: unlike {@code active}, a fiscal
 * profile with no NCM yet is a legitimate, common state (see {@code
 * FiscalProfile.ncm}'s javadoc), so forcing every {@code PUT} to always
 * re-send an NCM would make "clear it" impossible to express.
 */
public record FiscalProfileUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull Boolean active,
        @Pattern(regexp = "^[0-9]{8}$", message = "ncm must be exactly 8 numeric digits") String ncm) {
}
