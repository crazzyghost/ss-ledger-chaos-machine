package com.softspark.chaos.reservation.consumer;

import com.softspark.chaos.kafka.ConsumerConfiguration;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.reservation.service.ReservationProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes <strong>both</strong> {@code ledger.reservation.created} and {@code
 * ledger.reservation.released} on the shared method-typed container factory (ADR-024) and projects
 * them into the stateful {@code reservation} table (Phase 019 Task 001). One mirror record backs
 * both topics; {@code created} vs {@code released} is derived from the payload {@code status}
 * (ACTIVE = created). A poison body dead-letters to {@code ledger.reservation.{created,released}.dlt}.
 *
 * <p>Gated by {@code chaos.kafka.consumer.enabled}.
 */
@Component
@ConditionalOnProperty(
    prefix = "chaos.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LedgerReservationConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerReservationConsumer.class);

  private final ReservationProjectionService projectionService;

  public LedgerReservationConsumer(ReservationProjectionService projectionService) {
    this.projectionService = projectionService;
  }

  /**
   * Handles a single reservation lifecycle envelope from either reservation topic.
   *
   * @param envelope the deserialized reservation lifecycle envelope
   */
  @KafkaListener(
      topics = {
        "${chaos.topics.ledger-reservation-created}",
        "${chaos.topics.ledger-reservation-released}"
      },
      groupId = "${chaos.kafka.consumer.group-id:chaos-machine}",
      containerFactory = ConsumerConfiguration.LEDGER_EVENT_CONTAINER_FACTORY)
  public void onReservationEvent(EventEnvelope<LedgerReservationLifecycleEventData> envelope) {
    if (envelope == null || envelope.data() == null) {
      log.warn("Received reservation event with empty envelope/data — ignoring");
      return;
    }
    log.debug(
        "Consuming reservation event {} reservation={} status={}",
        envelope.eventId(),
        envelope.data().reservationId(),
        envelope.data().status());
    projectionService.project(envelope);
  }
}
