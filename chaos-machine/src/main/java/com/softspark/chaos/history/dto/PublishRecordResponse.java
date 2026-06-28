package com.softspark.chaos.history.dto;

import com.softspark.chaos.history.model.PublishRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a single {@link PublishRecord}.
 *
 * @param id the history record ULID
 * @param eventId the event ULID
 * @param eventType the Kafka event type string
 * @param topic the Kafka topic
 * @param source the source system
 * @param correlationId the correlation identifier
 * @param idempotencyKey the idempotency key
 * @param tenantId the tenant identifier
 * @param transactionRequestId the canonical request id (correlation key for ledger outcomes); null
 *     for non-transactional flows and historical rows
 * @param sourceVaId the source virtual account id
 * @param destinationVaId the destination virtual account id
 * @param status {@code "PUBLISHED"} or {@code "FAILED"}
 * @param intentionalFailure whether this was a deliberate failure (chaos)
 * @param chaosStrategy the chaos label applied; null for normal sends
 * @param payloadJson the serialized event payload
 * @param batchId the batch run id; null for non-batch sends
 * @param batchRowId the batch row id; null for non-batch sends
 * @param kafkaOffset the Kafka broker offset; null on failure
 * @param kafkaPartition the Kafka partition; null on failure
 * @param createdAt the record creation timestamp
 */
@RecordBuilder
public record PublishRecordResponse(
    String id,
    String eventId,
    String eventType,
    String topic,
    String source,
    @Nullable String correlationId,
    @Nullable String idempotencyKey,
    @Nullable String tenantId,
    @Nullable String transactionRequestId,
    @Nullable String sourceVaId,
    @Nullable String destinationVaId,
    String status,
    boolean intentionalFailure,
    @Nullable String chaosStrategy,
    @Nullable String payloadJson,
    @Nullable String batchId,
    @Nullable String batchRowId,
    @Nullable Long kafkaOffset,
    @Nullable Integer kafkaPartition,
    Instant createdAt) {

  /**
   * Maps a {@link PublishRecord} entity to a {@link PublishRecordResponse}.
   *
   * @param record the entity to map
   * @return the response DTO
   */
  public static PublishRecordResponse from(PublishRecord record) {
    return new PublishRecordResponse(
        record.getId(),
        record.getEventId(),
        record.getEventType(),
        record.getTopic(),
        record.getSource(),
        record.getCorrelationId(),
        record.getIdempotencyKey(),
        record.getTenantId(),
        record.getTransactionRequestId(),
        record.getSourceVaId(),
        record.getDestinationVaId(),
        record.getStatus(),
        record.isIntentionalFailure(),
        record.getChaosStrategy(),
        record.getPayloadJson(),
        record.getBatchId(),
        record.getBatchRowId(),
        record.getKafkaOffset(),
        record.getKafkaPartition(),
        record.getCreatedAt());
  }
}
