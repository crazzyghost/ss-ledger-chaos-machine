package com.softspark.chaos.transaction.dto;

import com.softspark.chaos.transaction.model.TransactionFailure;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a single {@link TransactionFailure} projection row.
 *
 * @param id the projection row id
 * @param eventId the source envelope event id (idempotency key)
 * @param transactionRequestId the chaos-supplied request id — the correlation key to a publish
 * @param ledgerTransactionId the ledger's recording UUID ({@code data.transaction_id})
 * @param transactionType COLLECTION | DISBURSEMENT | SETTLEMENT | …
 * @param failureCode the ledger failure code
 * @param failureReason the human-readable failure reason
 * @param ledgerCorrelationId the failure envelope correlation id (= ledger recording id)
 * @param idempotencyKey the failure envelope idempotency key ({@code "{request_id}:failed"})
 * @param tenantId the tenant id
 * @param occurredAt the failure envelope timestamp
 * @param receivedAt when the chaos machine consumed it
 * @param payloadJson the raw failure envelope (detail view only)
 */
@RecordBuilder
public record TransactionFailureResponse(
    String id,
    String eventId,
    String transactionRequestId,
    String ledgerTransactionId,
    String transactionType,
    @Nullable String failureCode,
    @Nullable String failureReason,
    @Nullable String ledgerCorrelationId,
    @Nullable String idempotencyKey,
    @Nullable String tenantId,
    Instant occurredAt,
    Instant receivedAt,
    @Nullable String payloadJson) {

  /**
   * Maps a {@link TransactionFailure} entity to a response DTO.
   *
   * @param entity the entity to map
   * @return the response DTO
   */
  public static TransactionFailureResponse from(TransactionFailure entity) {
    return new TransactionFailureResponse(
        entity.getId(),
        entity.getEventId(),
        entity.getTransactionRequestId(),
        entity.getLedgerTransactionId(),
        entity.getTransactionType(),
        entity.getFailureCode(),
        entity.getFailureReason(),
        entity.getLedgerCorrelationId(),
        entity.getIdempotencyKey(),
        entity.getTenantId(),
        entity.getOccurredAt(),
        entity.getReceivedAt(),
        entity.getPayloadJson());
  }
}
