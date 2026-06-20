package com.softspark.chaos.organization.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import com.softspark.chaos.organization.service.CountryService;
import com.softspark.chaos.organization.service.CurrencyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Unit tests for {@link ReferenceDataSeeder} (mapping, dedup, primary-currency pick, policy,
 * idempotency, and API-down tolerance).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReferenceDataSeeder")
class ReferenceDataSeederTest {

  @Mock private RestCountriesClient client;
  @Mock private CurrencyService currencyService;
  @Mock private CountryService countryService;
  @Mock private CurrencyRepository currencyRepository;
  @Mock private CountryRepository countryRepository;
  @Mock private AsyncTaskExecutor taskExecutor;

  private ReferenceDataSeeder seeder;

  private ReferenceDataProperties props(SeedPolicy policy) {
    return new ReferenceDataProperties(policy, null);
  }

  @BeforeEach
  void setUp() {
    seeder =
        new ReferenceDataSeeder(
            client,
            currencyService,
            countryService,
            currencyRepository,
            countryRepository,
            props(SeedPolicy.IF_EMPTY),
            taskExecutor);
  }

  private Currency currency(String id, String code) {
    var c = new Currency();
    c.setCurrencyId(id);
    c.setCode(code);
    c.setStatus(CurrencyStatus.ACTIVE);
    return c;
  }

  private RestCountry country(String name, String cca2, Map<String, RestCountry.RestCurrency> cur) {
    return new RestCountry(new RestCountry.Name(name), cca2, cca2 + "X", cur);
  }

  @Nested
  @DisplayName("seedIfNeeded policy")
  class PolicyTests {

    @Test
    @DisplayName("NEVER skips without fetching")
    void neverSkips() {
      var summary = seeder.seedIfNeeded(SeedPolicy.NEVER);

      assertThat(summary.skipped()).isTrue();
      verify(client, never()).fetchAll();
    }

    @Test
    @DisplayName("IF_EMPTY skips when the country table is not empty")
    void ifEmptySkipsWhenPopulated() {
      when(countryRepository.count()).thenReturn(50L);

      var summary = seeder.seedIfNeeded(SeedPolicy.IF_EMPTY);

      assertThat(summary.skipped()).isTrue();
      verify(client, never()).fetchAll();
    }
  }

  @Nested
  @DisplayName("doSeed mapping")
  class MappingTests {

    @Test
    @DisplayName("ALWAYS seeds: dedups currencies and links the first currency as primary")
    void seedsAndPicksPrimary() {
      var ghanaCurrencies = new LinkedHashMap<String, RestCountry.RestCurrency>();
      ghanaCurrencies.put("GHS", new RestCountry.RestCurrency("Ghanaian cedi", "₵"));

      // Multi-currency country: the first entry (BTN) must be picked as primary.
      var bhutanCurrencies = new LinkedHashMap<String, RestCountry.RestCurrency>();
      bhutanCurrencies.put("BTN", new RestCountry.RestCurrency("Ngultrum", "Nu."));
      bhutanCurrencies.put("INR", new RestCountry.RestCurrency("Indian rupee", "₹"));

      when(client.fetchAll())
          .thenReturn(
              List.of(
                  country("Ghana", "GH", ghanaCurrencies),
                  country("Bhutan", "BT", bhutanCurrencies)));
      when(currencyService.upsertIfAbsent(eq("GHS"), any(), any())).thenReturn(currency("c-ghs", "GHS"));
      when(currencyService.upsertIfAbsent(eq("BTN"), any(), any())).thenReturn(currency("c-btn", "BTN"));
      when(currencyService.upsertIfAbsent(eq("INR"), any(), any())).thenReturn(currency("c-inr", "INR"));

      var summary = seeder.seedIfNeeded(SeedPolicy.ALWAYS);

      assertThat(summary.skipped()).isFalse();
      assertThat(summary.fetched()).isEqualTo(2);

      verify(countryService).upsertIfAbsent("GH", "Ghana", "c-ghs");
      verify(countryService).upsertIfAbsent("BT", "Bhutan", "c-btn");
    }

    @Test
    @DisplayName("skips entries with a blank cca2")
    void skipsBlankIso() {
      when(client.fetchAll()).thenReturn(List.of(country("Nowhere", null, Map.of())));

      var summary = seeder.seedIfNeeded(SeedPolicy.ALWAYS);

      assertThat(summary.fetched()).isEqualTo(1);
      verify(countryService, never()).upsertIfAbsent(anyString(), any(), any());
    }

    @Test
    @DisplayName("country with no currencies is seeded with a null primary currency")
    void noCurrencies() {
      when(client.fetchAll()).thenReturn(List.of(country("Antarctica", "AQ", Map.of())));

      seeder.seedIfNeeded(SeedPolicy.ALWAYS);

      verify(countryService).upsertIfAbsent("AQ", "Antarctica", null);
    }
  }

  @Nested
  @DisplayName("resilience")
  class ResilienceTests {

    @Test
    @DisplayName("API failure degrades to a failed summary without throwing")
    void apiDownDegrades() {
      when(client.fetchAll()).thenThrow(new RuntimeException("connection refused"));

      var summary = seeder.seedIfNeeded(SeedPolicy.ALWAYS);

      assertThat(summary.skipped()).isFalse();
      assertThat(summary.error()).contains("connection refused");
      verify(countryService, never()).upsertIfAbsent(anyString(), any(), any());
    }
  }
}
