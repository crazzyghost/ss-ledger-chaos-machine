package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically retries provisioning for account roles that are not yet
 * {@link ProvisioningStatus#PROVISIONED}.
 *
 * <p>The reconciler fires after a configurable initial delay ({@code
 * chaos.bootstrap.reconcile-initial-delay-ms}, default 30 s) and then on a fixed delay between
 * invocations ({@code chaos.bootstrap.reconcile-delay-ms}, default 60 s). On each invocation it
 * delegates to {@link ChartOfAccountsBootstrapRunner#triggerManualBootstrap()} which skips already
 * provisioned roles.
 *
 * <p>When {@code chaos.bootstrap.provision-on-startup} is {@code false} the reconciler exits
 * immediately without making any HTTP calls, preserving test isolation.
 */
@Component
public class BootstrapReconciler {

  private static final Logger log = LoggerFactory.getLogger(BootstrapReconciler.class);

  private final BootstrapProperties bootstrapProperties;
  private final ChartOfAccountsBootstrapRunner runner;

  /**
   * Constructs the reconciler.
   *
   * @param bootstrapProperties bootstrap configuration (used to check the provision-on-startup flag)
   * @param runner              the runner that holds the shared provisioning logic
   */
  public BootstrapReconciler(
      BootstrapProperties bootstrapProperties, ChartOfAccountsBootstrapRunner runner) {
    this.bootstrapProperties = bootstrapProperties;
    this.runner = runner;
  }

  /**
   * Reconciliation tick: re-attempts provisioning for all non-PROVISIONED roles.
   *
   * <p>This method is a no-op when {@code provision-on-startup=false} so that test contexts
   * never make outbound HTTP calls to the ledger.
   */
  @Scheduled(
      fixedDelayString = "${chaos.bootstrap.reconcile-delay-ms:60000}",
      initialDelayString = "${chaos.bootstrap.reconcile-initial-delay-ms:30000}")
  public void reconcile() {
    if (!bootstrapProperties.provisionOnStartup()) {
      log.debug("Reconciler skipped — provision-on-startup is false");
      return;
    }

    log.debug("Bootstrap reconciler tick started");
    var result = runner.triggerManualBootstrap();

    if (result.pending() == 0 && result.failed() == 0) {
      log.debug("All {} roles provisioned — reconciler idle", result.provisioned());
    } else {
      log.info(
          "Bootstrap reconciler: provisioned={}, pending={}, failed={}",
          result.provisioned(),
          result.pending(),
          result.failed());
    }
  }
}
