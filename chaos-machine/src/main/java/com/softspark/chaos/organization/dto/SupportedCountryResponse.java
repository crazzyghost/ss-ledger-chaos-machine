package com.softspark.chaos.organization.dto;

import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

/**
 * Response record representing a supported-country membership with its resolved country.
 *
 * @param supportedCountryId the membership ID (UUID v4)
 * @param countryId          the referenced country ID
 * @param status             the membership status
 * @param country            the resolved country (name, iso_code, primary currency); null if the
 *                           referenced country was deleted
 * @param createdAt          the creation timestamp
 * @param updatedAt          the last update timestamp
 */
@RecordBuilder
public record SupportedCountryResponse(
    String supportedCountryId,
    String countryId,
    SupportedCountryStatus status,
    Country country,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Resolved country detail for display on the onboarding form.
   *
   * @param countryId       the country ID
   * @param name            the country name
   * @param isoCode         the ISO code
   * @param primaryCurrency the resolved primary currency (nullable)
   */
  @RecordBuilder
  public record Country(
      String countryId, String name, String isoCode, CurrencyRefResponse primaryCurrency) {}
}
