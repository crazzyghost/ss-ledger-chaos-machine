package com.softspark.chaos.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.transaction.consumer.LedgerTransactionFailedEventData;
import com.softspark.chaos.transaction.model.TransactionFailure;
import com.softspark.chaos.transaction.repository.TransactionFailureRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps a {@code ledger.transaction.failed} envelope into the {@code transaction_failure} projection
 * with an idempotent, at-least-once-safe upsert (ADR-025).
 *
 * <p>Idempotency is by envelope {@code event_id} (UNIQUE): a redelivered failure is a no-op. A
 * null/partial envelope is logged and skipped — it never throws, so it never dead-letters
 * needlessly (only genuinely unparseable bytes DLT, via the ADR-024 handler).
 */
@Service
public class TransactionFailureProjectionService {

  private static final Logger log =
      LoggerFactory.getLogger(TransactionFailureProjectionService.class);

  private final TransactionFailureRepository repository;
  private final ObjectMapper kafkaObjectMapper;

  public TransactionFailureProjectionService(
      TransactionFailureRepository repository,
      @Qualifier("kafkaObjectMapper") ObjectMapper kafkaObjectMapper) {
    this.repository = repository;
    this.kafkaObjectMapper = kafkaObjectMapper;
  }

  /**
   * Projects a single failure envelope. Safe to call repeatedly with the same event.
   *
   * @param envelope the consumed failure envelope (may be null/partial)
   */
  @Transactional
  public void project(EventEnvelope<LedgerTransactionFailedEventData> envelope) {
    if (envelope == null || envelope.data() == null || envelope.eventId() == null) {
      log.warn("Skipping ledger.transaction.failed with empty envelope/data/event_id");
      return;
    }
    String eventId = envelope.eventId();
    if (repository.existsByEventId(eventId)) {
      log.debug("Duplicate ledger.transaction.failed event {} — skipping", eventId);
      return;
    }

    LedgerTransactionFailedEventData data = envelope.data();
    EventMetadata metadata = envelope.metadata();

    TransactionFailure entity = new TransactionFailure();
    entity.setId(Ids.generate());
    entity.setEventId(eventId);
    entity.setTransactionRequestId(data.transactionRequestId());
    entity.setLedgerTransactionId(
        data.transactionId() == null ? null : data.transactionId().toString());
    entity.setTransactionType(data.transactionType());
    entity.setFailureCode(data.failureCode());
    entity.setFailureReason(data.failureReason());
    entity.setLedgerCorrelationId(metadata == null ? null : metadata.correlationId());
    entity.setIdempotencyKey(metadata == null ? null : metadata.idempotencyKey());
    entity.setTenantId(metadata == null ? null : metadata.tenantId());
    entity.setOccurredAt(envelope.timestamp());
    entity.setReceivedAt(Instant.now());
    entity.setPayloadJson(serialize(envelope));

    try {
      repository.save(entity);
      log.debug(
          "Projected transaction failure request_id={} code={}",
          data.transactionRequestId(),
          data.failureCode());
    } catch (DataIntegrityViolationException e) {
      // A concurrent redelivery won the UNIQUE(event_id) race; the row already exists — no-op.
      log.debug("Concurrent duplicate failure event {} — already projected", eventId);
    }
  }

  private String serialize(EventEnvelope<LedgerTransactionFailedEventData> envelope) {
    try {
      return kafkaObjectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize transaction-failure payload for event {}", envelope.eventId());
      return null;
    }
  }
}
