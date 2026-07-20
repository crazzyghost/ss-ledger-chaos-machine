package com.softspark.chaos.consistencycheck.consumer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of the ledger's {@code ReconciliationMismatchEventData} (snake_case), carried as the
 * {@code data} of an {@link com.softspark.chaos.kafka.EventEnvelope} on {@code
 * ledger.reconciliation.mismatch}.
 *
 * <p>The ledger emits this event when a consistency check completes with {@code
 * discrepancyCount > 0}. The chaos machine projects it to the {@code reconciliation_mismatch} table
 * for toast notification.
 *
 * @param checkId the ledger-side consistency check ID
 * @param type the check type (enum name: ACCOUNT_BALANCE_PROJECTION, ENTRY_BALANCE,
 *     SEQUENCE_INTEGRITY)
 * @param initiatorType the initiator type (enum name: SYSTEM, PLATFORM_OPERATOR)
 * @param asOf the check's as-of timestamp (the snapshot time)
 * @param initiatedAt the check's initiation timestamp
 * @param completedAt the check's completion timestamp
 * @param discrepancyCount the number of findings (always >= 1 by contract)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReconciliationMismatchEventData(
    UUID checkId,
    String type,
    String initiatorType,
    Instant asOf,
    Instant initiatedAt,
    Instant completedAt,
    int discrepancyCount) {}
