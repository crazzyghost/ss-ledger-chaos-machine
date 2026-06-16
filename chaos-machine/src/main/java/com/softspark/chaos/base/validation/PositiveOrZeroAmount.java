package com.softspark.chaos.base.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@link java.math.BigDecimal} value is greater than or equal to zero.
 *
 * <p>A {@code null} value is considered valid; pair with {@code @NotNull} to reject nulls.
 */
@Documented
@Constraint(validatedBy = PositiveOrZeroAmountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveOrZeroAmount {

  /** Default violation message. */
  String message() default "Amount must be positive or zero";

  /** Constraint groups. */
  Class<?>[] groups() default {};

  /** Constraint payload. */
  Class<? extends Payload>[] payload() default {};
}
