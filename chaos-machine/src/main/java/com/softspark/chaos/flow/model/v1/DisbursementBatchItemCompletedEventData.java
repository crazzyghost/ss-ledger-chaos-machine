package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.batch.item.completed} events.
 *
 * <p>The success phase of one batch item: the ledger captures the item's gross
 * ({@code principal_amount} + Σ{@code fees}) from the shared BATCH reservation and posts a journal
 * crediting the destination VA with the principal and each fee VA with its fee, then increments the
 * batch's {@code processed_count}. The inbound {@code reservation_id} is structurally required but
 * ledger-ignored (the ledger relinks by {@code batch_id}). The field set is the authoritative
 * {@code ss-ledger-service} contract (see ADR-022/ADR-023).
 *
 * @param batchId the batch id (carried)
 * @param itemId the item id (same as the item request; item idempotency reference)
 * @param itemSequence the 1-based sequence of this item within the batch (carried)
 * @param virtualAccountId the batch source ORGANIZATION virtual account (carried)
 * @param reservationId the ledger-created reservation id (required on the wire; ledger-ignored)
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER} (carried)
 * @param providerId the payment provider id
 * @param providerReferenceId the payment provider's reference id
 * @param principalAmount the item's principal slice (carried)
 * @param currency the ISO-4217 currency code (carried)
 * @param fees the fee lines (never null; may be empty)
 * @param recipientReference optional recipient reference (nullable)
 * @param destinationCountry optional ISO destination country (cross-border; nullable)
 * @param corridor optional corridor (cross-border; nullable)
 * @param appliedFxRate optional applied FX rate (cross-border; nullable)
 * @param completedAt ISO-8601 timestamp of completion
 * @param merchantItemRef the merchant item reference (carried)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementBatchItemCompletedEventData(
    String batchId,
    String itemId,
    int itemSequence,
    String virtualAccountId,
    String reservationId,
    String disbursementSubtype,
    String providerId,
    String providerReferenceId,
    BigDecimal principalAmount,
    String currency,
    List<TransactionFeeLine> fees,
    @Nullable String recipientReference,
    @Nullable String destinationCountry,
    @Nullable String corridor,
    @Nullable BigDecimal appliedFxRate,
    String completedAt,
    String merchantItemRef) {}
