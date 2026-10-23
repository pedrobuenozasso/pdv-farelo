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

    final class Validator implements ConstraintValidator<ValidGtin, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Gtin.isValid(value);
        }
    }
}
