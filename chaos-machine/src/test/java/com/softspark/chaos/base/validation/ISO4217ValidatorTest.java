package com.softspark.chaos.base.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ISO4217Validator}.
 */
@DisplayName("ISO4217Validator")
class ISO4217ValidatorTest {

  private ISO4217Validator validator;

  @BeforeEach
  void setUp() {
    validator = new ISO4217Validator();
  }

  @ParameterizedTest(name = "''{0}'' → valid")
  @ValueSource(strings = {"GHS", "USD", "EUR", "ghs", "usd", "eur"})
  @DisplayName("should accept known ISO 4217 currency codes (case-insensitive)")
  void shouldAcceptValidCurrencyCodes(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }

  @ParameterizedTest(name = "''{0}'' → invalid")
  @ValueSource(strings = {"NOTACURRENCY", "ZZZ", "123", "ABCD"})
  @DisplayName("should reject unknown currency codes")
  void shouldRejectInvalidCurrencyCodes(String code) {
    assertThat(validator.isValid(code, null)).isFalse();
  }

  @ParameterizedTest(name = "empty string → invalid")
  @ValueSource(strings = {""})
  @DisplayName("empty string should be invalid")
  void emptyStringShouldBeInvalid(String code) {
    assertThat(validator.isValid(code, null)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @DisplayName("null should be valid (delegate null handling to @NotBlank)")
  void nullShouldBeValid(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }
}
