package com.softspark.chaos.consistencycheck.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for a single reconciliation mismatch event (for polling).
 *
 * <p>Returned by {@code GET /reconciliation-mismatches?since=} for toast notification.
 */
public record ReconciliationMismatchDto(
    UUID id,
    UUID checkId,
    String type,
    String initiatorType,
    Instant asOf,
    Instant initiatedAt,
    Instant completedAt,
    int discrepancyCount,
    LocalDateTime consumedAt) {

  public static ReconciliationMismatchDto from(
      com.softspark.chaos.consistencycheck.model.ReconciliationMismatch entity) {
    return new ReconciliationMismatchDto(
        UUID.fromString(entity.getId()),
        UUID.fromString(entity.getCheckId()),
        entity.getType(),
        entity.getInitiatorType(),
        entity.getAsOf(),
        entity.getInitiatedAt(),
        entity.getCompletedAt(),
        entity.getDiscrepancyCount(),
        entity.getConsumedAt());
  }
}
