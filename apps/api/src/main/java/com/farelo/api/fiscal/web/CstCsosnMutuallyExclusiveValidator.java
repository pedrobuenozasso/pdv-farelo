package com.farelo.api.fiscal.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link CstCsosnMutuallyExclusive} — see that annotation's
 * javadoc for the full FARELO-154 reasoning. Works against
 * {@link FiscalProfileCstCsosnCarrier} rather than a concrete DTO type so
 * both {@link FiscalProfileRequest} and {@link FiscalProfileUpdateRequest}
 * can reuse the same annotation/validator.
 */
public class CstCsosnMutuallyExclusiveValidator
        implements ConstraintValidator<CstCsosnMutuallyExclusive, FiscalProfileCstCsosnCarrier> {

    @Override
    public boolean isValid(FiscalProfileCstCsosnCarrier value, ConstraintValidatorContext context) {
        // Bean Validation only invokes isValid for a non-null annotated
        // value, but null-guard defensively anyway (same caution as every
        // other single-field @Pattern in this codebase, which also only
        // fires against non-null values).
        return value == null || value.cst() == null || value.csosn() == null;
    }

}
