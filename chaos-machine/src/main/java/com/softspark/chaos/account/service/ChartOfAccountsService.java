package com.softspark.chaos.account.service;

import com.softspark.chaos.account.bootstrap.BootstrapResult;
import com.softspark.chaos.account.bootstrap.ChartOfAccountsBootstrapRunner;
import com.softspark.chaos.account.dto.ChartOfAccountsRoleResponse;
import com.softspark.chaos.account.dto.UpdateRoleRequest;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the chart of accounts.
 *
 * <p>Provides operations for viewing and editing account roles, their codes, and default virtual
 * accounts. Also exposes a manual bootstrap trigger for HTTP-based provisioning.
 */
@Service
public class ChartOfAccountsService {

  private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsService.class);

  private final AccountRoleRepository accountRoleRepository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final ChartOfAccountsBootstrapRunner bootstrapRunner;

  public ChartOfAccountsService(
      AccountRoleRepository accountRoleRepository,
      VirtualAccountRepository virtualAccountRepository,
      ChartOfAccountsBootstrapRunner bootstrapRunner) {
    this.accountRoleRepository = accountRoleRepository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.bootstrapRunner = bootstrapRunner;
  }

  /**
   * Retrieves all account roles in the chart of accounts.
   *
   * @return a list of all account roles
   */
  @Transactional(readOnly = true)
  public List<ChartOfAccountsRoleResponse> getAllRoles() {
    log.debug("Fetching all account roles");
    return accountRoleRepository.findAll().stream().map(this::mapToResponse).toList();
  }

  /**
   * Retrieves a specific account role by its identifier.
   *
   * @param role the account role to retrieve
   * @return the account role response
   * @throws NotFoundException if the role is not found
   */
  @Transactional(readOnly = true)
  public ChartOfAccountsRoleResponse getRole(AccountRole role) {
    log.debug("Fetching account role: {}", role);
    var entity =
        accountRoleRepository
            .findById(role)
            .orElseThrow(() -> new NotFoundException("Account role not found: " + role));
    return mapToResponse(entity);
  }

  /**
   * Updates an existing account role.
   *
   * @param role    the account role to update
   * @param request the update request containing new values
   * @return the updated account role
   * @throws NotFoundException   if the role is not found
   * @throws BadRequestException if the specified default VA ID does not exist
   */
  @Transactional
  public ChartOfAccountsRoleResponse updateRole(AccountRole role, UpdateRoleRequest request) {
    log.info("Updating account role: {}", role);

    var entity =
        accountRoleRepository
            .findById(role)
            .orElseThrow(() -> new NotFoundException("Account role not found: " + role));

    if (!virtualAccountRepository.existsById(request.defaultVaId())) {
      throw new BadRequestException("Virtual account not found: " + request.defaultVaId(), null);
    }

    entity.setDefaultVaId(request.defaultVaId());
    entity.setCurrency(request.currency());
    var saved = accountRoleRepository.save(entity);

    log.info("Updated account role {} with default VA {}", role, request.defaultVaId());
    return mapToResponse(saved);
  }

  /**
   * Triggers a manual provisioning run for all non-{@link com.softspark.chaos.account.enumeration.ProvisioningStatus#PROVISIONED} roles.
   *
   * <p>This is idempotent: already-provisioned roles are skipped.
   *
   * @return a {@link BootstrapResult} with the current provisioning state counts
   */
  public BootstrapResult triggerBootstrap() {
    log.info("Manual chart-of-accounts bootstrap triggered via HTTP");
    return bootstrapRunner.triggerManualBootstrap();
  }

  private ChartOfAccountsRoleResponse mapToResponse(AccountRoleEntity entity) {
    return new ChartOfAccountsRoleResponse(
        entity.getRole(),
        entity.getAccountCode(),
        entity.getCategory(),
        entity.getCurrency(),
        entity.getChannel(),
        entity.getDefaultVaId(),
        entity.getDefaultVaId(),
        entity.getProvisioningStatus());
  }
}
