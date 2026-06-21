package com.softspark.chaos.account.controller;

import com.softspark.chaos.account.bootstrap.BootstrapResult;
import com.softspark.chaos.account.dto.ChartOfAccountsRoleResponse;
import com.softspark.chaos.account.dto.UpdateRoleRequest;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.service.ChartOfAccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for chart of accounts operations.
 *
 * <p>Provides endpoints for viewing and editing account roles in the chart of accounts, and for
 * triggering manual provisioning against the ledger service.
 */
@RestController
@RequestMapping("/api/v0/chart-of-accounts")
@Tag(name = "Chart of Accounts", description = "Chart of accounts management")
public class ChartOfAccountsController {

  private final ChartOfAccountsService chartOfAccountsService;

  public ChartOfAccountsController(ChartOfAccountsService chartOfAccountsService) {
    this.chartOfAccountsService = chartOfAccountsService;
  }

  /**
   * Retrieves all account roles in the chart of accounts.
   *
   * @return a list of all account roles
   */
  @GetMapping
  @Operation(
      summary = "Get all account roles",
      description =
          "Retrieves all account roles with their codes, categories, and default virtual accounts")
  public ResponseEntity<List<ChartOfAccountsRoleResponse>> getAllRoles() {
    var roles = chartOfAccountsService.getAllRoles();
    return ResponseEntity.ok(roles);
  }

  /**
   * Retrieves a specific account role.
   *
   * @param role the account role to retrieve
   * @return the account role
   */
  @GetMapping("/{role}")
  @Operation(
      summary = "Get a specific account role",
      description = "Retrieves a specific account role by its identifier")
  public ResponseEntity<ChartOfAccountsRoleResponse> getRole(@PathVariable AccountRole role) {
    var roleResponse = chartOfAccountsService.getRole(role);
    return ResponseEntity.ok(roleResponse);
  }

  /**
   * Updates an existing account role.
   *
   * @param role    the account role to update
   * @param request the update request
   * @return the updated account role
   */
  @PutMapping("/{role}")
  @Operation(
      summary = "Update an account role",
      description = "Updates an account role's default virtual account and currency")
  public ResponseEntity<ChartOfAccountsRoleResponse> updateRole(
      @PathVariable AccountRole role, @Valid @RequestBody UpdateRoleRequest request) {
    var updated = chartOfAccountsService.updateRole(role, request);
    return ResponseEntity.ok(updated);
  }

  /**
   * Triggers a manual chart-of-accounts bootstrap run.
   *
   * @return provisioning result with counts per status
   */
  @PostMapping("/bootstrap")
  @Operation(
      summary = "Trigger chart of accounts bootstrap",
      description =
          "Re-runs account provisioning for PENDING/FAILED roles against the ledger (idempotent)."
              + " Ledger requests are authorized with the caller's bearer token.")
  public ResponseEntity<BootstrapResult> triggerBootstrap() {
    var result = chartOfAccountsService.triggerBootstrap();
    return ResponseEntity.ok(result);
  }
}
