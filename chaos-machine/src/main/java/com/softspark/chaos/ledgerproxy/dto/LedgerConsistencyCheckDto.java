package com.softspark.chaos.ledgerproxy.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ledger DTO for a single consistency check.
 *
 * <p>Mirrors the ledger's {@code ConsistencyCheckResponse} shape. Maps one-to-one from the ledger's
 * JSON response (snake_case → camelCase via Jackson).
 */
public record LedgerConsistencyCheckDto(
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
    int discrepancyCount) {}
