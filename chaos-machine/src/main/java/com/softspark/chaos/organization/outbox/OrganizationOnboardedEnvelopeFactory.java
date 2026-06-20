package com.softspark.chaos.organization.outbox;

import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.OrganizationOnboardedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import com.softspark.chaos.organization.model.Organization;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the {@code organization.onboarded} event envelope shape.
 *
 * <p>Assembles an {@link EventEnvelope} of {@link OrganizationOnboardedEventData} that matches the
 * authoritative {@code organization.onboarded} contract field-for-field: {@code source =
 * organization-service}, {@code version = 1.0}, snake_case {@code data} with nested {@code type
 * {id,name}} and {@code country {id,name,iso_code,status,modified_date}}, a {@code phone[]} array,
 * and {@code metadata {correlation_id, idempotency_key = "organization-onboarded:<event_id>",
 * tenant_id}}.
 */
@Component
public class OrganizationOnboardedEnvelopeFactory {

  /** Prefix for the idempotency key derived from the event identifier. */
  public static final String IDEMPOTENCY_KEY_PREFIX = "organization-onboarded:";

  private static final String SOURCE = "organization-service";
  private static final String VERSION = "1.0";

  private final TopicCatalog topicCatalog;

  /**
   * Constructs the factory.
   *
   * @param topicCatalog the catalog used to resolve the {@code organization.onboarded} topic
   */
  public OrganizationOnboardedEnvelopeFactory(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  /**
   * Builds the {@code organization.onboarded} envelope for the given organization.
   *
   * @param org           the persisted organization (snapshot columns are read as-is)
   * @param eventId       the stable event identifier (also drives the idempotency key)
   * @param timestamp     the event timestamp
   * @param correlationId the correlation identifier linking related events
   * @param tenantId      the owning tenant identifier
   * @return the fully-assembled event envelope
   */
  public EventEnvelope<OrganizationOnboardedEventData> build(
      Organization org, String eventId, Instant timestamp, String correlationId, String tenantId) {

    var type =
        new OrganizationOnboardedEventData.OrganizationType(
            org.getOrganizationTypeId(), org.getTypeName());

    var country =
        new OrganizationOnboardedEventData.Country(
            org.getCountryId(),
            org.getCountryName(),
            org.getCountryIsoCode(),
            org.getCountryStatus(),
            org.getCountryModifiedDate());

    var data =
        new OrganizationOnboardedEventData(
            org.getOrganizationId(),
            org.getName(),
            type,
            country,
            org.getPrimaryContactEmail(),
            org.getPhoneNumbers() != null ? org.getPhoneNumbers() : List.of(),
            org.getStatus() != null ? org.getStatus().name() : null);

    var metadata = new EventMetadata(correlationId, IDEMPOTENCY_KEY_PREFIX + eventId, tenantId);

    return new EventEnvelope<>(
        eventId,
        topicCatalog.topicFor(FlowType.ORGANIZATION_ONBOARDED),
        timestamp,
        SOURCE,
        VERSION,
        data,
        metadata);
  }
}
