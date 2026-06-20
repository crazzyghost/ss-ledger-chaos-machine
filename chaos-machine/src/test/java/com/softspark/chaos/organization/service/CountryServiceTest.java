package com.softspark.chaos.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateCountryRequest;
import com.softspark.chaos.organization.dto.UpdateCountryRequest;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CountryService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CountryService")
class CountryServiceTest {

  @Mock private CountryRepository countryRepository;
  @Mock private CurrencyRepository currencyRepository;

  @InjectMocks private CountryService service;

  // ── Helpers ────────────────────────────────────────────────────────────────

  private Country buildCountry(String countryId, String isoCode) {
    var country = new Country();
    country.setCountryId(countryId);
    country.setName("Ghana");
    country.setIsoCode(isoCode);
    country.setStatus(CountryStatus.ACTIVE);
    country.setModifiedDate(Instant.now());
    return country;
  }

  // ── createCountry ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("createCountry")
  class CreateCountryTests {

    @Test
    @DisplayName("assigns a UUID id, sets modified_date, uppercases iso_code, defaults ACTIVE")
    void createAssignsDefaults() {
      var req = new CreateCountryRequest("Ghana", "gh", null, null, null);
      when(countryRepository.existsByIsoCode("GH")).thenReturn(false);
      when(countryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      var response = service.createCountry(req);

      ArgumentCaptor<Country> captor = ArgumentCaptor.forClass(Country.class);
      org.mockito.Mockito.verify(countryRepository).save(captor.capture());
      var persisted = captor.getValue();

      assertThat(persisted.getCountryId()).isNotBlank();
      assertThat(java.util.UUID.fromString(persisted.getCountryId())).isNotNull();
      assertThat(persisted.getIsoCode()).isEqualTo("GH");
      assertThat(persisted.getStatus()).isEqualTo(CountryStatus.ACTIVE);
      assertThat(persisted.getModifiedDate()).isNotNull();
      assertThat(response.isoCode()).isEqualTo("GH");
      assertThat(response.status()).isEqualTo(CountryStatus.ACTIVE);
    }

    @Test
    @DisplayName("honours a supplied modified_date and status")
    void createHonoursSuppliedValues() {
      var modified = Instant.parse("2020-01-01T00:00:00Z");
      var req = new CreateCountryRequest("Ghana", "gha", "INACTIVE", null, modified);
      when(countryRepository.existsByIsoCode("GHA")).thenReturn(false);
      when(countryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      var response = service.createCountry(req);

      assertThat(response.isoCode()).isEqualTo("GHA");
      assertThat(response.status()).isEqualTo(CountryStatus.INACTIVE);
      assertThat(response.modifiedDate()).isEqualTo(modified);
    }

    @Test
    @DisplayName("duplicate iso_code throws ConflictException")
    void duplicateIsoCodeThrowsConflict() {
      var req = new CreateCountryRequest("Ghana", "GH", null, null, null);
      when(countryRepository.existsByIsoCode("GH")).thenReturn(true);

      assertThatThrownBy(() -> service.createCountry(req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("GH");
    }
  }

  // ── getCountry ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getCountry")
  class GetCountryTests {

    @Test
    @DisplayName("throws NotFoundException when country does not exist")
    void throwsNotFoundWhenMissing() {
      when(countryRepository.findById(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getCountry("missing"))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }
  }

  // ── updateCountry ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("updateCountry")
  class UpdateCountryTests {

    @Test
    @DisplayName("throws NotFoundException when country does not exist")
    void throwsNotFoundWhenMissing() {
      when(countryRepository.findById(anyString())).thenReturn(Optional.empty());

      var req = new UpdateCountryRequest("Ghana", "GH", null, null, null);
      assertThatThrownBy(() -> service.updateCountry("missing", req))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("mutates name, iso_code, and status and uppercases iso_code")
    void updatesFields() {
      var existing = buildCountry("c-1", "GH");
      when(countryRepository.findById("c-1")).thenReturn(Optional.of(existing));
      when(countryRepository.findByIsoCode("GHA")).thenReturn(Optional.empty());
      when(countryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      var req = new UpdateCountryRequest("Republic of Ghana", "gha", "INACTIVE", null, null);
      var response = service.updateCountry("c-1", req);

      assertThat(response.name()).isEqualTo("Republic of Ghana");
      assertThat(response.isoCode()).isEqualTo("GHA");
      assertThat(response.status()).isEqualTo(CountryStatus.INACTIVE);
    }

    @Test
    @DisplayName("changing iso_code to one owned by another country throws ConflictException")
    void conflictingIsoCodeThrows() {
      var existing = buildCountry("c-1", "GH");
      var other = buildCountry("c-2", "NG");
      when(countryRepository.findById("c-1")).thenReturn(Optional.of(existing));
      when(countryRepository.findByIsoCode("NG")).thenReturn(Optional.of(other));

      var req = new UpdateCountryRequest("Ghana", "ng", null, null, null);
      assertThatThrownBy(() -> service.updateCountry("c-1", req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("NG");
    }

    @Test
    @DisplayName("keeping the same iso_code does not trigger a uniqueness check")
    void sameIsoCodeNoConflict() {
      var existing = buildCountry("c-1", "GH");
      when(countryRepository.findById("c-1")).thenReturn(Optional.of(existing));
      when(countryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      var req = new UpdateCountryRequest("Ghana Updated", "gh", "ACTIVE", null, null);
      var response = service.updateCountry("c-1", req);

      assertThat(response.isoCode()).isEqualTo("GH");
      assertThat(response.name()).isEqualTo("Ghana Updated");
      org.mockito.Mockito.verify(countryRepository, org.mockito.Mockito.never())
          .findByIsoCode(anyString());
    }
  }
}
