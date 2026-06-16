package com.softspark.chaos.base.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.softspark.chaos.account.enumeration.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link IsInEnumValidator} initialized with {@link AccountStatus}.
 */
@DisplayName("IsInEnumValidator (AccountStatus)")
class IsInEnumValidatorTest {

  private IsInEnumValidator validator;

  @BeforeEach
  void setUp() {
    IsInEnum annotation = mock(IsInEnum.class);
    when(annotation.enumClass()).thenAnswer(inv -> AccountStatus.class);
    validator = new IsInEnumValidator();
    validator.initialize(annotation);
  }

  @ParameterizedTest(name = "''{0}'' → valid")
  @ValueSource(strings = {"ACTIVE", "SUSPENDED", "FROZEN", "DORMANT", "CLOSED"})
  @DisplayName("should accept valid AccountStatus values")
  void shouldAcceptValidValues(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }

  @ParameterizedTest(name = "''{0}'' (lowercase) → valid (case-insensitive)")
  @ValueSource(strings = {"active", "suspended", "frozen", "dormant", "closed"})
  @DisplayName("should accept lowercase variants via toUpperCase normalisation")
  void shouldAcceptLowercaseVariants(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }

  @ParameterizedTest(name = "''{0}'' → invalid")
  @ValueSource(strings = {"BOGUS", "UNKNOWN", "DELETED", "PENDING"})
  @DisplayName("should reject values not in the enum")
  void shouldRejectInvalidValues(String value) {
    assertThat(validator.isValid(value, null)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @DisplayName("null should be valid (delegate null handling to @NotNull)")
  void nullShouldBeValid(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }
}
