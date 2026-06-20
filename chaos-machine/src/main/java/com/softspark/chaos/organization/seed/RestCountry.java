package com.softspark.chaos.organization.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Partial mapping of a restcountries.com {@code v3.1} country entry.
 *
 * <p>Only the fields requested via {@code ?fields=name,cca2,cca3,currencies} are mapped; the API is
 * schema-tolerant so unknown properties are ignored. The {@code currencies} map is keyed by ISO-4217
 * code (e.g. {@code "GHS"}) and its iteration order (a {@link LinkedHashMap}) is preserved so the
 * first entry can be taken as the country's primary currency.
 *
 * @param name       the country name block ({@code name.common})
 * @param cca2       the ISO 3166-1 alpha-2 code (e.g. {@code GH})
 * @param cca3       the ISO 3166-1 alpha-3 code (e.g. {@code GHA})
 * @param currencies map of ISO-4217 code to currency detail (may be null/empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestCountry(
    Name name, String cca2, String cca3, Map<String, RestCurrency> currencies) {

  /**
   * The {@code name} block of a restcountries.com entry.
   *
   * @param common the common country name
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Name(String common) {}

  /**
   * A currency detail value in the {@code currencies} map.
   *
   * @param name   the currency name
   * @param symbol the currency symbol (may be null)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RestCurrency(String name, String symbol) {}
}
