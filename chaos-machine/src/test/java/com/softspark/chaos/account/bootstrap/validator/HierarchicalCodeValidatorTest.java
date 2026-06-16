package com.softspark.chaos.account.bootstrap.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link HierarchicalCodeValidator}.
 */
@DisplayName("HierarchicalCodeValidator")
class HierarchicalCodeValidatorTest {

  private HierarchicalCodeValidator validator;

  @BeforeEach
  void setUp() {
    validator = new HierarchicalCodeValidator();
  }

  @Nested
  @DisplayName("valid codes")
  class ValidCodes {

    @ParameterizedTest(name = "\"{0}\" is valid")
    @ValueSource(
        strings = {
          "ASSET",
          "ASSET.PLATFORM.FLOAT",
          "ASSET.BANK.SETTLEMENT.0000000000001.GHS",
          "REVENUE.PLATFORM.FEE",
          "A",
          "A1",
          "A1.B2.C3"
        })
    @DisplayName("accepts valid hierarchical codes")
    void acceptsValidCodes(String code) {
      assertThat(validator.isValid(code, null)).isTrue();
    }

    @Test
    @DisplayName("null is valid (let @NotBlank handle null rejection)")
    void nullIsValid() {
      assertThat(validator.isValid(null, null)).isTrue();
    }
  }

  @Nested
  @DisplayName("invalid codes")
  class InvalidCodes {

    @ParameterizedTest(name = "\"{0}\" is invalid")
    @ValueSource(
        strings = {
          "asset.platform.float", // lowercase
          "Asset.Platform", // mixed case
          "ASSET..FLOAT", // double dot
          "ASSET.", // trailing dot
          ".ASSET", // leading dot
          "ASSET FLOAT", // space
          "ASSET-FLOAT", // hyphen
          "", // empty string
          ".", // lone dot
          "ASSET.FLO@T" // special character
        })
    @DisplayName("rejects invalid codes")
    void rejectsInvalidCodes(String code) {
      assertThat(validator.isValid(code, null)).isFalse();
    }
  }
}
