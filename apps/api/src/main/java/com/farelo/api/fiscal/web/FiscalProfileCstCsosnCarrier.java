package com.farelo.api.fiscal.web;

/**
 * Implemented by both {@link FiscalProfileRequest} and
 * {@link FiscalProfileUpdateRequest} (FARELO-154) so a single
 * {@link CstCsosnMutuallyExclusive} annotation/validator works for both
 * request DTOs without duplicating the cross-field check — records
 * automatically satisfy this interface via their generated {@code cst()}/
 * {@code csosn()} accessor methods, so implementing it costs each record
 * only an {@code implements} clause, no extra code.
 */
interface FiscalProfileCstCsosnCarrier {

    String cst();

    String csosn();

}
