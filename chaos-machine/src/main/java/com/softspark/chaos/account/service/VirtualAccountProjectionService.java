package com.softspark.chaos.account.service;

import com.softspark.chaos.account.consumer.LedgerAccountCreatedEventData;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes the {@code virtual_account} registry from {@code ledger.account.created} events.
 *
 * <p>Per Phase 009, the ledger owns virtual accounts; the chaos registry is a read projection. Each
 * event is upserted by the ledger {@code account_id} (which becomes the chaos {@code va_id}), so
 * at-least-once redelivery never produces a duplicate row — a second delivery updates the existing
 * row in place. When the account is a SYSTEM account whose {@code account_code} matches a known
 * {@link AccountRoleEntity}, the role is linked: its {@code default_va_id} is set and it is flipped
 * to {@link ProvisioningStatus#PROVISIONED}.
 */
@Service
public class VirtualAccountProjectionService {

  private static final Logger log = LoggerFactory.getLogger(VirtualAccountProjectionService.class);

  private final VirtualAccountRepository virtualAccountRepository;
  private final AccountRoleRepository accountRoleRepository;

  public VirtualAccountProjectionService(
      VirtualAccountRepository virtualAccountRepository,
      AccountRoleRepository accountRoleRepository) {
    this.virtualAccountRepository = virtualAccountRepository;
    this.accountRoleRepository = accountRoleRepository;
  }

  /**
   * Idempotently projects an account-created event into the VA registry and links its role.
   *
   * @param data the deserialized ledger event payload
   */
  @Transactional
  public void project(LedgerAccountCreatedEventData data) {
    if (data == null || data.accountId() == null || data.accountId().isBlank()) {
      log.warn("Skipping ledger.account.created projection — missing account_id");
      return;
    }

    String accountId = data.accountId();
    var va = virtualAccountRepository.findById(accountId).orElseGet(VirtualAccount::new);
    boolean isNew = va.getVaId() == null;

    va.setVaId(accountId);
    va.setName(data.accountName());
    va.setOwnershipType(parseOwnership(data.accountOwnershipType()));
    va.setOrganizationId(data.organizationId());
    va.setCurrency(data.currency());
    va.setStatus(parseStatus(data.status()));
    va.setAccountCode(data.accountCode());
    va.setAccountCategory(data.accountCategory());
    va.setCreatedVia(CreatedVia.KAFKA);

    linkRole(data, va);

    virtualAccountRepository.save(va);
    log.info(
        "Projected ledger account {} ({}) into VA registry [{}]",
        accountId,
        data.accountCode(),
        isNew ? "inserted" : "updated");
  }

  /**
   * Links the matching account role for SYSTEM accounts, flipping it to PROVISIONED. Org accounts
   * and unknown codes are left without a role link (best-effort, never an error).
   */
  private void linkRole(LedgerAccountCreatedEventData data, VirtualAccount va) {
    if (!"SYSTEM".equalsIgnoreCase(data.accountOwnershipType()) || data.accountCode() == null) {
      return;
    }

    accountRoleRepository
        .findByAccountCode(data.accountCode())
        .ifPresent(
            role -> {
              va.setAccountRole(role.getRole());
              role.setDefaultVaId(data.accountId());
              role.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
              role.setProvisionedAt(Instant.now().toString());
              role.setLastError(null);
              accountRoleRepository.save(role);
              log.info(
                  "Linked role {} → ledger account {} (PROVISIONED)",
                  role.getRole(),
                  data.accountId());
            });
  }

  private AccountOwnershipType parseOwnership(String value) {
    if (value == null) {
      return AccountOwnershipType.ORGANIZATION;
    }
    try {
      return AccountOwnershipType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown account_ownership_type '{}' — defaulting to ORGANIZATION", value);
      return AccountOwnershipType.ORGANIZATION;
    }
  }

  private AccountStatus parseStatus(String value) {
    if (value == null) {
      return AccountStatus.ACTIVE;
    }
    try {
      return AccountStatus.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown account status '{}' — defaulting to ACTIVE", value);
      return AccountStatus.ACTIVE;
    }
  }
}
