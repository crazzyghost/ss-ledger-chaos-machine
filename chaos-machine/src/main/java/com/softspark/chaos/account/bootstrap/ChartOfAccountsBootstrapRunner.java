package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
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
 * <p>Phase 009 inverts account ownership: <b>the ledger owns virtual accounts</b>. The bootstrap no
 * longer persists VAs — it only <em>requests</em> their creation over HTTP and lets the {@code
 * ledger.account.created} consumer materialize the {@code virtual_account} rows. Bootstrapping
 * proceeds in four stages:
 *
 * <ol>
 *   <li><b>Validate</b> — the YAML catalog is validated and sorted into topological order (parents
 *       before children) by {@link SystemAccountCatalogValidator}.
 *   <li><b>Seed</b> — for every role in the catalog, an {@code account_role} row is created with
 *       {@link ProvisioningStatus#PENDING} if one does not already exist.
 *   <li><b>Request</b> (when {@code chaos.bootstrap.provision-on-startup=true}) — for each role
 *       whose {@code account_code} is not already present in {@code virtual_account}, a {@code POST
 *       /api/v0/accounts} request is issued to the ledger via
 *       {@link LedgerAccountProvisioningClient}. The role row is left {@link
 *       ProvisioningStatus#PENDING}; it flips to {@link ProvisioningStatus#PROVISIONED} only when
 *       the consumer projects the resulting event. No {@code virtual_account} row is written here.
 *   <li><b>Flow slots</b> — {@code flow_slot_config} rows are upserted from the YAML
 *       {@code flow-slots} list.
 * </ol>
 *
 * <p>The stage never blocks waiting for a VA to materialize, so startup completes promptly even if
 * the event has not yet arrived. Roles still {@code PENDING}/{@code FAILED} are retried by
 * {@link BootstrapReconciler}.
 *
 * <p>This runner supersedes the Phase 002 {@link ChartOfAccountsBootstrap} (disabled) and the
 * synchronous-persist behavior of Phase 007.
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
      runRequests(orderedDefs);
    } else {
      log.info("provision-on-startup=false — skipping HTTP requests (roles seeded as PENDING)");
    }

    bootstrapFlowSlots(props.flowSlots());

    log.info(
        "Chart of accounts bootstrap completed in {}ms", System.currentTimeMillis() - startTime);
  }

  /**
   * Manually triggers provisioning for all non-{@link ProvisioningStatus#PROVISIONED} roles.
   *
   * <p>Intended for use by the reconciler and the manual bootstrap HTTP endpoint. Safe to call
   * concurrently — each role is independently persisted; already-provisioned roles are skipped. When
   * invoked on a request thread (the UI-driven endpoint) the ledger calls are authorized with the
   * caller's access token via {@link com.softspark.chaos.auth.AuthenticationContext}; on the
   * reconciler/startup threads they fall back to the configured service token.
   *
   * @return a {@link BootstrapResult} summarising the current provisioning state
   */
  public BootstrapResult triggerManualBootstrap() {
    var orderedDefs = catalogValidator.validateAndOrder(props.systemAccounts());
    orderedDefs.forEach(this::seedRoleIfAbsent);
    var errors = runRequests(orderedDefs);

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
   * Requests creation of every role whose {@code account_code} is not already a VA, in topological
   * order. HTTP-only and non-blocking: no {@code virtual_account} row is written here — the role is
   * left {@link ProvisioningStatus#PENDING} until the consumer projects the ledger event. Returns
   * the list of per-role error messages encountered.
   */
  private List<String> runRequests(List<SystemAccountDefinition> orderedDefs) {
    Map<AccountRole, String> roleToAccountId = new HashMap<>();
    List<String> errors = new ArrayList<>();

    for (var def : orderedDefs) {
      // Already materialized? The consumer owns VA creation; just ensure the role is linked.
      var existingVa = virtualAccountRepository.findByAccountCode(def.accountCode()).orElse(null);
      if (existingVa != null) {
        roleToAccountId.put(def.role(), existingVa.getVaId());
        linkRoleToExistingVa(def, existingVa.getVaId());
        log.debug(
            "Code {} already a VA — skipping request for role {}", def.accountCode(), def.role());
        continue;
      }

      // Resolve the parent's ledger account id; defer the child if it cannot be resolved yet.
      String parentAccountId = null;
      if (def.parentRole() != null) {
        parentAccountId = resolveParentAccountId(def, roleToAccountId);
        if (parentAccountId == null) {
          log.info(
              "Deferring role {} — parent {} not yet resolvable; reconcile will retry",
              def.role(),
              def.parentRole());
          continue;
        }
      }

      try {
        String accountId = ledgerClient.createAccount(def, parentAccountId);
        upsertRolePending(def);
        roleToAccountId.put(def.role(), accountId);
        log.info(
            "Requested ledger account for role {} (accountId {}); role left PENDING until"
                + " projected",
            def.role(),
            accountId);
      } catch (RuntimeException e) {
        // Never let a single role's failure abort startup — record it and move on; the reconciler
        // retries. Ledger errors (403/5xx/transport) and any unexpected error are handled here.
        persistFailure(def, e.getMessage());
        String error = def.role() + ": " + e.getMessage();
        errors.add(error);
        log.warn("Failed to request account for role {}: {}", def.role(), e.getMessage());
      }
    }

    return errors;
  }

  /**
   * Resolves a child's parent ledger account id from (in order): account ids requested earlier in
   * this run, the VA projection by parent code, then a ledger code lookup. Returns {@code null} when
   * the parent has not been created yet.
   */
  private String resolveParentAccountId(
      SystemAccountDefinition def, Map<AccountRole, String> roleToAccountId) {
    String fromRun = roleToAccountId.get(def.parentRole());
    if (fromRun != null) {
      return fromRun;
    }

    String parentCode =
        accountRoleRepository
            .findById(def.parentRole())
            .map(AccountRoleEntity::getAccountCode)
            .orElse(null);
    if (parentCode == null) {
      return null;
    }

    var parentVa = virtualAccountRepository.findByAccountCode(parentCode).orElse(null);
    if (parentVa != null) {
      return parentVa.getVaId();
    }

    // Last resort: ask the ledger. A failure here must not crash the bootstrap — defer the child.
    try {
      return ledgerClient.findAccountByCode(parentCode).orElse(null);
    } catch (LedgerProvisioningException e) {
      log.warn(
          "Parent code lookup for {} failed ({}); deferring child {}",
          parentCode,
          e.getMessage(),
          def.role());
      return null;
    }
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

  /**
   * Upserts the role row as {@link ProvisioningStatus#PENDING} after a successful HTTP request,
   * clearing any prior error. The status is intentionally <em>not</em> set to PROVISIONED — that
   * transition is owned by the consumer when it projects the {@code ledger.account.created} event.
   * An already-PROVISIONED role is left untouched.
   */
  private void upsertRolePending(SystemAccountDefinition def) {
    var entity = accountRoleRepository.findById(def.role()).orElseGet(AccountRoleEntity::new);

    if (entity.getProvisioningStatus() == ProvisioningStatus.PROVISIONED) {
      return;
    }

    entity.setRole(def.role());
    entity.setAccountCode(def.accountCode());
    entity.setCategory(AccountCategory.valueOf(def.accountCategory()));
    entity.setCurrency(def.currency());
    entity.setProvisioningStatus(ProvisioningStatus.PENDING);
    entity.setLastError(null);

    accountRoleRepository.save(entity);
  }

  /**
   * Links a role to a VA that already exists in the projection but whose role was not yet flipped to
   * PROVISIONED (the event-before-seed race). No-op when the role is already PROVISIONED.
   */
  private void linkRoleToExistingVa(SystemAccountDefinition def, String accountId) {
    accountRoleRepository
        .findById(def.role())
        .filter(e -> e.getProvisioningStatus() != ProvisioningStatus.PROVISIONED)
        .ifPresent(
            entity -> {
              entity.setDefaultVaId(accountId);
              entity.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
              entity.setProvisionedAt(Instant.now().toString());
              entity.setLastError(null);
              accountRoleRepository.save(entity);
              log.info("Reconciled role {} to existing VA {}", def.role(), accountId);
            });
  }

  private void persistFailure(SystemAccountDefinition def, String errorMessage) {
    accountRoleRepository
        .findById(def.role())
        .filter(e -> e.getProvisioningStatus() != ProvisioningStatus.PROVISIONED)
        .ifPresent(
            entity -> {
              entity.setProvisioningStatus(ProvisioningStatus.FAILED);
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
