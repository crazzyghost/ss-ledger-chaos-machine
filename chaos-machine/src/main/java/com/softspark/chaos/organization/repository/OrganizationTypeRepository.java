package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.OrganizationType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /**
   * Searches organization types whose name contains the given term (case-insensitive).
   *
   * @param name     the term matched against {@code name}
   * @param pageable the page + sort request
   * @return a page of matching organization types
   */
  Page<OrganizationType> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
