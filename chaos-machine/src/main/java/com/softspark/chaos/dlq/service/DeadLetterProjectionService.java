package com.softspark.chaos.dlq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.dlq.consumer.LedgerDeadLetterRecord;
import com.softspark.chaos.dlq.model.DeadLetterRecord;
import com.softspark.chaos.dlq.repository.DeadLetterRecordRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects one ledger inbound dead letter into the {@code dlq} table (ADR-029): derives the domain,
 * best-effort mines the transaction id/type from the (possibly absent) original payload, and dedups
 * by the {@code (dlt_topic, dlt_partition, dlt_offset)} coordinates.
 */
@Service
public class DeadLetterProjectionService {

  /** {@code source} discriminator for the ledger's inbound DLTs (future: {@code CHAOS_CONSUMER}). */
  static final String SOURCE_LEDGER_INBOUND = "LEDGER_INBOUND";

  /** Candidate request-id fields, in precedence order, mined from {@code originalEvent.data}. */
  private static final String[] TXN_ID_FIELDS = {
    "transaction_id",
    "transaction_request_id",
    "settlement_request_id",
    "transfer_request_id",
    "topup_request_id",
    "prefund_request_id",
    "sweep_request_id",
    "batch_id",
    "item_id"
  };

  private static final Logger log = LoggerFactory.getLogger(DeadLetterProjectionService.class);

  private final DeadLetterRecordRepository repository;
  private final ObjectMapper kafkaObjectMapper;

  public DeadLetterProjectionService(
      DeadLetterRecordRepository repository,
      @Qualifier("kafkaObjectMapper") ObjectMapper kafkaObjectMapper) {
    this.repository = repository;
    this.kafkaObjectMapper = kafkaObjectMapper;
  }

  /**
   * Projects a single dead letter. Idempotent by the consumed {@code .dlt} record's coordinates.
   *
   * @param record the deserialized DLT record
   * @param dltTopic the {@code .dlt} topic consumed from (Kafka header)
   * @param dltPartition the {@code .dlt} partition (Kafka header)
   * @param dltOffset the {@code .dlt} offset (Kafka header)
   */
  @Transactional
  public void project(LedgerDeadLetterRecord record, String dltTopic, int dltPartition, long dltOffset) {
    if (record == null) {
      log.warn("Skipping null dead-letter record on {}", dltTopic);
      return;
    }
    if (repository.existsByDltTopicAndDltPartitionAndDltOffset(dltTopic, dltPartition, dltOffset)) {
      log.debug("Duplicate dead letter {}/{}/{} — skipping", dltTopic, dltPartition, dltOffset);
      return;
    }

    JsonNode original = record.originalEvent();
    JsonNode data = original == null ? null : original.get("data");
    String eventType = text(original, "event_type");

    DeadLetterRecord entity = new DeadLetterRecord();
    entity.setId(Ids.generate());
    entity.setDltTopic(dltTopic);
    entity.setDltPartition(dltPartition);
    entity.setDltOffset(dltOffset);
    entity.setDeadLetterId(record.deadLetterId() == null ? null : record.deadLetterId().toString());
    entity.setOriginalTopic(record.originalTopic());
    entity.setDomain(DlqDomain.of(record.originalTopic()));
    entity.setSource(SOURCE_LEDGER_INBOUND);
    entity.setEventType(eventType);
    entity.setEventId(text(original, "event_id"));
    entity.setTransactionId(extractTransactionId(data));
    entity.setTransactionType(extractTransactionType(data, eventType));

    LedgerDeadLetterRecord.Failure failure = record.failure();
    if (failure != null) {
      entity.setFailureClassification(failure.classification());
      entity.setErrorType(failure.exceptionType());
      entity.setErrorMessage(failure.message());
      entity.setRetryCount(failure.retryCount());
    }

    entity.setOriginalPartition(record.originalPartition());
    entity.setOriginalOffset(record.originalOffset());
    entity.setOriginalKey(record.originalKey());
    entity.setDeadLetteredAt(record.deadLetteredAt());
    entity.setOriginalPayloadJson(original == null ? null : serialize(original));
    entity.setRawDltJson(serialize(record));
    entity.setReceivedAt(Instant.now());

    try {
      repository.save(entity);
      log.debug(
          "Projected dead letter from {} (domain={}, class={})",
          record.originalTopic(),
          entity.getDomain(),
          entity.getFailureClassification());
    } catch (DataIntegrityViolationException e) {
      log.debug("Concurrent duplicate dead letter {}/{}/{}", dltTopic, dltPartition, dltOffset);
    }
  }

  private static String extractTransactionId(JsonNode data) {
    if (data == null) {
      return null;
    }
    for (String field : TXN_ID_FIELDS) {
      String value = text(data, field);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String extractTransactionType(JsonNode data, String eventType) {
    String type = text(data, "transaction_type");
    if (type != null && !type.isBlank()) {
      return type;
    }
    if (eventType == null) {
      return null;
    }
    String derived = DlqDomain.of(eventType);
    return DlqDomain.UNKNOWN.equals(derived) ? null : derived;
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private String serialize(Object value) {
    try {
      return kafkaObjectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize dead-letter JSON: {}", e.getMessage());
      return null;
    }
  }
}
