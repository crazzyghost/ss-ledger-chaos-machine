package com.softspark.chaos.organization.model;

import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Supported-country entity: the curated subset of countries an operator may onboard into.
 *
 * <p>Modelled as a separate table (not a flag on {@code country}) so the onboarding-form subset is
 * distinct from the full country master list and can carry its own configuration over time.
 */
@Entity
@Table(name = "supported_country")
public class SupportedCountry extends AuditableEntity {

  @Id
  @Column(name = "supported_country_id", nullable = false)
  private String supportedCountryId;

  @Column(name = "country_id", nullable = false, unique = true)
  private String countryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SupportedCountryStatus status;

  public String getSupportedCountryId() {
    return supportedCountryId;
  }

  public void setSupportedCountryId(String supportedCountryId) {
    this.supportedCountryId = supportedCountryId;
  }

  public String getCountryId() {
    return countryId;
  }

  public void setCountryId(String countryId) {
    this.countryId = countryId;
  }

  public SupportedCountryStatus getStatus() {
    return status;
  }

  public void setStatus(SupportedCountryStatus status) {
    this.status = status;
  }
}
