package com.softspark.chaos.organization.outbox;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.flow.model.v1.OrganizationOnboardedEventData;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled relay that publishes {@link OutboxStatus#PENDING} organization onboarding events.
 *
 * <p>On each tick it claims up to {@code chaos.organization.outbox.batch-size} pending rows in
 * creation order, deserializes each stored envelope, and publishes via {@link ChaosEventPublisher}.
 * Success flips the row to {@link OutboxStatus#PUBLISHED}; failure increments the attempt counter,
 * records the error, and leaves the row {@link OutboxStatus#PENDING} for retry until the configured
 * attempt cap, at which point the row becomes {@link OutboxStatus#FAILED}. The stored {@code
 * event_id}/idempotency key in the payload is never regenerated, preserving at-least-once delivery
 * with downstream deduplication.
 *
 * <p>This relay assumes a single instance (the harness is single-node, SQLite single-writer); no
 * distributed claim is required.
 */
@Component
@ConditionalOnProperty(
    name = "chaos.organization.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OrganizationOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OrganizationOutboxRelay.class);

  private final OutboxEventRepository repository;
  private final ChaosEventPublisher publisher;
  private final ObjectMapper objectMapper;
  private final OutboxProperties properties;
  private final JavaType envelopeType;

  /**
   * Constructs the relay.
   *
   * @param repository   the outbox repository
   * @param publisher    the Kafka publisher
   * @param objectMapper the application JSON mapper
   * @param properties   the outbox configuration
   */
  public OrganizationOutboxRelay(
      OutboxEventRepository repository,
      ChaosEventPublisher publisher,
      ObjectMapper objectMapper,
      OutboxProperties properties) {
    this.repository = repository;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.envelopeType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(EventEnvelope.class, OrganizationOnboardedEventData.class);
  }

  /**
   * Relay tick: claims and publishes pending outbox rows.
   *
   * <p>The entire body is guarded so a transient fault never terminates the scheduler thread; each
   * row is additionally isolated so one poison row does not block its siblings.
   */
  @Scheduled(
      fixedDelayString = "${chaos.organization.outbox.poll-interval-ms:1000}",
      initialDelayString = "${chaos.organization.outbox.poll-interval-ms:1000}")
  public void relay() {
    try {
      List<OutboxEvent> pending =
          repository.findByStatusOrderByCreatedAtAsc(
              OutboxStatus.PENDING, PageRequest.of(0, properties.batchSize()));

      if (pending.isEmpty()) {
        return;
      }

      log.debug("Outbox relay tick: {} pending row(s)", pending.size());
      for (OutboxEvent row : pending) {
        publishRow(row);
      }
    } catch (Exception e) {
      log.error("Outbox relay tick failed: {}", e.getMessage(), e);
    }
  }

  private void publishRow(OutboxEvent row) {
    try {
      EventEnvelope<OrganizationOnboardedEventData> envelope =
          objectMapper.readValue(row.getPayloadJson(), envelopeType);

      publisher.publish(row.getEventType(), row.getPartitionKey(), envelope);

      row.setStatus(OutboxStatus.PUBLISHED);
      row.setPublishedAt(Instant.now());
      row.setLastError(null);
      repository.save(row);
      log.info("Published outbox event {} ({})", row.getEventId(), row.getOutboxId());
    } catch (Exception e) {
      int attempts = row.getAttempts() + 1;
      row.setAttempts(attempts);
      row.setLastError(e.getMessage());
      if (attempts >= properties.maxAttempts()) {
        row.setStatus(OutboxStatus.FAILED);
        log.error(
            "Outbox event {} failed permanently after {} attempt(s): {}",
            row.getEventId(),
            attempts,
            e.getMessage());
      } else {
        log.warn(
            "Outbox event {} publish attempt {} failed, will retry: {}",
            row.getEventId(),
            attempts,
            e.getMessage());
      }
      repository.save(row);
    }
  }
}
