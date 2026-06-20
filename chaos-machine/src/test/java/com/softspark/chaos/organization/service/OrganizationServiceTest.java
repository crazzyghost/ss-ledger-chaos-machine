package com.softspark.chaos.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateOrganizationRequest;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.model.Organization;
import com.softspark.chaos.organization.model.OrganizationType;
import com.softspark.chaos.organization.outbox.OutboxEnqueuer;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.OrganizationRepository;
import com.softspark.chaos.organization.repository.OrganizationTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link OrganizationService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService")
class OrganizationServiceTest {

  private static final String DEFAULT_TENANT_ID = "org_system";

  @Mock private OrganizationRepository organizationRepository;
  @Mock private CountryRepository countryRepository;
  @Mock private OrganizationTypeRepository organizationTypeRepository;
  @Mock private OutboxEnqueuer outboxEnqueuer;

  private OrganizationService service;

  @BeforeEach
  void setUp() {
    service =
        new OrganizationService(
            organizationRepository,
            countryRepository,
            organizationTypeRepository,
            outboxEnqueuer,
            DEFAULT_TENANT_ID);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private Country buildCountry() {
    var country = new Country();
    country.setCountryId("country-1");
    country.setName("Ghana");
    country.setIsoCode("GH");
    country.setStatus(CountryStatus.ACTIVE);
    country.setModifiedDate(Instant.parse("2024-01-01T00:00:00Z"));
    return country;
  }

  private OrganizationType buildType() {
    var type = new OrganizationType();
    type.setOrganizationTypeId("type-1");
    type.setName("Merchant");
    return type;
  }

  private CreateOrganizationRequest buildRequest() {
    return new CreateOrganizationRequest(
        "Acme Ltd", "type-1", "country-1", "ops@acme.test", List.of("+233200000000"), null);
  }

  // ── onboard ──────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("onboard")
  class OnboardTests {

    @Test
    @DisplayName("happy path assigns a UUID id and copies reference-data snapshots")
    void onboardCopiesSnapshots() {
      when(countryRepository.findById("country-1")).thenReturn(Optional.of(buildCountry()));
      when(organizationTypeRepository.findById("type-1")).thenReturn(Optional.of(buildType()));
      when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEnqueuer.enqueueOrganizationOnboarded(any(), anyString(), eq(DEFAULT_TENANT_ID)))
          .thenReturn("event-123");

      var response = service.onboard(buildRequest());

      ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
      verify(organizationRepository).save(captor.capture());
      var persisted = captor.getValue();

      assertThat(persisted.getOrganizationId()).isNotBlank();
      assertThat(UUID.fromString(persisted.getOrganizationId())).isNotNull();
      assertThat(persisted.getName()).isEqualTo("Acme Ltd");
      assertThat(persisted.getOrganizationTypeId()).isEqualTo("type-1");
      assertThat(persisted.getCountryId()).isEqualTo("country-1");
      assertThat(persisted.getTypeName()).isEqualTo("Merchant");
      assertThat(persisted.getCountryName()).isEqualTo("Ghana");
      assertThat(persisted.getCountryIsoCode()).isEqualTo("GH");
      assertThat(persisted.getCountryStatus()).isEqualTo("ACTIVE");
      assertThat(persisted.getCountryModifiedDate())
          .isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
      assertThat(persisted.getPrimaryContactEmail()).isEqualTo("ops@acme.test");
      assertThat(persisted.getPhoneNumbers()).containsExactly("+233200000000");
      assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

      assertThat(response.organizationId()).isEqualTo(persisted.getOrganizationId());
      assertThat(response.phoneNumbers()).containsExactly("+233200000000");
      assertThat(response.status()).isEqualTo(OrganizationStatus.ACTIVE);
      assertThat(response.eventId()).isEqualTo("event-123");

      verify(outboxEnqueuer, times(1))
          .enqueueOrganizationOnboarded(eq(persisted), anyString(), eq(DEFAULT_TENANT_ID));
    }

    @Test
    @DisplayName("status defaults to ACTIVE when omitted")
    void statusDefaultsToActive() {
      when(countryRepository.findById("country-1")).thenReturn(Optional.of(buildCountry()));
      when(organizationTypeRepository.findById("type-1")).thenReturn(Optional.of(buildType()));
      when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEnqueuer.enqueueOrganizationOnboarded(any(), anyString(), eq(DEFAULT_TENANT_ID)))
          .thenReturn("event-456");

      var request =
          new CreateOrganizationRequest("Acme Ltd", "type-1", "country-1", null, null, null);
      var response = service.onboard(request);

      assertThat(response.status()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    @DisplayName("unknown country id throws NotFoundException and never saves")
    void unknownCountryThrows() {
      when(countryRepository.findById("country-1")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.onboard(buildRequest()))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("country-1");

      verify(organizationRepository, never()).save(any());
      verify(outboxEnqueuer, never()).enqueueOrganizationOnboarded(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("unknown organization type id throws NotFoundException and never saves")
    void unknownTypeThrows() {
      when(countryRepository.findById("country-1")).thenReturn(Optional.of(buildCountry()));
      when(organizationTypeRepository.findById("type-1")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.onboard(buildRequest()))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("type-1");

      verify(organizationRepository, never()).save(any());
      verify(outboxEnqueuer, never()).enqueueOrganizationOnboarded(any(), anyString(), anyString());
    }
  }

  // ── getOrganization ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getOrganization")
  class GetOrganizationTests {

    @Test
    @DisplayName("throws NotFoundException when the organization does not exist")
    void throwsNotFoundWhenMissing() {
      when(organizationRepository.findById(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getOrganization("missing"))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }
  }

  // ── listOrganizations ────────────────────────────────────────────────────────

  @Nested
  @DisplayName("listOrganizations")
  class ListOrganizationsTests {

    @Test
    @DisplayName("returns a PageResponse of mapped organizations")
    void returnsPageResponse() {
      var organization = new Organization();
      organization.setOrganizationId("org-1");
      organization.setName("Acme Ltd");
      organization.setStatus(OrganizationStatus.ACTIVE);
      Page<Organization> page = new PageImpl<>(List.of(organization), PageRequest.of(0, 20), 1);
      when(organizationRepository.findAll(any(PageRequest.class))).thenReturn(page);

      var result = service.listOrganizations(0, 20);

      assertThat(result.total()).isEqualTo(1);
      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().organizationId()).isEqualTo("org-1");
    }
  }
}
