package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for organization entities.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

  /**
   * Searches organizations by name, snapshot type name, snapshot country name, or primary contact
   * email (case-insensitive substring match).
   *
   * @param term     the search term
   * @param pageable the page + sort request
   * @return a page of matching organizations
   */
  @Query(
      """
      select o from Organization o
      where lower(o.name) like lower(concat('%', :term, '%'))
         or lower(coalesce(o.typeName, '')) like lower(concat('%', :term, '%'))
         or lower(coalesce(o.countryName, '')) like lower(concat('%', :term, '%'))
         or lower(coalesce(o.primaryContactEmail, '')) like lower(concat('%', :term, '%'))
      """)
  Page<Organization> search(@Param("term") String term, Pageable pageable);
}
