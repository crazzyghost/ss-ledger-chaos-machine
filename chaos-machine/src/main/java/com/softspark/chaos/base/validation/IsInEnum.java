package com.softspark.chaos.base.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string value is a valid enum constant name.
 */
@Documented
@Constraint(validatedBy = IsInEnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsInEnum {
    Class<? extends Enum<?>> enumClass();
    String message() default "Value is not valid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
