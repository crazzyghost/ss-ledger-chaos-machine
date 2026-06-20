package com.softspark.chaos.organization.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.kafka.TopicCatalog;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import com.softspark.chaos.organization.model.Organization;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxEnqueuer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEnqueuer")
class OutboxEnqueuerTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Mock private OutboxEventRepository repository;

  private OutboxEnqueuer enqueuer;

  @BeforeEach
  void setUp() {
    var factory = new OrganizationOnboardedEnvelopeFactory(new TopicCatalog());
    enqueuer = new OutboxEnqueuer(repository, factory, objectMapper);
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

  @Test
  @DisplayName("saves a PENDING outbox row and returns the generated event id")
  void savesPendingRow() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    String eventId =
        enqueuer.enqueueOrganizationOnboarded(buildOrganization(), "corr-1", "org_123");

    assertThat(eventId).isNotBlank();

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(captor.capture());
    OutboxEvent saved = captor.getValue();

    assertThat(saved.getOutboxId()).isNotBlank();
    assertThat(saved.getAggregateType()).isEqualTo("ORGANIZATION");
    assertThat(saved.getAggregateId()).isEqualTo("org-1");
    assertThat(saved.getPartitionKey()).isEqualTo("org-1");
    assertThat(saved.getEventId()).isEqualTo(eventId);
    assertThat(saved.getEventType()).isEqualTo("organization.onboarded");
    assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(saved.getAttempts()).isZero();
    assertThat(saved.getPayloadJson()).isNotNull();
    assertThat(saved.getPayloadJson()).contains("organization-onboarded:" + eventId);
  }
}
