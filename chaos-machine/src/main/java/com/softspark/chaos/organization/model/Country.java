package com.softspark.chaos.organization.model;

import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Country reference-data entity.
 */
@Entity
@Table(name = "country")
public class Country extends AuditableEntity {

  @Id
  @Column(name = "country_id", nullable = false)
  private String countryId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "iso_code", nullable = false, unique = true)
  private String isoCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CountryStatus status;

  @Column(name = "primary_currency_id")
  private String primaryCurrencyId;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "modified_date", nullable = false)
  private Instant modifiedDate;

  public String getCountryId() {
    return countryId;
  }

  public void setCountryId(String countryId) {
    this.countryId = countryId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIsoCode() {
    return isoCode;
  }

  public void setIsoCode(String isoCode) {
    this.isoCode = isoCode;
  }

  public CountryStatus getStatus() {
    return status;
  }

  public void setStatus(CountryStatus status) {
    this.status = status;
  }

  public String getPrimaryCurrencyId() {
    return primaryCurrencyId;
  }

  public void setPrimaryCurrencyId(String primaryCurrencyId) {
    this.primaryCurrencyId = primaryCurrencyId;
  }

  public Instant getModifiedDate() {
    return modifiedDate;
  }

  public void setModifiedDate(Instant modifiedDate) {
    this.modifiedDate = modifiedDate;
  }
}
