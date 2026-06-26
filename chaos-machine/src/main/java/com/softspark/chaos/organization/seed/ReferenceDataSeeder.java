package com.softspark.chaos.organization.seed;

import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import com.softspark.chaos.organization.service.CountryService;
import com.softspark.chaos.organization.service.CurrencyService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Seeds {@code currency} and {@code country} reference data from restcountries.com at startup.
 *
 * <p>Runs as an {@link ApplicationRunner} (after Flyway) but performs the actual fetch + upsert on a
 * virtual thread, so a slow or unreachable API never blocks or fails boot. Seeding is idempotent and
 * seed-if-absent by natural key ({@code code} / {@code iso_code}); operator edits are never
 * clobbered. The {@code chaos.reference-data.seed-on-startup} policy controls when it runs and the
 * {@code POST /api/v0/countries/refresh} endpoint forces a re-seed via {@link #refresh()}.
 */
@Component
@Order
public class ReferenceDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

  private final RestCountriesClient client;
  private final CurrencyService currencyService;
  private final CountryService countryService;
  private final CurrencyRepository currencyRepository;
  private final CountryRepository countryRepository;
  private final ReferenceDataProperties properties;
  private final AsyncTaskExecutor taskExecutor;

  public ReferenceDataSeeder(
      RestCountriesClient client,
      CurrencyService currencyService,
      CountryService countryService,
      CurrencyRepository currencyRepository,
      CountryRepository countryRepository,
      ReferenceDataProperties properties,
      AsyncTaskExecutor applicationTaskExecutor) {
    this.client = client;
    this.currencyService = currencyService;
    this.countryService = countryService;
    this.currencyRepository = currencyRepository;
    this.countryRepository = countryRepository;
    this.properties = properties;
    this.taskExecutor = applicationTaskExecutor;
  }

  /**
   * Schedules seeding off the boot thread. Never throws — boot is never blocked or broken by the
   * external API.
   *
   * @param args the application arguments (unused)
   */
  @Override
  public void run(ApplicationArguments args) {
    SeedPolicy policy = properties.seedOnStartup();
    log.info("Reference-data seeding scheduled (policy={})", policy);
    taskExecutor.execute(
        () -> {
          try {
            var summary = seedIfNeeded(policy);
            log.info("Reference-data seeding finished: {}", summary);
          } catch (RuntimeException e) {
            // Defensive: doSeed already swallows fetch errors; this guards everything else.
            log.error("Reference-data seeding failed unexpectedly", e);
          }
        });
  }

  /**
   * Seeds reference data when the policy and table state call for it.
   *
   * @param policy the seed policy
   * @return a summary describing the outcome
   */
  public SeedSummary seedIfNeeded(SeedPolicy policy) {
    if (policy == SeedPolicy.NEVER) {
      log.debug("Reference-data seeding skipped (policy=NEVER)");
      return SeedSummary.ofSkipped();
    }
    if (policy == SeedPolicy.IF_EMPTY && countryRepository.count() > 0) {
      log.debug("Reference-data seeding skipped (policy=IF_EMPTY, country table not empty)");
      return SeedSummary.ofSkipped();
    }
    return doSeed();
  }

  /**
   * Forces a re-seed regardless of policy (used by the manual refresh endpoint).
   *
   * @return a summary describing the outcome
   */
  public SeedSummary refresh() {
    log.info("Reference-data refresh requested");
    return doSeed();
  }

  private SeedSummary doSeed() {
    List<RestCountry> countries;
    try {
      countries = client.fetchAll();
    } catch (RuntimeException e) {
      log.error("restcountries.com unreachable; reference data not seeded this run", e);
      return SeedSummary.ofFailed(e.getMessage());
    }

    long currenciesBefore = currencyRepository.count();
    long countriesBefore = countryRepository.count();

    for (RestCountry country : countries) {
      String iso = country.cca2();
      if (iso == null || iso.isBlank()) {
        continue;
      }

      String primaryCurrencyId = upsertCurrenciesAndResolvePrimary(country.currencies());
      String name = country.name() != null ? country.name().common() : null;
      countryService.upsertIfAbsent(iso, name, primaryCurrencyId);
    }

    int currenciesUpserted = (int) (currencyRepository.count() - currenciesBefore);
    int countriesUpserted = (int) (countryRepository.count() - countriesBefore);

    return new SeedSummary(countries.size(), currenciesUpserted, countriesUpserted, false, null);
  }

  /**
   * Upserts every currency in a country's {@code currencies} map (dedup by code) and returns the
   * currency ID of the <em>first</em> entry — the country's primary currency.
   *
   * @param currencies the restcountries.com {@code currencies} map (may be null/empty)
   * @return the primary currency ID, or {@code null} when the country has no currencies
   */
  private String upsertCurrenciesAndResolvePrimary(
      Map<String, RestCountry.RestCurrency> currencies) {
    if (currencies == null || currencies.isEmpty()) {
      return null;
    }
    String primaryCurrencyId = null;
    for (Map.Entry<String, RestCountry.RestCurrency> entry : currencies.entrySet()) {
      String code = entry.getKey();
      if (code == null || code.isBlank()) {
        continue;
      }
      RestCountry.RestCurrency detail = entry.getValue();
      String currencyName = detail != null ? detail.name() : null;
      String symbol = detail != null ? detail.symbol() : null;
      Currency currency = currencyService.upsertIfAbsent(code, currencyName, symbol);
      if (primaryCurrencyId == null) {
        primaryCurrencyId = currency.getCurrencyId();
      }
    }
    return primaryCurrencyId;
  }
}
