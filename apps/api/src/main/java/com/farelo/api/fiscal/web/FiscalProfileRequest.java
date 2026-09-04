package com.farelo.api.fiscal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
 *
 * <p>{@code ncm} (FARELO-152) is optional too, defaulting to {@code null}
 * ("no NCM configured") when omitted — same shape as {@code
 * IngredientRequest.minimumStock}: there's no single unambiguous default
 * value to silently apply (a missing NCM is a distinct, deliberate state,
 * not an error), so leaving it unset must genuinely mean "not configured
 * yet". {@code @Pattern(regexp = "^[0-9]{8}$")} without {@code @NotNull}:
 * Bean Validation only runs a constraint against a non-null value, so this
 * rejects a malformed NCM (wrong digit count, non-numeric) when sent while
 * still allowing the field to be entirely absent. A real NCM is always
 * exactly 8 numeric digits (standard Brazilian tax classification code,
 * not an app-specific format choice) — see {@code FiscalProfile.ncm}'s
 * javadoc.
 *
 * <p>{@code cfop} (FARELO-153) is optional too, same reasoning and shape as
 * {@code ncm} above: defaults to {@code null} ("no CFOP configured") when
 * omitted, {@code @Pattern(regexp = "^[0-9]{4}$")} without {@code @NotNull}.
 * A real CFOP is always exactly 4 numeric digits (standard Brazilian tax
 * classification code, not an app-specific format choice) — see {@code
 * FiscalProfile.cfop}'s javadoc.
 *
 * <p>{@code cst}/{@code csosn} (FARELO-154) are both optional, same
 * "defaults to {@code null}, no {@code @NotNull}" shape as {@code ncm}/
 * {@code cfop} — but, unlike those two, they're also mutually exclusive
 * with each other ({@link CstCsosnMutuallyExclusive}, applied at the record
 * level below): a fiscal profile carries a CST (Regime Normal) or a CSOSN
 * (Simples Nacional), never both. {@code cst}'s {@code @Pattern(regexp =
 * "^[0-9]{2,3}$")} is looser than {@code ncm}/{@code cfop}'s exact-digit
 * patterns — CST tables differ by tax type (ICMS/IPI/PIS/COFINS) and this
 * field doesn't track which one, so 2-to-3 numeric digits is the honest
 * structural common denominator rather than a made-up single exact length.
 * {@code csosn}'s {@code @Pattern(regexp = "^[0-9]{3}$")} is exact, same
 * confidence level as {@code ncm}/{@code cfop} — CSOSN is a single national
 * table (Simples Nacional) whose codes are always exactly 3 digits. See
 * {@code FiscalProfile.cst}/{@code .csosn}'s javadoc and
 * {@code docs/domain-model.md}'s FARELO-154 subsection for the full
 * reasoning.
 */
@CstCsosnMutuallyExclusive
public record FiscalProfileRequest(
        @NotBlank String name,
        String description,
        @Pattern(regexp = "^[0-9]{8}$", message = "ncm must be exactly 8 numeric digits") String ncm,
        @Pattern(regexp = "^[0-9]{4}$", message = "cfop must be exactly 4 numeric digits") String cfop,
        @Pattern(regexp = "^[0-9]{2,3}$", message = "cst must be 2 to 3 numeric digits") String cst,
        @Pattern(regexp = "^[0-9]{3}$", message = "csosn must be exactly 3 numeric digits") String csosn)
        implements FiscalProfileCstCsosnCarrier {
}
