package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @deprecated Superseded by {@link ChartOfAccountsBootstrapRunner} (Phase 025).
 *     <p>This class implemented the Phase 002 config-UUID approach where account IDs were
 *     hard-coded in {@code chaos-bootstrap.yml}. Phase 025 replaces that with HTTP-based
 *     provisioning against the ledger service. The {@code @Component} annotation has been removed
 *     so Spring no longer auto-wires this bean; it is retained only to satisfy compilation until a
 *     future cleanup phase removes it entirely.
 */
@Deprecated
@EnableConfigurationProperties(BootstrapProperties.class)
public class ChartOfAccountsBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsBootstrap.class);

  private final BootstrapProperties bootstrapProperties;
  private final AccountRoleRepository accountRoleRepository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final FlowSlotConfigRepository flowSlotConfigRepository;

  public ChartOfAccountsBootstrap(
      BootstrapProperties bootstrapProperties,
      AccountRoleRepository accountRoleRepository,
      VirtualAccountRepository virtualAccountRepository,
      FlowSlotConfigRepository flowSlotConfigRepository) {
    this.bootstrapProperties = bootstrapProperties;
    this.accountRoleRepository = accountRoleRepository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.flowSlotConfigRepository = flowSlotConfigRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    long startTime = System.currentTimeMillis();
    log.info("[DEPRECATED] Starting Phase 002 chart of accounts bootstrap");

    if (bootstrapProperties.systemAccounts() == null
        || bootstrapProperties.systemAccounts().isEmpty()) {
      log.warn("No system accounts configured; skipping deprecated bootstrap");
      return;
    }

    int rolesCreated = 0;
    int rolesUpdated = 0;

    for (var def : bootstrapProperties.systemAccounts()) {
      var existingRole = accountRoleRepository.findById(def.role());

      if (existingRole.isPresent()) {
        var role = existingRole.get();
        role.setAccountCode(def.accountCode());
        role.setCategory(AccountCategory.valueOf(def.accountCategory()));
        role.setCurrency(def.currency());
        accountRoleRepository.save(role);
        rolesUpdated++;
      } else {
        var role = new AccountRoleEntity();
        role.setRole(def.role());
        role.setAccountCode(def.accountCode());
        role.setCategory(AccountCategory.valueOf(def.accountCategory()));
        role.setCurrency(def.currency());
        accountRoleRepository.save(role);
        rolesCreated++;
      }
    }

    if (bootstrapProperties.flowSlots() != null) {
      for (var slotConfig : bootstrapProperties.flowSlots()) {
        var existingConfig =
            flowSlotConfigRepository.findByFlowTypeAndSlotName(
                slotConfig.flowType(), slotConfig.slotName());

        if (existingConfig.isPresent()) {
          var config = existingConfig.get();
          config.setAccountRole(slotConfig.accountRole());
          flowSlotConfigRepository.save(config);
        } else {
          var config = new FlowSlotConfig();
          config.setId(Ids.generate());
          config.setFlowType(slotConfig.flowType());
          config.setSlotName(slotConfig.slotName());
          config.setAccountRole(slotConfig.accountRole());
          config.setExplicitVaId(null);
          flowSlotConfigRepository.save(config);
        }
      }
    }

    log.info(
        "[DEPRECATED] Bootstrap completed in {}ms: roles created={}, roles updated={}",
        System.currentTimeMillis() - startTime,
        rolesCreated,
        rolesUpdated);
  }
}
