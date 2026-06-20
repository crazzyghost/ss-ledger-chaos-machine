package com.softspark.chaos.organization.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.organization.model.Organization;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Inserts {@code organization.onboarded} outbox rows within the caller's transaction.
 *
 * <p>Builds the contract envelope via {@link OrganizationOnboardedEnvelopeFactory}, serializes it
 * with the shared {@link ObjectMapper}, and persists a {@link OutboxStatus#PENDING} row keyed by the
 * organization identifier. Because it is invoked from within the onboarding transaction, the
 * organization row and its outbox row commit atomically.
 */
@Component
public class OutboxEnqueuer {

  private static final String AGGREGATE_TYPE = "ORGANIZATION";

  private final OutboxEventRepository repository;
  private final OrganizationOnboardedEnvelopeFactory envelopeFactory;
  private final ObjectMapper objectMapper;

  /**
   * Constructs the enqueuer.
   *
   * @param repository      the outbox repository
   * @param envelopeFactory the shared envelope factory
   * @param objectMapper    the application JSON mapper
   */
  public OutboxEnqueuer(
      OutboxEventRepository repository,
      OrganizationOnboardedEnvelopeFactory envelopeFactory,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.envelopeFactory = envelopeFactory;
    this.objectMapper = objectMapper;
  }

  /**
   * Enqueues a pending {@code organization.onboarded} outbox row for the given organization.
   *
   * @param org           the persisted organization
   * @param correlationId the correlation identifier to stamp on the envelope
   * @param tenantId      the owning tenant identifier
   * @return the generated, stable event identifier
   * @throws IllegalStateException if the envelope cannot be serialized
   */
  public String enqueueOrganizationOnboarded(
      Organization org, String correlationId, String tenantId) {
    String eventId = UUID.randomUUID().toString();
    Instant timestamp = Instant.now();

    var envelope = envelopeFactory.build(org, eventId, timestamp, correlationId, tenantId);

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize organization.onboarded envelope for org " + org.getOrganizationId(),
          e);
    }

    var row = new OutboxEvent();
    row.setOutboxId(UUID.randomUUID().toString());
    row.setAggregateType(AGGREGATE_TYPE);
    row.setAggregateId(org.getOrganizationId());
    row.setEventId(eventId);
    row.setEventType(envelope.eventType());
    row.setPartitionKey(org.getOrganizationId());
    row.setPayloadJson(payloadJson);
    row.setStatus(OutboxStatus.PENDING);
    row.setAttempts(0);

    repository.save(row);
    return eventId;
  }
}
