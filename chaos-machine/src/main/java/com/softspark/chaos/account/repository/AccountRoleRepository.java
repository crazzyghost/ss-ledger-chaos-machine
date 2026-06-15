package com.softspark.chaos.account.repository;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
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
}
