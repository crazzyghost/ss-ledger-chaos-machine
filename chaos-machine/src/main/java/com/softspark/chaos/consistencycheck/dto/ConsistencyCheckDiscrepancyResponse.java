package com.softspark.chaos.consistencycheck.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Chaos-facing DTO for a single consistency check discrepancy (finding).
 *
 * <p>Maps from {@code LedgerConsistencyCheckDiscrepancyDto}. The {@code details} field is a JSONB
 * pass-through — the UI renders it as formatted JSON.
 */
public record ConsistencyCheckDiscrepancyResponse(
    UUID id,
    String code,
    UUID accountId,
    UUID entryId,
    Map<String, Object> details,
    Instant detectedAt) {

  public static ConsistencyCheckDiscrepancyResponse from(
      com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDiscrepancyDto dto) {
    return new ConsistencyCheckDiscrepancyResponse(
        dto.id(), dto.code(), dto.accountId(), dto.entryId(), dto.details(), dto.detectedAt());
  }
}
