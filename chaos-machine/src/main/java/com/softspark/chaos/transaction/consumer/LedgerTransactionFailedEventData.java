package com.softspark.chaos.transaction.consumer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * Mirror of the ledger's {@code TransactionFailedEventData} (snake_case, no JSON type headers),
 * carried as the {@code data} of an {@link com.softspark.chaos.kafka.EventEnvelope} on
 * {@code ledger.transaction.failed}.
 *
 * <p><strong>Two distinct ids — do not confuse them.</strong> {@code transactionId} is the ledger's
 * own recording UUID ({@code data.transaction_id}); {@code transactionRequestId} is the
 * chaos-supplied id the publisher placed in the inbound event's request-id field
 * ({@code data.transaction_request_id}). Correlation back to a publish matches the
 * <em>latter</em> (ADR-025).
 *
 * @param transactionId the ledger recording id ({@code data.transaction_id})
 * @param transactionRequestId the publisher-supplied request id — the correlation key
 * @param transactionType COLLECTION | DISBURSEMENT | SETTLEMENT | TRANSFER | …
 * @param failureCode the ledger failure code
 * @param failureReason the human-readable failure reason
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LedgerTransactionFailedEventData(
    UUID transactionId,
    String transactionRequestId,
    String transactionType,
    String failureCode,
    String failureReason) {}
