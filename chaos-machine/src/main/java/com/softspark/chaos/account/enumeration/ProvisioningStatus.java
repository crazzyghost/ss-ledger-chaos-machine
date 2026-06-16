package com.softspark.chaos.account.enumeration;

/**
 * Represents the provisioning state of a system account role against the ledger service.
 *
 * <ul>
 *   <li>{@link #PENDING} — the role has been seeded locally but not yet confirmed by the ledger.
 *   <li>{@link #PROVISIONED} — the ledger has assigned a virtual-account ID to this role.
 *   <li>{@link #FAILED} — all reconciliation attempts have failed; manual intervention may be
 *       required.
 * </ul>
 */
public enum ProvisioningStatus {
  PENDING,
  PROVISIONED,
  FAILED
}
