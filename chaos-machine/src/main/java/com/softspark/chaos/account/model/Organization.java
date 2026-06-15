package com.softspark.chaos.account.model;

import com.softspark.chaos.account.enumeration.OrganizationStatus;
import com.softspark.chaos.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Organization entity.
 */
@Entity
@Table(name = "organization")
public class Organization extends AuditableEntity {

  @Id
  @Column(name = "organization_id", nullable = false)
  private String organizationId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type_name")
  private String typeName;

  @Column(name = "country_name")
  private String countryName;

  @Column(name = "country_iso_code", length = 3)
  private String countryIsoCode;

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

  public OrganizationStatus getStatus() {
    return status;
  }

  public void setStatus(OrganizationStatus status) {
    this.status = status;
  }
}
