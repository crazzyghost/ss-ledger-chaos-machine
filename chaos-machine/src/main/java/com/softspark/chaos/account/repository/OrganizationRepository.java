package com.softspark.chaos.account.repository;

import com.softspark.chaos.account.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for organization entities.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {
}
