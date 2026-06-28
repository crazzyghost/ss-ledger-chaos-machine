package com.softspark.chaos.history.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.FlowBuilderRegistry;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.history.model.PublishRecord;
import com.softspark.chaos.history.repository.PublishRecordRepository;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous implementation of {@link HistoryWriter}.
 *
 * <p>Publish events are enqueued immediately (non-blocking) and drained by a dedicated virtual
 * thread. Enqueue operations never block the Kafka publish path. If the queue is full, the event is
 * dropped with a warning.
 */
@Component
public class AsyncHistoryWriter implements HistoryWriter {

  private static final Logger log = LoggerFactory.getLogger(AsyncHistoryWriter.class);

  private final LinkedBlockingQueue<HistoryEvent> queue;
  private final PublishRecordRepository repository;
  private final ObjectMapper objectMapper;
  private final FlowBuilderRegistry builderRegistry;

  public AsyncHistoryWriter(
      @Value("${chaos.history.queue-capacity:10000}") int queueCapacity,
      PublishRecordRepository repository,
      ObjectMapper objectMapper,
      FlowBuilderRegistry builderRegistry) {
    this.queue = new LinkedBlockingQueue<>(queueCapacity);
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.builderRegistry = builderRegistry;
  }

  /**
   * Starts the virtual-thread drain loop after bean initialization.
   */
  @PostConstruct
  public void startDrainThread() {
    Thread.ofVirtual().name("history-drain").start(this::drainLoop);
  }

  @Override
  public String record(
      EventEnvelope<?> envelope,
      String topic,
      String partitionKey,
      ChaosEventPublisher.PublishResult publishResult,
      FlowRequest request,
      @Nullable String chaosLabel,
      boolean intentionalFailure) {
    String historyId = Ids.generate();
    var event =
        new HistoryEvent(
            historyId,
            envelope,
            topic,
            request,
            "PUBLISHED",
            publishResult.offset(),
            publishResult.partition(),
            chaosLabel,
            intentionalFailure,
            null,
            null);
    offerOrDrop(event);
    return historyId;
  }

  @Override
  public String recordFailure(
      EventEnvelope<?> envelope, String topic, String errorMsg, FlowRequest request) {
    String historyId = Ids.generate();
    var event =
        new HistoryEvent(
            historyId, envelope, topic, request, "FAILED", -1L, -1, null, false, errorMsg, null);
    offerOrDrop(event);
    return historyId;
  }

  @Override
  public String recordBatch(
      EventEnvelope<?> envelope,
      String topic,
      String partitionKey,
      ChaosEventPublisher.PublishResult publishResult,
      FlowRequest request,
      String batchId,
      String batchRowId,
      @Nullable String chaosLabel,
      boolean intentionalFailure) {
    String historyId = Ids.generate();
    var event =
        new HistoryEvent(
            historyId,
            envelope,
            topic,
            request,
            "PUBLISHED",
            publishResult.offset(),
            publishResult.partition(),
            chaosLabel,
            intentionalFailure,
            null,
            new BatchContext(batchId, batchRowId));
    offerOrDrop(event);
    return historyId;
  }

  private void offerOrDrop(HistoryEvent event) {
    boolean accepted = queue.offer(event);
    if (!accepted) {
      log.warn(
          "History queue is full; dropping record for event {} (queue capacity exceeded)",
          event.envelope().eventId());
    }
  }

  private void drainLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        HistoryEvent event = queue.take();
        persistEvent(event);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("History drain thread interrupted, stopping");
        break;
      } catch (Exception e) {
        log.error("Failed to persist history event: {}", e.getMessage(), e);
      }
    }
  }

  @Transactional
  void persistEvent(HistoryEvent event) {
    PublishRecord record = new PublishRecord();
    record.setId(event.historyId());
    record.setEventId(event.envelope().eventId());
    record.setEventType(event.envelope().eventType());
    record.setTopic(event.topic());
    record.setSource(event.envelope().source());
    record.setCorrelationId(
        event.envelope().metadata() != null ? event.envelope().metadata().correlationId() : null);
    record.setIdempotencyKey(
        event.envelope().metadata() != null ? event.envelope().metadata().idempotencyKey() : null);
    record.setTenantId(
        event.envelope().metadata() != null ? event.envelope().metadata().tenantId() : null);
    // The canonical request id (labelled per flow) — the join key a later ledger.transaction.failed
    // is correlated by. Null for non-transactional flows and historical rows (ADR-025).
    record.setTransactionRequestId(
        builderRegistry.transactionRequestIdValue(event.request()).orElse(null));

    var slots = event.request().slotOverrides();
    record.setSourceVaId(
        VaIdExtractor.extractSourceVaId(event.request().slotOverrides(), event.request()));
    record.setDestinationVaId(VaIdExtractor.extractDestinationVaId(slots, event.request()));

    record.setStatus(event.status());
    record.setIntentionalFailure(event.intentionalFailure());
    record.setChaosStrategy(event.chaosLabel());
    record.setPayloadJson(serializePayload(event.envelope()));
    record.setKafkaOffset(event.kafkaOffset() >= 0 ? event.kafkaOffset() : null);
    record.setKafkaPartition(event.kafkaPartition() >= 0 ? event.kafkaPartition() : null);
    record.setCreatedAt(Instant.now());

    if (event.batchContext() != null) {
      record.setBatchId(event.batchContext().batchId());
      record.setBatchRowId(event.batchContext().batchRowId());
    }

    repository.save(record);
  }

  private String serializePayload(EventEnvelope<?> envelope) {
    try {
      return objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize event payload for history: {}", e.getMessage());
      return null;
    }
  }

  /** Internal event record queued for asynchronous persistence. */
  record HistoryEvent(
      String historyId,
      EventEnvelope<?> envelope,
      String topic,
      FlowRequest request,
      String status,
      long kafkaOffset,
      int kafkaPartition,
      @Nullable String chaosLabel,
      boolean intentionalFailure,
      @Nullable String errorMsg,
      @Nullable BatchContext batchContext) {}

  /** Batch context attached to batch-originated history events. */
  record BatchContext(String batchId, String batchRowId) {}
}
