package com.farelo.api.catalog.web;

import com.farelo.api.fiscal.Gtin;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidGtin.Validator.class)
public @interface ValidGtin {
    String message() default "must be SEM GTIN or a valid GTIN-8/12/13/14";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean allowEmpty() default false;

    final class Validator implements ConstraintValidator<ValidGtin, String> {
        private boolean allowEmpty;

        @Override
        public void initialize(ValidGtin annotation) {
            allowEmpty = annotation.allowEmpty();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (allowEmpty && value.isEmpty()) || Gtin.isValid(value);
        }
    }
}
