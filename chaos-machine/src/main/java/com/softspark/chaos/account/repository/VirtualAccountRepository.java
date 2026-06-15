package com.softspark.chaos.account.repository;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.model.VirtualAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for virtual account entities.
 */
@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, String> {

  /**
   * Finds virtual accounts by ownership type.
   *
   * @param ownershipType the ownership type to filter by
   * @param pageable      pagination information
   * @return a page of virtual accounts
   */
  Page<VirtualAccount> findByOwnershipType(AccountOwnershipType ownershipType, Pageable pageable);

  /**
   * Finds virtual accounts by organization ID.
   *
   * @param organizationId the organization ID to filter by
   * @param pageable       pagination information
   * @return a page of virtual accounts
   */
  Page<VirtualAccount> findByOrganizationId(String organizationId, Pageable pageable);

  /**
   * Finds virtual accounts by currency.
   *
   * @param currency the currency to filter by
   * @param pageable pagination information
   * @return a page of virtual accounts
   */
  Page<VirtualAccount> findByCurrency(String currency, Pageable pageable);

  /**
   * Finds virtual accounts by status.
   *
   * @param status   the status to filter by
   * @param pageable pagination information
   * @return a page of virtual accounts
   */
  Page<VirtualAccount> findByStatus(AccountStatus status, Pageable pageable);

  /**
   * Searches virtual accounts by name or VA ID (case-insensitive partial match).
   *
   * @param searchTerm the search term
   * @param pageable   pagination information
   * @return a page of virtual accounts
   */
  @Query(
      "SELECT v FROM VirtualAccount v WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))"
          + " OR LOWER(v.vaId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
  Page<VirtualAccount> searchByNameOrId(@Param("searchTerm") String searchTerm, Pageable pageable);
}
