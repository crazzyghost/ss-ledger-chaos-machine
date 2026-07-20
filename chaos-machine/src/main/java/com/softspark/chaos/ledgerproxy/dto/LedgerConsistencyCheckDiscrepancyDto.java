package com.softspark.chaos.ledgerproxy.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ledger DTO for a single consistency check discrepancy (finding).
 *
 * <p>Mirrors the ledger's {@code ConsistencyCheckDiscrepancyResponse} shape. The {@code details}
 * field is a JSONB pass-through — the chaos machine does not interpret it; the UI renders it as
 * formatted JSON.
 */
public record LedgerConsistencyCheckDiscrepancyDto(
    UUID id,
    String code,
    UUID accountId,
    UUID entryId,
    Map<String, Object> details,
    Instant detectedAt) {}
