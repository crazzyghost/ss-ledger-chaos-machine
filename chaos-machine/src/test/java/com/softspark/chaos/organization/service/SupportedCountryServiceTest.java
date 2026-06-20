package com.softspark.chaos.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateSupportedCountryRequest;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.model.SupportedCountry;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import com.softspark.chaos.organization.repository.SupportedCountryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SupportedCountryService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SupportedCountryService")
class SupportedCountryServiceTest {

  @Mock private SupportedCountryRepository supportedCountryRepository;
  @Mock private CountryRepository countryRepository;
  @Mock private CurrencyRepository currencyRepository;

  @InjectMocks private SupportedCountryService service;

  private Country buildCountry() {
    var country = new Country();
    country.setCountryId("country-1");
    country.setName("Ghana");
    country.setIsoCode("GH");
    country.setStatus(CountryStatus.ACTIVE);
    country.setPrimaryCurrencyId("cur-1");
    return country;
  }

  private Currency buildCurrency() {
    var currency = new Currency();
    currency.setCurrencyId("cur-1");
    currency.setCode("GHS");
    currency.setName("Ghanaian cedi");
    currency.setStatus(CurrencyStatus.ACTIVE);
    return currency;
  }

  @Nested
  @DisplayName("createSupportedCountry")
  class CreateTests {

    @Test
    @DisplayName("creates a UUID-keyed membership and resolves the country + primary currency")
    void createsAndResolves() {
      var req = new CreateSupportedCountryRequest("country-1", null);
      when(countryRepository.findById("country-1")).thenReturn(Optional.of(buildCountry()));
      when(supportedCountryRepository.existsByCountryId("country-1")).thenReturn(false);
      when(currencyRepository.findById("cur-1")).thenReturn(Optional.of(buildCurrency()));
      when(supportedCountryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      var response = service.createSupportedCountry(req);

      ArgumentCaptor<SupportedCountry> captor = ArgumentCaptor.forClass(SupportedCountry.class);
      verify(supportedCountryRepository).save(captor.capture());
      assertThat(UUID.fromString(captor.getValue().getSupportedCountryId())).isNotNull();
      assertThat(captor.getValue().getStatus()).isEqualTo(SupportedCountryStatus.ACTIVE);

      assertThat(response.countryId()).isEqualTo("country-1");
      assertThat(response.country()).isNotNull();
      assertThat(response.country().name()).isEqualTo("Ghana");
      assertThat(response.country().primaryCurrency()).isNotNull();
      assertThat(response.country().primaryCurrency().code()).isEqualTo("GHS");
    }

    @Test
    @DisplayName("unknown country throws NotFoundException")
    void unknownCountryThrows() {
      var req = new CreateSupportedCountryRequest("missing", null);
      when(countryRepository.findById("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.createSupportedCountry(req))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");

      verify(supportedCountryRepository, never()).save(any());
    }

    @Test
    @DisplayName("already-supported country throws ConflictException")
    void duplicateThrows() {
      var req = new CreateSupportedCountryRequest("country-1", null);
      when(countryRepository.findById("country-1")).thenReturn(Optional.of(buildCountry()));
      when(supportedCountryRepository.existsByCountryId("country-1")).thenReturn(true);

      assertThatThrownBy(() -> service.createSupportedCountry(req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("already supported");

      verify(supportedCountryRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("listSupportedCountries")
  class ListTests {

    private SupportedCountry supported(String id, String countryId) {
      var s = new SupportedCountry();
      s.setSupportedCountryId(id);
      s.setCountryId(countryId);
      s.setStatus(SupportedCountryStatus.ACTIVE);
      return s;
    }

    private Country country(String id, String name, String iso) {
      var c = new Country();
      c.setCountryId(id);
      c.setName(name);
      c.setIsoCode(iso);
      return c;
    }

    @Test
    @DisplayName("filters by country name and sorts the matches by country name")
    void filtersAndSorts() {
      when(supportedCountryRepository.findAll())
          .thenReturn(
              java.util.List.of(
                  supported("s-1", "gh"), supported("s-2", "kr"), supported("s-3", "za")));
      when(countryRepository.findAllById(any()))
          .thenReturn(
              java.util.List.of(
                  country("gh", "Ghana", "GH"),
                  country("kr", "South Korea", "KR"),
                  country("za", "South Africa", "ZA")));
      when(currencyRepository.findAllById(any())).thenReturn(java.util.List.of());

      // "south" matches the two South* names only; sorted asc → Africa before Korea.
      var result = service.listSupportedCountries(0, 20, "south", "country", "asc");

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).country().name()).isEqualTo("South Africa");
      assertThat(result.items().get(1).country().name()).isEqualTo("South Korea");
      assertThat(result.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("paginates the resolved list in memory")
    void paginates() {
      when(supportedCountryRepository.findAll())
          .thenReturn(
              java.util.List.of(
                  supported("s-1", "gh"), supported("s-2", "ng"), supported("s-3", "ke")));
      when(countryRepository.findAllById(any()))
          .thenReturn(
              java.util.List.of(
                  country("gh", "Ghana", "GH"),
                  country("ng", "Nigeria", "NG"),
                  country("ke", "Kenya", "KE")));
      when(currencyRepository.findAllById(any())).thenReturn(java.util.List.of());

      var pageOne = service.listSupportedCountries(0, 2, null, "country", "asc");
      assertThat(pageOne.items()).hasSize(2);
      assertThat(pageOne.total()).isEqualTo(3);
      assertThat(pageOne.items().get(0).country().name()).isEqualTo("Ghana");

      var pageTwo = service.listSupportedCountries(1, 2, null, "country", "asc");
      assertThat(pageTwo.items()).hasSize(1);
      assertThat(pageTwo.items().get(0).country().name()).isEqualTo("Nigeria");
    }
  }

  @Nested
  @DisplayName("deleteSupportedCountry")
  class DeleteTests {

    @Test
    @DisplayName("throws NotFoundException when the membership is missing")
    void throwsNotFoundWhenMissing() {
      when(supportedCountryRepository.findById("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteSupportedCountry("missing"))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("deletes an existing membership")
    void deletesExisting() {
      var supported = new SupportedCountry();
      supported.setSupportedCountryId("sc-1");
      supported.setCountryId("country-1");
      supported.setStatus(SupportedCountryStatus.ACTIVE);
      when(supportedCountryRepository.findById("sc-1")).thenReturn(Optional.of(supported));

      service.deleteSupportedCountry("sc-1");

      verify(supportedCountryRepository).delete(supported);
    }
  }
}
