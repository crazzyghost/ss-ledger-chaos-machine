package com.softspark.chaos.account.bootstrap.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validates that a string value matches the hierarchical account-code pattern.
 *
 * <p>The pattern {@code ^[A-Z0-9]+(\.[A-Z0-9]+)*$} requires one or more dot-separated segments
 * where every segment is composed exclusively of uppercase ASCII letters and digits.
 *
 * <p>{@code null} is considered valid; pairing with {@code @NotBlank} enforces non-null presence.
 */
public class HierarchicalCodeValidator implements ConstraintValidator<HierarchicalCode, String> {

  private static final Pattern HIERARCHICAL_CODE_PATTERN =
      Pattern.compile("^[A-Z0-9]+(\\.[A-Z0-9]+)*$");

  /**
   * Returns {@code true} when {@code value} is {@code null} or matches the hierarchical code
   * pattern; {@code false} otherwise.
   *
   * @param value   the string to validate
   * @param context the constraint validator context
   * @return {@code true} if valid
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return HIERARCHICAL_CODE_PATTERN.matcher(value).matches();
  }
}
