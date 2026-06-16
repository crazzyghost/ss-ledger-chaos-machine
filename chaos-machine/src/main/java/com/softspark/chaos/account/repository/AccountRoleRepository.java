package com.softspark.chaos.account.repository;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.account.model.AccountRoleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for account role entities.
 */
@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRoleEntity, AccountRole> {

  /**
   * Finds an account role by its account code.
   *
   * @param accountCode the account code to search for
   * @return an optional containing the account role if found
   */
  Optional<AccountRoleEntity> findByAccountCode(String accountCode);

  /**
   * Finds all account roles whose provisioning status is not equal to the given status.
   *
   * @param status the status to exclude
   * @return a list of account role entities not in the given status
   */
  List<AccountRoleEntity> findByProvisioningStatusNot(ProvisioningStatus status);

  /**
   * Counts account roles in the given provisioning status.
   *
   * @param status the provisioning status to count
   * @return the number of roles in that status
   */
  long countByProvisioningStatus(ProvisioningStatus status);
}
