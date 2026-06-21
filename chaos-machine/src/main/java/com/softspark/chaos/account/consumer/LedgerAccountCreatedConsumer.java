package com.softspark.chaos.account.consumer;

import com.softspark.chaos.account.service.VirtualAccountProjectionService;
import com.softspark.chaos.kafka.ConsumerConfiguration;
import com.softspark.chaos.kafka.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code ledger.account.created} and projects each event into the VA registry.
 *
 * <p>This is the chaos machine's first Kafka listener. It uses the Phase 009 / Task 001 container
 * factory ({@link ConsumerConfiguration#LEDGER_EVENT_CONTAINER_FACTORY}) which deserializes the
 * ledger {@link EventEnvelope}{@code <LedgerAccountCreatedEventData>} (snake_case, no type headers),
 * retries transient failures, and dead-letters poison records to {@code
 * ledger.account.created.dlt}. Projection itself is delegated to a {@code @Transactional} service so
 * the upsert + role link commit atomically.
 *
 * <p>The bean is gated by {@code chaos.kafka.consumer.enabled}; when disabled (or absent) no
 * listener container starts.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LedgerAccountCreatedConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerAccountCreatedConsumer.class);

  private final VirtualAccountProjectionService projectionService;

  public LedgerAccountCreatedConsumer(VirtualAccountProjectionService projectionService) {
    this.projectionService = projectionService;
  }

  /**
   * Handles a single {@code ledger.account.created} envelope.
   *
   * <p>Any exception thrown here is handled by the container's {@link
   * org.springframework.kafka.listener.DefaultErrorHandler} (retry then DLT); the offset is only
   * committed on successful return.
   *
   * @param envelope the deserialized ledger event envelope
   */
  @KafkaListener(
      topics = "${chaos.topics.ledger-account-created}",
      groupId = "${chaos.kafka.consumer.group-id:chaos-machine}",
      containerFactory = ConsumerConfiguration.LEDGER_EVENT_CONTAINER_FACTORY)
  public void onLedgerAccountCreated(EventEnvelope<LedgerAccountCreatedEventData> envelope) {
    if (envelope == null || envelope.data() == null) {
      log.warn("Received ledger.account.created with empty envelope/data — ignoring");
      return;
    }
    log.debug(
        "Consuming ledger.account.created event {} for account {}",
        envelope.eventId(),
        envelope.data().accountId());
    projectionService.project(envelope.data());
  }
}
