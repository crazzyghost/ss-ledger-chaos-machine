package com.softspark.chaos.organization.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Unit tests for {@link OrganizationOnboardedEnvelopeFactory}.
 */
@DisplayName("OrganizationOnboardedEnvelopeFactory")
class OrganizationOnboardedEnvelopeFactoryTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private OrganizationOnboardedEnvelopeFactory factory;

  @BeforeEach
  void setUp() {
    factory = new OrganizationOnboardedEnvelopeFactory(new TopicCatalog());
  }

  private Organization buildOrganization() {
    var org = new Organization();
    org.setOrganizationId("org-uuid-1");
    org.setName("Acme Limited");
    org.setOrganizationTypeId("type-uuid-1");
    org.setTypeName("BUSINESS");
    org.setCountryId("country-uuid-1");
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
  @DisplayName("produces the authoritative organization.onboarded contract shape")
  void producesContractShape() throws Exception {
    var envelope =
        factory.build(
            buildOrganization(),
            "event-uuid-1",
            Instant.parse("2024-01-02T03:04:05Z"),
            "corr-1",
            "org_123");

    JsonNode root = objectMapper.valueToTree(envelope);

    assertThat(root.get("event_id").asText()).isEqualTo("event-uuid-1");
    assertThat(root.get("event_type").asText()).isEqualTo("organization.onboarded");
    assertThat(root.get("source").asText()).isEqualTo("organization-service");
    assertThat(root.get("version").asText()).isEqualTo("1.0");

    JsonNode data = root.get("data");
    assertThat(data.get("id").asText()).isEqualTo("org-uuid-1");
    assertThat(data.get("name").asText()).isEqualTo("Acme Limited");
    assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(data.get("primary_contact_email").asText()).isEqualTo("ops@acme.example");
    assertThat(data.get("phone").isArray()).isTrue();
    assertThat(data.get("phone").get(0).asText()).isEqualTo("+233201234567");

    JsonNode type = data.get("type");
    assertThat(type.get("id").asText()).isEqualTo("type-uuid-1");
    assertThat(type.get("name").asText()).isEqualTo("BUSINESS");

    JsonNode country = data.get("country");
    assertThat(country.get("id").asText()).isEqualTo("country-uuid-1");
    assertThat(country.get("name").asText()).isEqualTo("Ghana");
    assertThat(country.get("iso_code").asText()).isEqualTo("GH");
    assertThat(country.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(country.hasNonNull("modified_date")).isTrue();

    JsonNode metadata = root.get("metadata");
    assertThat(metadata.get("correlation_id").asText()).isEqualTo("corr-1");
    assertThat(metadata.get("idempotency_key").asText())
        .isEqualTo("organization-onboarded:event-uuid-1");
    assertThat(metadata.get("idempotency_key").asText()).startsWith("organization-onboarded:");
    assertThat(metadata.get("tenant_id").asText()).isEqualTo("org_123");
  }
}
