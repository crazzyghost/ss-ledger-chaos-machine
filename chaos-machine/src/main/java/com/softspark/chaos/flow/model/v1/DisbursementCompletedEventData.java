package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.completed} events.
 *
 * <p>The success phase of a disbursement lifecycle. Carries the same {@code transaction_id} minted on
 * {@code disbursement.initiated}; the inbound {@code reservation_id} is structurally required but
 * ledger-ignored (the ledger relinks by {@code transaction_id}). The field set is the authoritative
 * {@code ss-ledger-service} contract.
 *
 * @param transactionId the lifecycle/transaction id (same as initiated; idempotency key)
 * @param sourceVaId the merchant/organization virtual account (debited)
 * @param destinationVaId the system SETTLEMENT_ACCOUNT virtual account (credited)
 * @param reservationId the reservation id (required on the wire; ledger-ignored)
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER}
 * @param providerId the payment provider id
 * @param providerReferenceId the payment provider's reference id (the transaction reference)
 * @param principalAmount the principal amount disbursed
 * @param currency the ISO-4217 currency code
 * @param fees the fee lines (never null; may be empty)
 * @param recipientReference optional recipient reference (nullable)
 * @param destinationCountry optional ISO destination country (cross-border; nullable)
 * @param corridor optional corridor (cross-border; nullable)
 * @param appliedFxRate optional applied FX rate (cross-border; nullable)
 * @param completedAt ISO-8601 timestamp of completion
 * @param merchantRefId the merchant reference id (ULID-shaped)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementCompletedEventData(
    String transactionId,
    String sourceVaId,
    String destinationVaId,
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
    String merchantRefId) {}
