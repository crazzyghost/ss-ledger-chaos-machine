package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for organization entities.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {}
