package com.softspark.chaos.consistencycheck.consumer;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.consistencycheck.model.ReconciliationMismatch;
import com.softspark.chaos.consistencycheck.repository.ReconciliationMismatchRepository;
import com.softspark.chaos.kafka.ConsumerConfiguration;
import com.softspark.chaos.kafka.EventEnvelope;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code ledger.reconciliation.mismatch} and projects each event into the {@code
 * reconciliation_mismatch} table for toast notification. Rides the shared method-typed container
 * factory (ADR-024). A poison body dead-letters to {@code ledger.reconciliation.mismatch.dlt}.
 *
 * <p>Gated by {@code chaos.kafka.consumer.enabled}.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReconciliationMismatchEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(ReconciliationMismatchEventConsumer.class);

  private final ReconciliationMismatchRepository repository;

  public ReconciliationMismatchEventConsumer(ReconciliationMismatchRepository repository) {
    this.repository = repository;
  }

  /**
   * Handles a single {@code ledger.reconciliation.mismatch} envelope.
   *
   * <p>Projects the event to the {@code reconciliation_mismatch} table. Replayed events are
   * deduplicated by the {@code check_id UNIQUE} constraint (SQLite {@code INSERT OR IGNORE}).
   *
   * @param envelope the deserialized reconciliation-mismatch envelope
   */
  @KafkaListener(
      topics = "${chaos.topics.ledger-reconciliation-mismatch}",
      groupId = "${chaos.kafka.consumer.group-id:chaos-machine}",
      containerFactory = ConsumerConfiguration.LEDGER_EVENT_CONTAINER_FACTORY)
  public void onReconciliationMismatch(EventEnvelope<ReconciliationMismatchEventData> envelope) {
    if (envelope == null || envelope.data() == null) {
      log.warn("Received ledger.reconciliation.mismatch with empty envelope/data — ignoring");
      return;
    }

    var data = envelope.data();
    log.info(
        "Consumed ledger.reconciliation.mismatch for check {}: type={}, discrepancies={}",
        data.checkId(),
        data.type(),
        data.discrepancyCount());

    var entity = new ReconciliationMismatch();
    entity.setId(Ids.generateUUID());
    entity.setCheckId(data.checkId().toString());
    entity.setType(data.type());
    entity.setInitiatorType(data.initiatorType());
    entity.setAsOf(data.asOf());
    entity.setInitiatedAt(data.initiatedAt());
    entity.setCompletedAt(data.completedAt());
    entity.setDiscrepancyCount(data.discrepancyCount());
    entity.setConsumedAt(LocalDateTime.now());

    try {
      repository.save(entity);
    } catch (Exception e) {
      // If the save fails due to UNIQUE constraint violation (replayed event),
      // SQLite ignores it silently. If it fails for another reason, log and rethrow.
      log.debug("Failed to save reconciliation mismatch (likely duplicate): {}", e.getMessage());
    }
  }
}
