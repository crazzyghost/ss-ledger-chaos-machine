package com.softspark.chaos.account.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link ChartOfAccountsBootstrap}.
 *
 * <p>Uses the {@code test} profile which points to an in-memory SQLite database and disables Kafka,
 * so no external services are required.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ChartOfAccountsBootstrap")
class ChartOfAccountsBootstrapTest {

  @Autowired private AccountRoleRepository accountRoleRepository;
  @Autowired private VirtualAccountRepository virtualAccountRepository;
  @Autowired private FlowSlotConfigRepository flowSlotConfigRepository;

  @Test
  @DisplayName("fresh boot seeds exactly 6 account roles")
  void shouldSeedSixRoles() {
    assertThat(accountRoleRepository.count()).isEqualTo(6L);
  }

  @Test
  @DisplayName("fresh boot seeds 6 SYSTEM virtual accounts")
  void shouldSeedSixSystemVirtualAccounts() {
    long systemCount =
        virtualAccountRepository.findAll().stream()
            .filter(va -> va.getOwnershipType() == AccountOwnershipType.SYSTEM)
            .count();
    assertThat(systemCount).isEqualTo(6L);
  }

  @Test
  @DisplayName("fresh boot seeds default flow slot configurations")
  void shouldSeedFlowSlotConfigs() {
    assertThat(flowSlotConfigRepository.count()).isGreaterThan(0L);
  }

  @Test
  @DisplayName("re-running bootstrap is a no-op (row counts unchanged)")
  void bootstrapIsIdempotent(@Autowired ChartOfAccountsBootstrap bootstrap) throws Exception {
    long rolesBefore = accountRoleRepository.count();
    long vasBefore = virtualAccountRepository.count();
    long slotsBefore = flowSlotConfigRepository.count();

    bootstrap.run(null);

    assertThat(accountRoleRepository.count()).isEqualTo(rolesBefore);
    assertThat(virtualAccountRepository.count()).isEqualTo(vasBefore);
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
}
