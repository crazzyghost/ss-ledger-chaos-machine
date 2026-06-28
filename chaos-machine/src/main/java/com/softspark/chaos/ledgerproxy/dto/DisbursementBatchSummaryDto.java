package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * DTO mirroring the ledger's disbursement batch-summary response
 * ({@code GET /api/v0/disbursement-batches/{batchId}}, verified
 * {@code DisbursementBatchQueryController}).
 *
 * <p>The ledger's REST DTOs serialize {@code camelCase} (only its Kafka event payloads use
 * snake_case), so this record maps the ledger response field-for-field by camelCase name and passes
 * straight through to the admin UI / runner. It carries the ledger-created {@code reservationId}
 * (null until the reservation lands), the derived batch {@code status}, the live item counters, and
 * the monetary totals — the source of both the wizard's progress panel and the automatic run-results
 * Ledger Batch panel (ADR-023).
 *
 * @param batchId the batch id (the driver-controlled {@code batch_id})
 * @param reservationId the ledger-created BATCH reservation id (null until the reservation lands)
 * @param status the derived batch status ({@code INITIATED}/{@code IN_PROGRESS}/{@code COMPLETED}/
 *     {@code FAILED}/{@code PARTIALLY_COMPLETED})
 * @param currency the ISO-4217 currency code
 * @param itemCount the declared number of items N
 * @param processedCount the number of items captured (completed)
 * @param failedCount the number of items released (failed)
 * @param pendingCount the number of items not yet terminal
 * @param totalPrincipalAmount the total principal reserved
 * @param totalFees the total fees reserved
 * @param totalAmount the total amount reserved ({@code total_principal + total_fees})
 * @param amountCaptured the amount captured so far (nullable)
 * @param amountReleased the amount released so far (nullable)
 * @param createdAt the batch creation timestamp (nullable)
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisbursementBatchSummaryDto(
    String batchId,
    @Nullable String reservationId,
    String status,
    @Nullable String currency,
    int itemCount,
    int processedCount,
    int failedCount,
    int pendingCount,
    @Nullable BigDecimal totalPrincipalAmount,
    @Nullable BigDecimal totalFees,
    @Nullable BigDecimal totalAmount,
    @Nullable BigDecimal amountCaptured,
    @Nullable BigDecimal amountReleased,
    @Nullable String createdAt) {}
