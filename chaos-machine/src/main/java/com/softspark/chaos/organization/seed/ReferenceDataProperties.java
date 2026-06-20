package com.softspark.chaos.organization.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for reference-data seeding from an external country dataset.
 *
 * <p>Bound from the {@code chaos.reference-data.*} namespace. Defaults point at the keyless
 * <a href="https://github.com/mledoze/countries">mledoze/countries</a> GitHub dataset (the upstream
 * source restcountries.com is generated from), so seeding works out of the box. The base URL, path,
 * and an optional Bearer API key are configurable, so the seeder can instead target the official
 * (now key-gated) {@code api.restcountries.com/countries/v5} endpoint or a self-hosted mirror.
 *
 * @param seedOnStartup the seed policy applied at startup (default {@link SeedPolicy#IF_EMPTY})
 * @param restcountries the dataset client settings
 */
@ConfigurationProperties(prefix = "chaos.reference-data")
public record ReferenceDataProperties(SeedPolicy seedOnStartup, RestCountries restcountries) {

  /** Applies defaults for any unbound properties. */
  public ReferenceDataProperties {
    if (seedOnStartup == null) {
      seedOnStartup = SeedPolicy.IF_EMPTY;
    }
    if (restcountries == null) {
      restcountries = new RestCountries(null, null, null, null, 0, 0, 0, 0);
    }
  }

  /**
   * Country-dataset client settings.
   *
   * @param baseUrl          the API/dataset base URL (default {@code https://raw.githubusercontent.com})
   * @param path             the request path (default the mledoze/countries JSON file)
   * @param fields           optional {@code fields} query parameter (appended only when non-blank;
   *                         used by the official v5 API, ignored by the static GitHub mirror)
   * @param apiKey           optional Bearer API key (sent as {@code Authorization: Bearer <key>}
   *                         only when non-blank; required by {@code api.restcountries.com/countries/v5})
   * @param limit            optional {@code limit} query parameter (appended only when {@code > 0})
   * @param connectMs        connect timeout in milliseconds (default {@code 5000})
   * @param readMs           read timeout in milliseconds (default {@code 20000})
   * @param retryMaxAttempts maximum fetch attempts including the first (default {@code 3})
   */
  public record RestCountries(
      String baseUrl,
      String path,
      String fields,
      String apiKey,
      int limit,
      int connectMs,
      int readMs,
      int retryMaxAttempts) {

    /** Applies defaults for any unbound properties. */
    public RestCountries {
      if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "https://raw.githubusercontent.com";
      }
      if (path == null || path.isBlank()) {
        path = "/mledoze/countries/master/countries.json";
      }
      if (fields == null) {
        fields = "";
      }
      if (apiKey == null) {
        apiKey = "";
      }
      if (connectMs <= 0) {
        connectMs = 5000;
      }
      if (readMs <= 0) {
        readMs = 20000;
      }
      if (retryMaxAttempts <= 0) {
        retryMaxAttempts = 3;
      }
    }
  }
}
