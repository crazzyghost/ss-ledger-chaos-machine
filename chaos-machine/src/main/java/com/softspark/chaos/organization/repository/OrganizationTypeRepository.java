package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.OrganizationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for organization type reference-data entities.
 */
@Repository
public interface OrganizationTypeRepository extends JpaRepository<OrganizationType, String> {

  /**
   * Finds an organization type by its name, ignoring case.
   *
   * @param name the name to look up
   * @return the organization type, if present
   */
  Optional<OrganizationType> findByNameIgnoreCase(String name);
}
