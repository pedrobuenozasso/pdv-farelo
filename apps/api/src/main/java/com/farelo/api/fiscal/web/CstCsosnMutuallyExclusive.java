package com.farelo.api.fiscal.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level Bean Validation constraint for FARELO-154's "cst and csosn are
 * mutually exclusive" rule — see {@code FiscalProfile}'s javadoc for the
 * full reasoning: a fiscal profile is configured for one tax regime, so it
 * carries a CST (Regime Normal) or a CSOSN (Simples Nacional), never both.
 * Applied at the record level, not on a single field, because this is a
 * cross-field check — the same reason Bean Validation requires class-level
 * constraints for "these two fields can't both be set" rules; a plain
 * {@code @Pattern}/{@code @NotNull} on one field alone can't see the other
 * field's value.
 *
 * <p>No custom Bean Validation constraint existed anywhere else in this
 * codebase before this ticket — every other {@code @Pattern}/{@code
 * @NotBlank}/{@code @NotNull} constraint so far (including {@code ncm}/
 * {@code cfop} right on these same two DTOs) is single-field. This one
 * earns the extra machinery specifically because it's the only place this
 * codebase needs a cross-field rule enforced at the request DTO boundary
 * with the exact same {@code 400}/{@code VALIDATION_ERROR} shape every
 * other Bean Validation failure on this endpoint already gets for free via
 * {@code ApiExceptionHandler#handleValidationException}. The alternative
 * (a hand-written service-layer check throwing a dedicated exception) would
 * need its own {@code ApiExceptionHandler} entry and would produce a
 * different error code for what is, in substance, the same kind of problem
 * ("this request body is malformed") as every other validation failure
 * already covered here.
 *
 * <p>Only rules out <i>both</i> being set — {@code null}/{@code null} (not
 * configured yet) and either one alone are all valid, same as the DB
 * {@code CHECK} backstop ({@code ck_fiscal_profile_cst_csosn_exclusive},
 * see {@code V33__add_fiscal_profile_cst_csosn_columns.sql}) this
 * constraint mirrors — same "validation at the DTO boundary + DB CHECK as
 * defense-in-depth backstop" pattern already established by {@code ncm}/
 * {@code cfop}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CstCsosnMutuallyExclusiveValidator.class)
public @interface CstCsosnMutuallyExclusive {

    String message() default "cst and csosn cannot both be set on the same fiscal profile";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
