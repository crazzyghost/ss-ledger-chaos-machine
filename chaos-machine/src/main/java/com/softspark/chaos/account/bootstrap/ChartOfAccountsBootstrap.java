package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.model.VirtualAccount;
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
import org.springframework.stereotype.Component;

/**
 * Bootstrap component that seeds the chart of accounts on application startup.
 * <p>
 * Loads account roles, default virtual accounts, and flow slot configurations from
 * chaos-bootstrap.yml and persists them to the database. This operation is idempotent
 * and uses upsert logic to avoid duplication on restarts.
 */
@Component
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
    log.info("Starting chart of accounts bootstrap");

    if (bootstrapProperties.accountRoles() == null
        || bootstrapProperties.accountRoles().isEmpty()) {
      log.warn("No account roles configured in chaos-bootstrap.yml; skipping bootstrap");
      return;
    }

    int rolesCreated = 0;
    int rolesUpdated = 0;
    int vasCreated = 0;
    int flowSlotsCreated = 0;
    int flowSlotsUpdated = 0;

    // Bootstrap account roles and their default virtual accounts
    for (var roleConfig : bootstrapProperties.accountRoles()) {
      var existingRole = accountRoleRepository.findById(roleConfig.role());

      if (existingRole.isPresent()) {
        // Update existing role
        var role = existingRole.get();
        role.setAccountCode(roleConfig.accountCode());
        role.setCategory(roleConfig.category());
        role.setCurrency(roleConfig.currency());
        role.setChannel(roleConfig.channel());
        role.setDefaultVaId(roleConfig.defaultVaId());
        accountRoleRepository.save(role);
        rolesUpdated++;
        log.debug("Updated account role: {}", roleConfig.role());
      } else {
        // Create new role
        var role = new AccountRoleEntity();
        role.setRole(roleConfig.role());
        role.setAccountCode(roleConfig.accountCode());
        role.setCategory(roleConfig.category());
        role.setCurrency(roleConfig.currency());
        role.setChannel(roleConfig.channel());
        role.setDefaultVaId(roleConfig.defaultVaId());
        accountRoleRepository.save(role);
        rolesCreated++;
        log.debug("Created account role: {}", roleConfig.role());
      }

      // Create or update the default virtual account for this role
      if (roleConfig.defaultVaId() != null && !roleConfig.defaultVaId().isBlank()) {
        var existingVa = virtualAccountRepository.findById(roleConfig.defaultVaId());

        if (existingVa.isEmpty()) {
          var va = new VirtualAccount();
          va.setVaId(roleConfig.defaultVaId());
          va.setName(roleConfig.role().name() + " SYSTEM Account");
          va.setOwnershipType(AccountOwnershipType.SYSTEM);
          va.setOrganizationId(null);
          va.setCurrency(roleConfig.currency());
          va.setStatus(AccountStatus.ACTIVE);
          va.setChannel(roleConfig.channel());
          va.setAccountRole(roleConfig.role());
          va.setCreatedVia(CreatedVia.BOOTSTRAP);
          virtualAccountRepository.save(va);
          vasCreated++;
          log.debug(
              "Created SYSTEM virtual account {} for role {}",
              roleConfig.defaultVaId(),
              roleConfig.role());
        }
      }
    }

    // Bootstrap flow slot configurations
    if (bootstrapProperties.flowSlots() != null) {
      for (var slotConfig : bootstrapProperties.flowSlots()) {
        var existingConfig =
            flowSlotConfigRepository.findByFlowTypeAndSlotName(
                slotConfig.flowType(), slotConfig.slotName());

        if (existingConfig.isPresent()) {
          // Update existing configuration
          var config = existingConfig.get();
          config.setAccountRole(slotConfig.accountRole());
          flowSlotConfigRepository.save(config);
          flowSlotsUpdated++;
          log.debug(
              "Updated flow slot config: {}.{} -> {}",
              slotConfig.flowType(),
              slotConfig.slotName(),
              slotConfig.accountRole());
        } else {
          // Create new configuration
          var config = new FlowSlotConfig();
          config.setId(Ids.generate());
          config.setFlowType(slotConfig.flowType());
          config.setSlotName(slotConfig.slotName());
          config.setAccountRole(slotConfig.accountRole());
          config.setExplicitVaId(null);
          flowSlotConfigRepository.save(config);
          flowSlotsCreated++;
          log.debug(
              "Created flow slot config: {}.{} -> {}",
              slotConfig.flowType(),
              slotConfig.slotName(),
              slotConfig.accountRole());
        }
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    log.info(
        "Chart of accounts bootstrap completed in {}ms: "
            + "roles created={}, roles updated={}, VAs created={}, "
            + "flow slots created={}, flow slots updated={}",
        duration,
        rolesCreated,
        rolesUpdated,
        vasCreated,
        flowSlotsCreated,
        flowSlotsUpdated);
  }
}
