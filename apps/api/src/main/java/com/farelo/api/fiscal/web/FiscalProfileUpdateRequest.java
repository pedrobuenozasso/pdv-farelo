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
 *
 * <p>{@code cfop} (FARELO-153) is optional too, same reasoning and same
 * {@code @Pattern} shape as {@code ncm} above — omitting it (or sending
 * {@code null}) clears a previously-set CFOP back to "not configured". Not
 * {@code @NotNull}, same reasoning as {@code ncm}: a fiscal profile with no
 * CFOP yet is a legitimate, common state (see {@code FiscalProfile.cfop}'s
 * javadoc).
 *
 * <p>{@code cst}/{@code csosn} (FARELO-154) are optional too, same
 * "PUT is a full replace" convention as {@code ncm}/{@code cfop} above —
 * omitting either (or sending {@code null}) clears a previously-set value
 * back to "not configured". Also mutually exclusive with each other
 * ({@link CstCsosnMutuallyExclusive}, applied at the record level below) —
 * see {@link FiscalProfileRequest}'s javadoc for the full reasoning on the
 * exclusivity rule and the two different {@code @Pattern}s ({@code cst}
 * looser, {@code csosn} exact).
 */
@CstCsosnMutuallyExclusive
public record FiscalProfileUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull Boolean active,
        @Pattern(regexp = "^[0-9]{8}$", message = "ncm must be exactly 8 numeric digits") String ncm,
        @Pattern(regexp = "^[0-9]{4}$", message = "cfop must be exactly 4 numeric digits") String cfop,
        @Pattern(regexp = "^[0-9]{2,3}$", message = "cst must be 2 to 3 numeric digits") String cst,
        @Pattern(regexp = "^[0-9]{3}$", message = "csosn must be exactly 3 numeric digits") String csosn)
        implements FiscalProfileCstCsosnCarrier {
}
