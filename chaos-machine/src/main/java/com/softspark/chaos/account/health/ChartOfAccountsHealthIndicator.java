package com.softspark.chaos.account.health;

import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator {@link HealthIndicator} that reflects the chart-of-accounts provisioning state.
 *
 * <p>Reports {@code UP} when all system account roles have been provisioned by the ledger. Reports
 * {@code DEGRADED} when any role is still {@link ProvisioningStatus#PENDING} or
 * {@link ProvisioningStatus#FAILED}, which may temporarily occur during startup or after a ledger
 * outage.
 */
@Component
public class ChartOfAccountsHealthIndicator implements HealthIndicator {

  private final AccountRoleRepository accountRoleRepository;

  /**
   * Constructs the health indicator.
   *
   * @param accountRoleRepository repository used to count roles by provisioning status
   */
  public ChartOfAccountsHealthIndicator(AccountRoleRepository accountRoleRepository) {
    this.accountRoleRepository = accountRoleRepository;
  }

  /**
   * Computes the health by counting roles in each {@link ProvisioningStatus}.
   *
   * @return {@link Health#up()} when all roles are provisioned; {@code DEGRADED} otherwise
   */
  @Override
  public Health health() {
    long provisioned =
        accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.PROVISIONED);
    long pending = accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.PENDING);
    long failed = accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.FAILED);

    if (pending == 0 && failed == 0) {
      return Health.up().withDetail("provisioned", provisioned).build();
    }

    return Health.status("DEGRADED")
        .withDetail("provisioned", provisioned)
        .withDetail("pending", pending)
        .withDetail("failed", failed)
        .build();
  }
}
