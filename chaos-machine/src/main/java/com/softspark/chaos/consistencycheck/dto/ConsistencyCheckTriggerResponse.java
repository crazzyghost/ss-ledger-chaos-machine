package com.softspark.chaos.consistencycheck.dto;

import com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckTriggerDto;
import java.util.List;
import java.util.UUID;

/**
 * Chaos-facing DTO for the trigger response (one or more triggered checks).
 *
 * <p>Maps from {@code LedgerConsistencyCheckTriggerDto}.
 */
public record ConsistencyCheckTriggerResponse(List<TriggeredCheck> checks) {

  /**
   * A single triggered check.
   *
   * @param type the check type (enum name)
   * @param checkId the ledger-side check ID
   * @param status always {@code PENDING} immediately after trigger
   */
  public record TriggeredCheck(String type, UUID checkId, String status) {}

  public static ConsistencyCheckTriggerResponse from(LedgerConsistencyCheckTriggerDto dto) {
    var checks =
        dto.checks().stream()
            .map(c -> new TriggeredCheck(c.type(), c.checkId(), c.status()))
            .toList();
    return new ConsistencyCheckTriggerResponse(checks);
  }
}
