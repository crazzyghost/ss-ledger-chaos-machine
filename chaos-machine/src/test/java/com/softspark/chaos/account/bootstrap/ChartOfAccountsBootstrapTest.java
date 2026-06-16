package com.softspark.chaos.account.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link ChartOfAccountsBootstrapRunner}.
 *
 * <p>Uses the {@code test} profile which points to an in-memory SQLite database, disables Kafka,
 * and sets {@code provision-on-startup=false} so no outbound HTTP calls are made.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ChartOfAccountsBootstrapRunner")
class ChartOfAccountsBootstrapTest {

  @Autowired private AccountRoleRepository accountRoleRepository;
  @Autowired private FlowSlotConfigRepository flowSlotConfigRepository;

  @Test
  @DisplayName("fresh boot seeds exactly 6 account roles")
  void shouldSeedSixRoles() {
    assertThat(accountRoleRepository.count()).isEqualTo(6L);
  }

  @Test
  @DisplayName("fresh boot seeds default flow slot configurations")
  void shouldSeedFlowSlotConfigs() {
    assertThat(flowSlotConfigRepository.count()).isGreaterThan(0L);
  }

  @Test
  @DisplayName("re-running bootstrap is a no-op (row counts unchanged)")
  void bootstrapIsIdempotent(@Autowired ChartOfAccountsBootstrapRunner runner) throws Exception {
    long rolesBefore = accountRoleRepository.count();
    long slotsBefore = flowSlotConfigRepository.count();

    runner.run(null);

    assertThat(accountRoleRepository.count()).isEqualTo(rolesBefore);
    assertThat(flowSlotConfigRepository.count()).isEqualTo(slotsBefore);
  }

  @Test
  @DisplayName("all six expected account codes are present")
  void shouldContainExpectedAccountCodes() {
    var codes = accountRoleRepository.findAll().stream().map(r -> r.getAccountCode()).toList();

    assertThat(codes)
        .containsExactlyInAnyOrder(
            "ASSET.BANK.SETTLEMENT.0000000000001.GHS",
            "ASSET.PLATFORM.FLOAT",
            "ASSET.PLATFORM.FLOAT.MTN",
            "ASSET.PLATFORM.FLOAT.TELECEL",
            "REVENUE.PLATFORM.FEE",
            "REVENUE.PROVIDER.FEE");
  }

  @Test
  @DisplayName("all six expected AccountRole enum values are present")
  void shouldContainExpectedRoleCodes() {
    var roles = accountRoleRepository.findAll().stream().map(r -> r.getRole()).toList();

    assertThat(roles)
        .containsExactlyInAnyOrder(
            AccountRole.SETTLEMENT_ACCOUNT,
            AccountRole.PLATFORM_FLOAT,
            AccountRole.PLATFORM_FLOAT_MTN,
            AccountRole.PLATFORM_FLOAT_TELECEL,
            AccountRole.PLATFORM_FEE,
            AccountRole.PROVIDER_FEE);
  }

  @Test
  @DisplayName("all roles start with PENDING provisioning status when provision-on-startup=false")
  void allRolesStartAsPending() {
    var statuses =
        accountRoleRepository.findAll().stream().map(r -> r.getProvisioningStatus()).toList();

    assertThat(statuses).hasSize(6).allMatch(s -> s == ProvisioningStatus.PENDING);
  }
}
