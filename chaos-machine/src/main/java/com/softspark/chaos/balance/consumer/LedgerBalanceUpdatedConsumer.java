package com.softspark.chaos.balance.consumer;

import com.softspark.chaos.balance.service.BalanceHistoryProjectionService;
import com.softspark.chaos.kafka.ConsumerConfiguration;
import com.softspark.chaos.kafka.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code ledger.balance.updated} and projects each event into the {@code balance_history}
 * table (Phase 018 Task 001), riding the shared method-typed container factory (ADR-024). A poison
 * body dead-letters to {@code ledger.balance.updated.dlt}.
 *
 * <p>Gated by {@code chaos.kafka.consumer.enabled}.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LedgerBalanceUpdatedConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerBalanceUpdatedConsumer.class);

  private final BalanceHistoryProjectionService projectionService;

  public LedgerBalanceUpdatedConsumer(BalanceHistoryProjectionService projectionService) {
    this.projectionService = projectionService;
  }

  /**
   * Handles a single {@code ledger.balance.updated} envelope.
   *
   * @param envelope the deserialized balance-updated envelope
   */
  @KafkaListener(
      topics = "${chaos.topics.ledger-balance-updated}",
      groupId = "${chaos.kafka.consumer.group-id:chaos-machine}",
      containerFactory = ConsumerConfiguration.LEDGER_EVENT_CONTAINER_FACTORY)
  public void onLedgerBalanceUpdated(EventEnvelope<LedgerBalanceUpdatedEventData> envelope) {
    if (envelope == null || envelope.data() == null) {
      log.warn("Received ledger.balance.updated with empty envelope/data — ignoring");
      return;
    }
    log.debug(
        "Consuming ledger.balance.updated event {} for account {}",
        envelope.eventId(),
        envelope.data().accountId());
    projectionService.project(envelope);
  }
}
