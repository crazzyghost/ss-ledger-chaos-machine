package com.softspark.chaos.account.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.softspark.chaos.account.enumeration.AccountRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SystemAccountCatalogValidator}.
 */
@DisplayName("SystemAccountCatalogValidator")
class SystemAccountCatalogValidatorTest {

  private SystemAccountCatalogValidator validator;

  @BeforeEach
  void setUp() {
    validator = new SystemAccountCatalogValidator();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static SystemAccountDefinition def(
      AccountRole role, String code, AccountRole parentRole) {
    return new SystemAccountDefinition(
        role, code, role.name() + " Account", "ASSET", "GHS", "SYSTEM", parentRole, null, null);
  }

  private static SystemAccountDefinition def(AccountRole role, String code) {
    return def(role, code, null);
  }

  // -------------------------------------------------------------------------
  // Happy-path tests
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("valid catalog")
  class ValidCatalog {

    @Test
    @DisplayName("returns all definitions when catalog is valid")
    void validCatalogReturnsTopologicalOrder() {
      var defs =
          List.of(
              def(AccountRole.SETTLEMENT_ACCOUNT, "ASSET.BANK.SETTLEMENT.0000000000001.GHS"),
              def(AccountRole.PLATFORM_FLOAT, "ASSET.PLATFORM.FLOAT"),
              def(
                  AccountRole.PLATFORM_FLOAT_MTN,
                  "ASSET.PLATFORM.FLOAT.MTN",
                  AccountRole.PLATFORM_FLOAT),
              def(
                  AccountRole.PLATFORM_FLOAT_TELECEL,
                  "ASSET.PLATFORM.FLOAT.TELECEL",
                  AccountRole.PLATFORM_FLOAT),
              def(AccountRole.PLATFORM_FEE, "REVENUE.PLATFORM.FEE"),
              def(AccountRole.PROVIDER_FEE, "REVENUE.PROVIDER.FEE"));

      var result = validator.validateAndOrder(defs);

      assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("PLATFORM_FLOAT appears before its children in the ordered result")
    void parentsBeforeChildrenInOrder() {
      var defs =
          List.of(
              def(
                  AccountRole.PLATFORM_FLOAT_MTN,
                  "ASSET.PLATFORM.FLOAT.MTN",
                  AccountRole.PLATFORM_FLOAT),
              def(AccountRole.PLATFORM_FLOAT, "ASSET.PLATFORM.FLOAT"),
              def(
                  AccountRole.PLATFORM_FLOAT_TELECEL,
                  "ASSET.PLATFORM.FLOAT.TELECEL",
                  AccountRole.PLATFORM_FLOAT));

      var result = validator.validateAndOrder(defs);

      int floatIndex = indexOfRole(result, AccountRole.PLATFORM_FLOAT);
      int mtnIndex = indexOfRole(result, AccountRole.PLATFORM_FLOAT_MTN);
      int telecelIndex = indexOfRole(result, AccountRole.PLATFORM_FLOAT_TELECEL);

      assertThat(floatIndex).isLessThan(mtnIndex);
      assertThat(floatIndex).isLessThan(telecelIndex);
    }

    @Test
    @DisplayName("empty catalog returns empty list without error")
    void emptyCatalogReturnsEmptyList() {
      var result = validator.validateAndOrder(List.of());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null catalog returns empty list without error")
    void nullCatalogReturnsEmptyList() {
      var result = validator.validateAndOrder(null);
      assertThat(result).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Failure-path tests
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("invalid catalog")
  class InvalidCatalog {

    @Test
    @DisplayName("duplicate accountCode throws IllegalStateException naming the code")
    void duplicateAccountCodeFails() {
      var defs =
          List.of(
              def(AccountRole.PLATFORM_FLOAT, "ASSET.PLATFORM.FLOAT"),
              def(AccountRole.SETTLEMENT_ACCOUNT, "ASSET.PLATFORM.FLOAT")); // same code

      assertThatIllegalStateException()
          .isThrownBy(() -> validator.validateAndOrder(defs))
          .withMessageContaining("ASSET.PLATFORM.FLOAT");
    }

    @Test
    @DisplayName("duplicate role throws IllegalStateException naming the role")
    void duplicateRoleFails() {
      var defs =
          List.of(
              def(AccountRole.PLATFORM_FLOAT, "ASSET.PLATFORM.FLOAT"),
              def(AccountRole.PLATFORM_FLOAT, "ASSET.PLATFORM.FLOAT.COPY")); // same role

      assertThatIllegalStateException()
          .isThrownBy(() -> validator.validateAndOrder(defs))
          .withMessageContaining("PLATFORM_FLOAT");
    }

    @Test
    @DisplayName("parentRole that does not exist in catalog throws IllegalStateException")
    void missingParentRoleFails() {
      // PLATFORM_FLOAT_MTN references PLATFORM_FLOAT which is not in the catalog
      var defs =
          List.of(
              def(
                  AccountRole.PLATFORM_FLOAT_MTN,
                  "ASSET.PLATFORM.FLOAT.MTN",
                  AccountRole.PLATFORM_FLOAT));

      assertThatIllegalStateException()
          .isThrownBy(() -> validator.validateAndOrder(defs))
          .withMessageContaining("PLATFORM_FLOAT");
    }

    @Test
    @DisplayName("cyclic dependency throws IllegalStateException describing the cycle")
    void cyclicDependencyFails() {
      // PLATFORM_FLOAT → parent=PLATFORM_FLOAT_MTN, PLATFORM_FLOAT_MTN → parent=PLATFORM_FLOAT
      var defs =
          List.of(
              def(
                  AccountRole.PLATFORM_FLOAT,
                  "ASSET.PLATFORM.FLOAT",
                  AccountRole.PLATFORM_FLOAT_MTN),
              def(
                  AccountRole.PLATFORM_FLOAT_MTN,
                  "ASSET.PLATFORM.FLOAT.MTN",
                  AccountRole.PLATFORM_FLOAT));

      assertThatIllegalStateException()
          .isThrownBy(() -> validator.validateAndOrder(defs))
          .withMessageContaining("Cyclic");
    }
  }

  // -------------------------------------------------------------------------
  // Utility
  // -------------------------------------------------------------------------

  private static int indexOfRole(List<SystemAccountDefinition> defs, AccountRole role) {
    for (int i = 0; i < defs.size(); i++) {
      if (defs.get(i).role() == role) {
        return i;
      }
    }
    return -1;
  }
}
