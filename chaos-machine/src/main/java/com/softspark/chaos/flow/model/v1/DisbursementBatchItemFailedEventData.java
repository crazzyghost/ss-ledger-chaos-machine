package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.batch.item.failed} events.
 *
 * <p>The failure phase of one batch item: the ledger <strong>partially releases</strong> the item's
 * gross back to {@code AVAILABLE} (no journal) and increments the batch's {@code failed_count}. Same
 * shape as {@link DisbursementBatchItemCompletedEventData} minus the journal fields, plus the failure
 * reason/code. The inbound {@code reservation_id} is required on the wire but ledger-ignored. The
 * field set is the authoritative {@code ss-ledger-service} contract (see ADR-022/ADR-023).
 *
 * @param batchId the batch id (carried)
 * @param itemId the item id (same as the item request; item idempotency reference)
 * @param itemSequence the 1-based sequence of this item within the batch (carried)
 * @param virtualAccountId the batch source ORGANIZATION virtual account whose slice is released
 * @param reservationId the ledger-created reservation id (required on the wire; ledger-ignored)
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER} (carried)
 * @param providerId the payment provider id
 * @param providerReferenceId the payment provider's reference id (nullable)
 * @param principalAmount the item's principal slice that failed to disburse (carried)
 * @param currency the ISO-4217 currency code (carried)
 * @param fees the fee lines (never null; may be empty)
 * @param failureReason a human-readable failure reason
 * @param failureCode a machine-readable failure code
 * @param failedAt ISO-8601 timestamp of the failure
 * @param merchantItemRef the merchant item reference (carried)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementBatchItemFailedEventData(
    String batchId,
    String itemId,
    int itemSequence,
    String virtualAccountId,
    String reservationId,
    String disbursementSubtype,
    String providerId,
    @Nullable String providerReferenceId,
    BigDecimal principalAmount,
    String currency,
    List<TransactionFeeLine> fees,
    String failureReason,
    String failureCode,
    String failedAt,
    String merchantItemRef) {}
