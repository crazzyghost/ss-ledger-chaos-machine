package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import java.util.List;

/**
 * Event data for organization.onboarded events.
 * <p>
 * Published when a new organization is onboarded to the system.
 *
 * @param id                  the organization ID
 * @param name                the organization name
 * @param type                the organization type
 * @param country             the country information
 * @param primaryContactEmail the primary contact email
 * @param phone               the phone numbers
 * @param status              the organization status
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrganizationOnboardedEventData(
    String id,
    String name,
    OrganizationType type,
    Country country,
    @JsonProperty("primary_contact_email") String primaryContactEmail,
    List<String> phone,
    String status) {

  /**
   * Organization type information.
   *
   * @param id   the type ID
   * @param name the type name
   */
  @RecordBuilder
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record OrganizationType(String id, String name) {}

  /**
   * Country information.
   *
   * @param id           the country ID
   * @param name         the country name
   * @param isoCode      the ISO country code
   * @param status       the country status
   * @param modifiedDate the country last-modified timestamp
   */
  @RecordBuilder
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Country(
      String id,
      String name,
      @JsonProperty("iso_code") String isoCode,
      String status,
      @JsonProperty("modified_date") Instant modifiedDate) {}
}
