package com.softspark.chaos.organization.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.ChaosEventPublisher.PublishResult;
import com.softspark.chaos.kafka.EventPublishException;
import com.softspark.chaos.kafka.TopicCatalog;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import com.softspark.chaos.organization.model.Organization;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link OrganizationOutboxRelay} status-transition logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationOutboxRelay")
class OrganizationOutboxRelayTest {

  private static final int MAX_ATTEMPTS = 5;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Mock private OutboxEventRepository repository;
  @Mock private ChaosEventPublisher publisher;

  private OrganizationOutboxRelay relay;

  @BeforeEach
  void setUp() {
    var properties = new OutboxProperties(true, 1000L, 50, MAX_ATTEMPTS);
    relay = new OrganizationOutboxRelay(repository, publisher, objectMapper, properties);
  }

  private Organization buildOrganization() {
    var org = new Organization();
    org.setOrganizationId("org-1");
    org.setName("Acme Limited");
    org.setOrganizationTypeId("type-1");
    org.setTypeName("BUSINESS");
    org.setCountryId("country-1");
    org.setCountryName("Ghana");
    org.setCountryIsoCode("GH");
    org.setCountryStatus("ACTIVE");
    org.setCountryModifiedDate(Instant.parse("2024-01-02T03:04:05Z"));
    org.setPrimaryContactEmail("ops@acme.example");
    org.setPhoneNumbers(List.of("+233201234567"));
    org.setStatus(OrganizationStatus.ACTIVE);
    return org;
  }

  private OutboxEvent buildPendingRow(int attempts) throws Exception {
    var factory = new OrganizationOnboardedEnvelopeFactory(new TopicCatalog());
    var envelope =
        factory.build(buildOrganization(), "event-1", Instant.now(), "corr-1", "org_123");

    var row = new OutboxEvent();
    row.setOutboxId("outbox-1");
    row.setAggregateType("ORGANIZATION");
    row.setAggregateId("org-1");
    row.setEventId("event-1");
    row.setEventType(envelope.eventType());
    row.setPartitionKey("org-1");
    row.setPayloadJson(objectMapper.writeValueAsString(envelope));
    row.setStatus(OutboxStatus.PENDING);
    row.setAttempts(attempts);
    return row;
  }

  @Test
  @DisplayName("empty outbox: no publish and no save")
  void emptyOutboxNoOp() {
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of());

    relay.relay();

    verify(publisher, never()).publish(anyString(), anyString(), any());
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("publisher success flips row to PUBLISHED and sets publishedAt")
  void successMarksPublished() throws Exception {
    var row = buildPendingRow(0);
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(row));
    when(publisher.publish(eq("organization.onboarded"), eq("org-1"), any()))
        .thenReturn(new PublishResult(0L, 0));

    relay.relay();

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(row.getPublishedAt()).isNotNull();
    assertThat(row.getLastError()).isNull();
    verify(repository).save(row);
  }

  @Test
  @DisplayName("publisher failure below cap increments attempts and stays PENDING")
  void failureBelowCapStaysPending() throws Exception {
    var row = buildPendingRow(0);
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(row));
    when(publisher.publish(anyString(), anyString(), any()))
        .thenThrow(new EventPublishException("broker down", new RuntimeException()));

    relay.relay();

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLastError()).isEqualTo("broker down");
    verify(repository).save(row);
  }

  @Test
  @DisplayName("publisher failure at the attempt cap marks the row FAILED")
  void failureAtCapMarksFailed() throws Exception {
    var row = buildPendingRow(MAX_ATTEMPTS - 1);
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(row));
    when(publisher.publish(anyString(), anyString(), any()))
        .thenThrow(new EventPublishException("still down", new RuntimeException()));

    relay.relay();

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(row.getLastError()).isEqualTo("still down");
    verify(repository).save(row);
  }
}
