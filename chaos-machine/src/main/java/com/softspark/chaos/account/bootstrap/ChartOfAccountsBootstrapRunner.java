package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Application runner that seeds and provisions the chart of accounts on startup.
 *
 * <p>Phase 025 bootstrapping proceeds in four stages:
 *
 * <ol>
 *   <li><b>Validate</b> — the YAML catalog is validated and sorted into topological order (parents
 *       before children) by {@link SystemAccountCatalogValidator}.
 *   <li><b>Seed</b> — for every role in the catalog, an {@code account_role} row is created with
 *       {@link ProvisioningStatus#PENDING} if one does not already exist.
 *   <li><b>Provision</b> (when {@code chaos.bootstrap.provision-on-startup=true}) — each
 *       PENDING/FAILED role is provisioned via {@link LedgerAccountProvisioningClient}; on success
 *       the row is updated to {@link ProvisioningStatus#PROVISIONED} and a
 *       {@link VirtualAccount} is recorded locally.
 *   <li><b>Flow slots</b> — {@code flow_slot_config} rows are upserted from the YAML
 *       {@code flow-slots} list.
 * </ol>
 *
 * <p>Roles that cannot be provisioned at startup are retried by {@link BootstrapReconciler}.
 *
 * <p>This runner supersedes the Phase 002 {@link ChartOfAccountsBootstrap} which has been
 * disabled.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(BootstrapProperties.class)
public class ChartOfAccountsBootstrapRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsBootstrapRunner.class);

  private final BootstrapProperties props;
  private final SystemAccountCatalogValidator catalogValidator;
  private final LedgerAccountProvisioningClient ledgerClient;
  private final AccountRoleRepository accountRoleRepository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final FlowSlotConfigRepository flowSlotConfigRepository;

  /**
   * Constructs the runner with all required collaborators.
   *
   * @param props                    bootstrap configuration properties
   * @param catalogValidator         validates and orders the system-account catalog
   * @param ledgerClient             HTTP client for account provisioning
   * @param accountRoleRepository    persistence for account roles
   * @param virtualAccountRepository persistence for virtual accounts
   * @param flowSlotConfigRepository persistence for flow-slot configurations
   */
  public ChartOfAccountsBootstrapRunner(
      BootstrapProperties props,
      SystemAccountCatalogValidator catalogValidator,
      LedgerAccountProvisioningClient ledgerClient,
      AccountRoleRepository accountRoleRepository,
      VirtualAccountRepository virtualAccountRepository,
      FlowSlotConfigRepository flowSlotConfigRepository) {
    this.props = props;
    this.catalogValidator = catalogValidator;
    this.ledgerClient = ledgerClient;
    this.accountRoleRepository = accountRoleRepository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.flowSlotConfigRepository = flowSlotConfigRepository;
  }

  /**
   * Runs the full bootstrap sequence on application startup.
   *
   * @param args application arguments (unused)
   */
  @Override
  public void run(ApplicationArguments args) {
    long startTime = System.currentTimeMillis();
    log.info("Chart of accounts bootstrap started");

    var orderedDefs = catalogValidator.validateAndOrder(props.systemAccounts());

    orderedDefs.forEach(this::seedRoleIfAbsent);

    if (props.provisionOnStartup()) {
      runProvisioning(orderedDefs);
    } else {
      log.info("provision-on-startup=false — skipping HTTP provisioning (roles seeded as PENDING)");
    }

    bootstrapFlowSlots(props.flowSlots());

    log.info(
        "Chart of accounts bootstrap completed in {}ms", System.currentTimeMillis() - startTime);
  }

  /**
   * Manually triggers provisioning for all non-{@link ProvisioningStatus#PROVISIONED} roles.
   *
   * <p>Intended for use by the reconciler and the manual bootstrap HTTP endpoint. Safe to call
   * concurrently — each role is independently persisted; already-provisioned roles are skipped.
   *
   * @return a {@link BootstrapResult} summarising the current provisioning state
   */
  public BootstrapResult triggerManualBootstrap() {
    var orderedDefs = catalogValidator.validateAndOrder(props.systemAccounts());
    var errors = runProvisioning(orderedDefs);

    long provisioned =
        accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.PROVISIONED);
    long pending = accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.PENDING);
    long failed = accountRoleRepository.countByProvisioningStatus(ProvisioningStatus.FAILED);

    return new BootstrapResult((int) provisioned, (int) pending, (int) failed, errors);
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  /**
   * Provisions all non-PROVISIONED roles in topological order, tracking parent VA IDs for child
   * requests. Returns the list of per-role error messages encountered.
   */
  private List<String> runProvisioning(List<SystemAccountDefinition> orderedDefs) {
    Map<com.softspark.chaos.account.enumeration.AccountRole, String> roleToVaId = new HashMap<>();
    List<String> errors = new ArrayList<>();

    // Pre-populate map with already-provisioned roles so children can find their parent IDs.
    for (var def : orderedDefs) {
      accountRoleRepository
          .findById(def.role())
          .filter(e -> e.getProvisioningStatus() == ProvisioningStatus.PROVISIONED)
          .ifPresent(e -> roleToVaId.put(def.role(), e.getDefaultVaId()));
    }

    for (var def : orderedDefs) {
      if (roleToVaId.containsKey(def.role())) {
        continue; // already provisioned
      }

      String parentAccountId = def.parentRole() != null ? roleToVaId.get(def.parentRole()) : null;

      try {
        String accountId = ledgerClient.createAccount(def, parentAccountId);
        persistProvisioned(def, accountId);
        roleToVaId.put(def.role(), accountId);
        log.info("Provisioned role {} → accountId {}", def.role(), accountId);
      } catch (LedgerProvisioningException e) {
        persistFailure(def, e.getMessage());
        String error = def.role() + ": " + e.getMessage();
        errors.add(error);
        log.warn("Failed to provision role {}: {}", def.role(), e.getMessage());
      }
    }

    return errors;
  }

  private void seedRoleIfAbsent(SystemAccountDefinition def) {
    if (accountRoleRepository.existsById(def.role())) {
      log.debug("Account role already seeded: {}", def.role());
      return;
    }

    var entity = new AccountRoleEntity();
    entity.setRole(def.role());
    entity.setAccountCode(def.accountCode());
    entity.setCategory(AccountCategory.valueOf(def.accountCategory()));
    entity.setCurrency(def.currency());
    entity.setDefaultVaId(null);
    entity.setProvisioningStatus(ProvisioningStatus.PENDING);

    accountRoleRepository.save(entity);
    log.debug("Seeded PENDING account_role row for role: {}", def.role());
  }

  private void persistProvisioned(SystemAccountDefinition def, String accountId) {
    var entity = accountRoleRepository.findById(def.role()).orElseGet(AccountRoleEntity::new);

    entity.setRole(def.role());
    entity.setAccountCode(def.accountCode());
    entity.setCategory(AccountCategory.valueOf(def.accountCategory()));
    entity.setCurrency(def.currency());
    entity.setDefaultVaId(accountId);
    entity.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
    entity.setProvisionedAt(Instant.now().toString());
    entity.setLastError(null);

    accountRoleRepository.save(entity);

    if (!virtualAccountRepository.existsById(accountId)) {
      var va = new VirtualAccount();
      va.setVaId(accountId);
      va.setName(def.accountName());
      va.setOwnershipType(AccountOwnershipType.valueOf(def.ownershipType()));
      va.setOrganizationId(null);
      va.setCurrency(def.currency());
      va.setStatus(AccountStatus.ACTIVE);
      va.setChannel(null);
      va.setAccountRole(def.role());
      va.setCreatedVia(CreatedVia.LEDGER_PROVISIONED);
      virtualAccountRepository.save(va);
      log.debug("Recorded SYSTEM virtual account {} for role {}", accountId, def.role());
    }
  }

  private void persistFailure(SystemAccountDefinition def, String errorMessage) {
    accountRoleRepository
        .findById(def.role())
        .ifPresent(
            entity -> {
              entity.setLastError(truncate(errorMessage, 500));
              accountRoleRepository.save(entity);
            });
  }

  private void bootstrapFlowSlots(List<BootstrapProperties.FlowSlotConfigSeed> flowSlots) {
    if (flowSlots == null) {
      return;
    }

    int created = 0;
    int updated = 0;

    for (var slot : flowSlots) {
      var existing =
          flowSlotConfigRepository.findByFlowTypeAndSlotName(slot.flowType(), slot.slotName());

      if (existing.isPresent()) {
        var config = existing.get();
        config.setAccountRole(slot.accountRole());
        flowSlotConfigRepository.save(config);
        updated++;
      } else {
        var config = new FlowSlotConfig();
        config.setId(Ids.generate());
        config.setFlowType(slot.flowType());
        config.setSlotName(slot.slotName());
        config.setAccountRole(slot.accountRole());
        config.setExplicitVaId(null);
        flowSlotConfigRepository.save(config);
        created++;
      }
    }

    log.info("Flow slot bootstrap: created={}, updated={}", created, updated);
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
