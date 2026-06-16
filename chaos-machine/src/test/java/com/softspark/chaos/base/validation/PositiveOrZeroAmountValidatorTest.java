package com.softspark.chaos.base.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

/**
 * Unit tests for {@link PositiveOrZeroAmountValidator}.
 */
@DisplayName("PositiveOrZeroAmountValidator")
class PositiveOrZeroAmountValidatorTest {

  private PositiveOrZeroAmountValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PositiveOrZeroAmountValidator();
  }

  @ParameterizedTest(name = "{0} → valid")
  @CsvSource({"0", "0.00", "1.50", "100", "0.01"})
  @DisplayName("should accept values >= 0")
  void shouldAcceptPositiveOrZeroValues(String input) {
    assertThat(validator.isValid(new BigDecimal(input), null)).isTrue();
  }

  @ParameterizedTest(name = "{0} → invalid")
  @CsvSource({"-0.01", "-1", "-100.00"})
  @DisplayName("should reject negative values")
  void shouldRejectNegativeValues(String input) {
    assertThat(validator.isValid(new BigDecimal(input), null)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @DisplayName("null should be valid (delegate null handling to @NotNull)")
  void nullShouldBeValid(BigDecimal value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }
}
