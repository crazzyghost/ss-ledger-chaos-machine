package com.softspark.chaos.transaction.consumer;

import com.softspark.chaos.kafka.ConsumerConfiguration;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.transaction.service.TransactionFailureProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code ledger.transaction.failed} and projects each event into the {@code
 * transaction_failure} table (Phase 017 Task 002).
 *
 * <p>Rides the shared method-typed container factory ({@link
 * ConsumerConfiguration#LEDGER_EVENT_CONTAINER_FACTORY}, ADR-024): the {@link EventEnvelope}{@code
 * <LedgerTransactionFailedEventData>} parameter type drives deserialization, and a poison body
 * dead-letters to {@code ledger.transaction.failed.dlt}. Projection is delegated to a {@code
 * @Transactional} service so the idempotent upsert commits atomically.
 *
 * <p>Gated by {@code chaos.kafka.consumer.enabled}.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LedgerTransactionFailedConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerTransactionFailedConsumer.class);

  private final TransactionFailureProjectionService projectionService;

  public LedgerTransactionFailedConsumer(TransactionFailureProjectionService projectionService) {
    this.projectionService = projectionService;
  }

  /**
   * Handles a single {@code ledger.transaction.failed} envelope.
   *
   * @param envelope the deserialized failure envelope
   */
  @KafkaListener(
      topics = "${chaos.topics.ledger-transaction-failed}",
      groupId = "${chaos.kafka.consumer.group-id:chaos-machine}",
      containerFactory = ConsumerConfiguration.LEDGER_EVENT_CONTAINER_FACTORY)
  public void onLedgerTransactionFailed(EventEnvelope<LedgerTransactionFailedEventData> envelope) {
    if (envelope == null || envelope.data() == null) {
      log.warn("Received ledger.transaction.failed with empty envelope/data — ignoring");
      return;
    }
    log.debug(
        "Consuming ledger.transaction.failed event {} for request_id {}",
        envelope.eventId(),
        envelope.data().transactionRequestId());
    projectionService.project(envelope);
  }
}
