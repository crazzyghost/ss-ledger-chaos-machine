package com.softspark.chaos.base.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for enum values.
 */
public class IsInEnumValidator implements ConstraintValidator<IsInEnum, String> {

  private Set<String> validValues;

  @Override
  public void initialize(IsInEnum annotation) {
    validValues =
        Arrays.stream(annotation.enumClass().getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return validValues.contains(value.toUpperCase());
  }
}
