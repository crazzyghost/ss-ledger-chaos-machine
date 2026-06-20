package com.softspark.chaos.organization.model;

import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.base.JsonStringListConverter;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * Organization entity.
 *
 * <p>Organizations are created via two paths: the onboarding API (which populates the reference-data
 * foreign keys, contact fields, and snapshot columns) and the legacy virtual-account create-on-demand
 * path (which leaves the foreign keys and contact fields null). The {@code type_name},
 * {@code country_name}, {@code country_iso_code}, {@code country_status}, and
 * {@code country_modified_date} columns are point-in-time snapshots of the referenced reference data
 * captured at onboarding time and are not re-derived on read.
 */
@Entity
@Table(name = "organization")
public class Organization extends AuditableEntity {

  @Id
  @Column(name = "organization_id", nullable = false)
  private String organizationId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "organization_type_id")
  private String organizationTypeId;

  @Column(name = "country_id")
  private String countryId;

  @Column(name = "primary_contact_email")
  private String primaryContactEmail;

  @Convert(converter = JsonStringListConverter.class)
  @Column(name = "phone_numbers")
  private List<String> phoneNumbers;

  @Column(name = "type_name")
  private String typeName;

  @Column(name = "country_name")
  private String countryName;

  @Column(name = "country_iso_code", length = 3)
  private String countryIsoCode;

  @Column(name = "country_status")
  private String countryStatus;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "country_modified_date")
  private Instant countryModifiedDate;

  @Column(name = "primary_currency_id")
  private String primaryCurrencyId;

  @Column(name = "primary_currency_code")
  private String primaryCurrencyCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OrganizationStatus status;

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOrganizationTypeId() {
    return organizationTypeId;
  }

  public void setOrganizationTypeId(String organizationTypeId) {
    this.organizationTypeId = organizationTypeId;
  }

  public String getCountryId() {
    return countryId;
  }

  public void setCountryId(String countryId) {
    this.countryId = countryId;
  }

  public String getPrimaryContactEmail() {
    return primaryContactEmail;
  }

  public void setPrimaryContactEmail(String primaryContactEmail) {
    this.primaryContactEmail = primaryContactEmail;
  }

  public List<String> getPhoneNumbers() {
    return phoneNumbers;
  }

  public void setPhoneNumbers(List<String> phoneNumbers) {
    this.phoneNumbers = phoneNumbers;
  }

  public String getTypeName() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  public String getCountryName() {
    return countryName;
  }

  public void setCountryName(String countryName) {
    this.countryName = countryName;
  }

  public String getCountryIsoCode() {
    return countryIsoCode;
  }

  public void setCountryIsoCode(String countryIsoCode) {
    this.countryIsoCode = countryIsoCode;
  }

  public String getCountryStatus() {
    return countryStatus;
  }

  public void setCountryStatus(String countryStatus) {
    this.countryStatus = countryStatus;
  }

  public Instant getCountryModifiedDate() {
    return countryModifiedDate;
  }

  public void setCountryModifiedDate(Instant countryModifiedDate) {
    this.countryModifiedDate = countryModifiedDate;
  }

  public String getPrimaryCurrencyId() {
    return primaryCurrencyId;
  }

  public void setPrimaryCurrencyId(String primaryCurrencyId) {
    this.primaryCurrencyId = primaryCurrencyId;
  }

  public String getPrimaryCurrencyCode() {
    return primaryCurrencyCode;
  }

  public void setPrimaryCurrencyCode(String primaryCurrencyCode) {
    this.primaryCurrencyCode = primaryCurrencyCode;
  }

  public OrganizationStatus getStatus() {
    return status;
  }

  public void setStatus(OrganizationStatus status) {
    this.status = status;
  }
}
