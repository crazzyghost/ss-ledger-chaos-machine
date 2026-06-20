package com.softspark.chaos.organization.model;

import com.softspark.chaos.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Organization type reference-data entity.
 */
@Entity
@Table(name = "organization_type")
public class OrganizationType extends AuditableEntity {

  @Id
  @Column(name = "organization_type_id", nullable = false)
  private String organizationTypeId;

  @Column(name = "name", nullable = false)
  private String name;

  public String getOrganizationTypeId() {
    return organizationTypeId;
  }

  public void setOrganizationTypeId(String organizationTypeId) {
    this.organizationTypeId = organizationTypeId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
