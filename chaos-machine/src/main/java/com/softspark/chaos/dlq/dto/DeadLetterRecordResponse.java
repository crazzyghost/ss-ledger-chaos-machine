package com.softspark.chaos.dlq.dto;

import com.softspark.chaos.dlq.model.DeadLetterRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a {@code dlq} row (ADR-029). The heavy {@code originalPayloadJson}/{@code
 * rawDltJson} fields are populated only on the by-id detail path ({@link #detail}) and left null in
 * list rows ({@link #summary}) to keep list pages light.
 *
 * @param id the projection row id
 * @param dltTopic the {@code .dlt} topic consumed from
 * @param originalTopic the original inbound topic the ledger rejected
 * @param domain the coarse domain bucket (filterable)
 * @param source the source discriminator ({@code LEDGER_INBOUND})
 * @param eventType the original event type (best-effort)
 * @param eventId the original event id (best-effort)
 * @param transactionId the best-effort transaction id (filterable)
 * @param transactionType the best-effort transaction type (filterable)
 * @param failureClassification PROCESSING | DESERIALIZATION | VERSION_RESOLUTION
 * @param errorType the exception type (the "error code")
 * @param errorMessage the failure message (the "error reason")
 * @param retryCount retries before dead-lettering
 * @param originalPartition the original record partition
 * @param originalOffset the original record offset
 * @param originalKey the original record key
 * @param deadLetteredAt when the ledger dead-lettered it
 * @param receivedAt when the chaos machine consumed it
 * @param originalPayloadJson the original payload (detail only; null in list rows)
 * @param rawDltJson the full raw DLT record (detail only; null in list rows)
 */
@RecordBuilder
public record DeadLetterRecordResponse(
    String id,
    String dltTopic,
    String originalTopic,
    String domain,
    String source,
    @Nullable String eventType,
    @Nullable String eventId,
    @Nullable String transactionId,
    @Nullable String transactionType,
    @Nullable String failureClassification,
    @Nullable String errorType,
    @Nullable String errorMessage,
    @Nullable Integer retryCount,
    @Nullable Integer originalPartition,
    @Nullable Long originalOffset,
    @Nullable String originalKey,
    @Nullable Instant deadLetteredAt,
    Instant receivedAt,
    @Nullable String originalPayloadJson,
    @Nullable String rawDltJson) {

  /**
   * Lightweight list projection — omits the heavy payload/raw JSON.
   *
   * @param entity the entity
   * @return a summary response
   */
  public static DeadLetterRecordResponse summary(DeadLetterRecord entity) {
    return map(entity, null, null);
  }

  /**
   * Full detail projection — includes the original payload and raw DLT JSON.
   *
   * @param entity the entity
   * @return a detail response
   */
  public static DeadLetterRecordResponse detail(DeadLetterRecord entity) {
    return map(entity, entity.getOriginalPayloadJson(), entity.getRawDltJson());
  }

  private static DeadLetterRecordResponse map(
      DeadLetterRecord e, @Nullable String originalPayloadJson, @Nullable String rawDltJson) {
    return new DeadLetterRecordResponse(
        e.getId(),
        e.getDltTopic(),
        e.getOriginalTopic(),
        e.getDomain(),
        e.getSource(),
        e.getEventType(),
        e.getEventId(),
        e.getTransactionId(),
        e.getTransactionType(),
        e.getFailureClassification(),
        e.getErrorType(),
        e.getErrorMessage(),
        e.getRetryCount(),
        e.getOriginalPartition(),
        e.getOriginalOffset(),
        e.getOriginalKey(),
        e.getDeadLetteredAt(),
        e.getReceivedAt(),
        originalPayloadJson,
        rawDltJson);
  }
}
