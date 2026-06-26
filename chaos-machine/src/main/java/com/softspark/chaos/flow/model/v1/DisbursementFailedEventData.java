package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/**
 * Event data for {@code disbursement.failed} events.
 *
 * <p>The failure phase of a disbursement lifecycle. Carries the same {@code transaction_id} minted on
 * {@code disbursement.initiated}; the inbound {@code reservation_id} is required on the wire but
 * ledger-ignored (the ledger releases the reservation it linked by {@code transaction_id}). The
 * field set is the authoritative {@code ss-ledger-service} contract.
 *
 * @param transactionId the lifecycle/transaction id (same as initiated; idempotency key)
 * @param virtualAccountId the merchant's own virtual account whose reservation is released
 * @param reservationId the reservation id (required on the wire; ledger-ignored)
 * @param disbursementSubtype {@code DOMESTIC} or {@code CROSS_BORDER}
 * @param providerId the payment provider id
 * @param providerReferenceId the payment provider's reference id (nullable)
 * @param principalAmount the principal amount that failed to disburse
 * @param currency the ISO-4217 currency code
 * @param failureReason a human-readable failure reason
 * @param failureCode an optional machine-readable failure code (nullable)
 * @param failedAt ISO-8601 timestamp of the failure
 * @param merchantRefId the merchant reference id (ULID-shaped)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisbursementFailedEventData(
    String transactionId,
    String virtualAccountId,
    String reservationId,
    String disbursementSubtype,
    String providerId,
    @Nullable String providerReferenceId,
    BigDecimal principalAmount,
    String currency,
    String failureReason,
    @Nullable String failureCode,
    String failedAt,
    String merchantRefId) {}
