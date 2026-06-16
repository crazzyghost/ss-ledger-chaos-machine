package com.softspark.chaos.base.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * Validates that a {@link BigDecimal} value is {@code >= 0}.
 *
 * <p>{@code null} values pass through as valid; use {@code @NotNull} on the field to reject them.
 */
public class PositiveOrZeroAmountValidator
    implements ConstraintValidator<PositiveOrZeroAmount, BigDecimal> {

  @Override
  public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.compareTo(BigDecimal.ZERO) >= 0;
  }
}
