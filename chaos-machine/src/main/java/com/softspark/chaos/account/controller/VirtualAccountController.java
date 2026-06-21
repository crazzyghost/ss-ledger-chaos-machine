package com.softspark.chaos.account.controller;

import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.dto.VirtualAccountRequestAccepted;
import com.softspark.chaos.account.dto.VirtualAccountResponse;
import com.softspark.chaos.account.service.VirtualAccountService;
import com.softspark.chaos.auth.BearerToken;
import com.softspark.chaos.base.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for virtual account operations.
 *
 * <p>Phase 009: the ledger owns virtual accounts. Creation is asynchronous — {@code POST} forwards
 * the request to the ledger and returns {@code 202 Accepted}; the VA appears in the list once the
 * {@code ledger.account.created} event is consumed. List/Get read the projection.
 */
@RestController
@RequestMapping("/api/v0/virtual-accounts")
@Tag(name = "Virtual Accounts", description = "Virtual account registry")
public class VirtualAccountController {

  private final VirtualAccountService virtualAccountService;

  public VirtualAccountController(VirtualAccountService virtualAccountService) {
    this.virtualAccountService = virtualAccountService;
  }

  /**
   * Requests creation of a virtual account from the ledger (asynchronous).
   *
   * @param request the creation request
   * @return {@code 202 Accepted} echoing the forwarded request
   */
  @PostMapping
  @Operation(
      summary = "Request a virtual account",
      description =
          "Forwards a creation request to the ledger (POST /api/v0/accounts) and returns 202"
              + " Accepted. The account is owned by the ledger and appears in GET"
              + " /api/v0/virtual-accounts once the ledger.account.created event is consumed —"
              + " poll the list for eventual consistency.")
  public ResponseEntity<VirtualAccountRequestAccepted> createVirtualAccount(
      @Valid @RequestBody CreateVirtualAccountRequest request, HttpServletRequest httpRequest) {
    var accepted =
        virtualAccountService.requestCreate(request, BearerToken.fromRequest(httpRequest));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
  }

  /**
   * Retrieves a virtual account by ID.
   *
   * @param vaId the virtual account ID
   * @return the virtual account
   */
  @GetMapping("/{vaId}")
  @Operation(
      summary = "Get a virtual account",
      description = "Retrieves a virtual account by its ID")
  public ResponseEntity<VirtualAccountResponse> getVirtualAccount(@PathVariable String vaId) {
    var va = virtualAccountService.getVirtualAccount(vaId);
    return ResponseEntity.ok(va);
  }

  /**
   * Lists virtual accounts with pagination and optional filtering.
   *
   * @param page           the page number (0-indexed)
   * @param perPage        the number of items per page
   * @param ownershipType  optional ownership type filter
   * @param organizationId optional organization ID filter
   * @param currency       optional currency filter
   * @param status         optional status filter
   * @param search         optional search term for name/ID
   * @return a paginated list of virtual accounts
   */
  @GetMapping
  @Operation(
      summary = "List virtual accounts",
      description = "Lists virtual accounts with pagination and optional filters")
  public ResponseEntity<PageResponse<VirtualAccountResponse>> listVirtualAccounts(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage,
      @Parameter(description = "Filter by ownership type (SYSTEM or ORGANIZATION)")
          @RequestParam(required = false)
          String ownershipType,
      @Parameter(description = "Filter by organization ID") @RequestParam(required = false)
          String organizationId,
      @Parameter(description = "Filter by currency") @RequestParam(required = false)
          String currency,
      @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
      @Parameter(description = "Search by name or VA ID (partial match)")
          @RequestParam(required = false)
          String search) {
    var result =
        virtualAccountService.listVirtualAccounts(
            page, perPage, ownershipType, organizationId, currency, status, search);
    return ResponseEntity.ok(result);
  }
}
