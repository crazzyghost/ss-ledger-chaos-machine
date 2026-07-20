package com.softspark.chaos.ledgerproxy.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ledger DTO for the trigger response (one or more triggered checks).
 *
 * <p>Mirrors the ledger's {@code ConsistencyCheckTriggerResponse}. When {@code type=ALL}, the list
 * contains three checks (one per type); otherwise it contains one.
 */
public record LedgerConsistencyCheckTriggerDto(List<TriggeredCheck> checks) {

  /**
   * A single triggered check.
   *
   * @param type the check type (enum name)
   * @param checkId the ledger-side check ID
   * @param status always {@code PENDING} immediately after trigger
   */
  public record TriggeredCheck(String type, UUID checkId, String status) {}
}
