package com.softspark.chaos.organization.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for the external country dataset (keyless mledoze/countries GitHub JSON by default).
 *
 * <p>Performs {@code GET <base-url><path>} against the dedicated {@link RestClient} bean and
 * deserializes the response into {@link RestCountry} records. The body is read as a raw string and
 * parsed with the shared {@link ObjectMapper} rather than relying on a content-type-driven message
 * converter: GitHub raw, restcountries.com, and intermediary proxies/CDNs do not always serve JSON
 * with an {@code application/json} content type, which would otherwise trip Spring's converter
 * selection. When a {@code fields}/{@code limit}/{@code apiKey} are configured (for the official
 * {@code api.restcountries.com/countries/v5} endpoint) they are applied; otherwise they are omitted.
 * A genuinely non-JSON body (e.g. an HTML error page) fails to parse and is surfaced as an exception
 * for the caller (the {@link ReferenceDataSeeder}) to catch, meter, and degrade — failures never
 * propagate to application boot. A small bounded retry smooths transient errors.
 */
@Component
public class RestCountriesClient {

  private static final Logger log = LoggerFactory.getLogger(RestCountriesClient.class);
  private static final TypeReference<List<RestCountry>> COUNTRY_LIST = new TypeReference<>() {};

  private final RestClient restClient;
  private final ReferenceDataProperties properties;
  private final ObjectMapper objectMapper;

  public RestCountriesClient(
      @Qualifier("restCountriesRestClient") RestClient restClient,
      ReferenceDataProperties properties,
      ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * Fetches the full country list with the required {@code fields} selector, retrying a bounded
   * number of times on failure.
   *
   * @return the country list returned by the API
   * @throws RuntimeException if every attempt fails (caught by the seeder)
   */
  public List<RestCountry> fetchAll() {
    var rc = properties.restcountries();
    String path = rc.path();
    String fields = rc.fields();
    int limit = rc.limit();
    String apiKey = rc.apiKey();
    int maxAttempts = rc.retryMaxAttempts();

    RuntimeException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        String body =
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      uriBuilder.path(path);
                      if (fields != null && !fields.isBlank()) {
                        uriBuilder.queryParam("fields", fields);
                      }
                      if (limit > 0) {
                        uriBuilder.queryParam("limit", limit);
                      }
                      return uriBuilder.build();
                    })
                .accept(MediaType.APPLICATION_JSON)
                .headers(
                    headers -> {
                      if (apiKey != null && !apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey);
                      }
                    })
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
          return List.of();
        }
        return objectMapper.readValue(body, COUNTRY_LIST);
      } catch (Exception e) {
        last = e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        log.warn(
            "Country dataset fetch attempt {}/{} failed: {}", attempt, maxAttempts, e.toString());
      }
    }
    throw last != null ? last : new IllegalStateException("country dataset fetch failed");
  }
}
