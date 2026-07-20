package com.softspark.chaos.consistencycheck.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Chaos-facing DTO for a single consistency check.
 *
 * <p>Maps from {@code LedgerConsistencyCheckDto}. Uses camelCase for frontend consumption.
 */
public record ConsistencyCheckResponse(
    UUID checkId,
    String type,
    String status,
    String initiatorType,
    UUID initiatedBy,
    Instant asOf,
    Instant initiatedAt,
    Instant completedAt,
    Instant erroredAt,
    String errorCode,
    int discrepancyCount) {

  public static ConsistencyCheckResponse from(
      com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDto dto) {
    return new ConsistencyCheckResponse(
        dto.checkId(),
        dto.type(),
        dto.status(),
        dto.initiatorType(),
        dto.initiatedBy(),
        dto.asOf(),
        dto.initiatedAt(),
        dto.completedAt(),
        dto.erroredAt(),
        dto.errorCode(),
        dto.discrepancyCount());
  }
}
