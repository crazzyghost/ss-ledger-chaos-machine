package com.softspark.chaos.account.bootstrap.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid hierarchical account code.
 *
 * <p>A hierarchical code is a non-empty sequence of dot-separated segments where each segment
 * contains only uppercase ASCII letters and digits. Examples:
 *
 * <ul>
 *   <li>{@code ASSET}
 *   <li>{@code ASSET.PLATFORM.FLOAT}
 *   <li>{@code ASSET.BANK.SETTLEMENT.0000000000001.GHS}
 * </ul>
 *
 * <p>{@code null} values pass validation; combine with {@code @NotBlank} to reject nulls.
 */
@Documented
@Constraint(validatedBy = HierarchicalCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HierarchicalCode {

  /** Default violation message. */
  String message() default "Invalid hierarchical account code";

  /** Constraint groups. */
  Class<?>[] groups() default {};

  /** Constraint payload. */
  Class<? extends Payload>[] payload() default {};
}
