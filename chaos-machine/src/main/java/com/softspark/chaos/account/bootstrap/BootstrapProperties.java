package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for bootstrap data.
 * <p>
 * Loads account roles, default virtual accounts, and flow slot configurations from
 * chaos-bootstrap.yml to seed the database on application startup.
 */
@ConfigurationProperties(prefix = "chaos.bootstrap")
public record BootstrapProperties(
    List<AccountRoleConfig> accountRoles, List<FlowSlotConfigSeed> flowSlots) {

  /**
   * Configuration for a single account role.
   *
   * @param role         the account role enum value
   * @param accountCode  the account code
   * @param category     the account category
   * @param currency     the currency code (ISO-4217)
   * @param channel      optional channel
   * @param defaultVaId  the stable default virtual account ID
   */
  public record AccountRoleConfig(
      AccountRole role,
      String accountCode,
      AccountCategory category,
      String currency,
      Channel channel,
      String defaultVaId) {}

  /**
   * Configuration for a flow slot mapping.
   *
   * @param flowType    the transaction flow type
   * @param slotName    the slot name within the flow
   * @param accountRole the account role to assign to this slot
   */
  public record FlowSlotConfigSeed(FlowType flowType, String slotName, AccountRole accountRole) {}
}
