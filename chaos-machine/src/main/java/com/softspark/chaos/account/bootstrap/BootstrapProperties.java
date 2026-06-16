package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the chart-of-accounts bootstrap process.
 *
 * <p>Loaded from {@code chaos-bootstrap.yml} (imported as optional classpath resource). Contains
 * the system-account catalog and flow-slot seed data used during startup provisioning.
 *
 * @param provisionOnStartup  when {@code true}, the runner calls the ledger HTTP API on startup
 * @param systemAccounts      ordered list of system accounts to provision
 * @param flowSlots           flow-slot-to-role mappings to seed in the local database
 */
@ConfigurationProperties(prefix = "chaos.bootstrap")
public record BootstrapProperties(
    boolean provisionOnStartup,
    List<SystemAccountDefinition> systemAccounts,
    List<FlowSlotConfigSeed> flowSlots) {

  /**
   * Seed data for a single flow-slot configuration.
   *
   * @param flowType    the transaction flow type
   * @param slotName    the slot name within the flow
   * @param accountRole the account role to assign to this slot
   */
  public record FlowSlotConfigSeed(FlowType flowType, String slotName, AccountRole accountRole) {}
}
