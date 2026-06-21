package com.softspark.chaos.account.service;

import com.softspark.chaos.account.bootstrap.CreateLedgerAccountRequest;
import com.softspark.chaos.account.bootstrap.LedgerAccountProvisioningClient;
import com.softspark.chaos.account.bootstrap.LedgerProvisioningException;
import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.dto.VirtualAccountRequestAccepted;
import com.softspark.chaos.account.dto.VirtualAccountResponse;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadGatewayException;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.exception.ServiceUnavailableException;
import com.softspark.chaos.organization.service.CurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing virtual accounts.
 *
 * <p>Phase 009 inverts ownership: the ledger owns virtual accounts. {@link
 * #requestCreate(CreateVirtualAccountRequest)} validates the request, forwards it to the ledger over
 * HTTP, and returns without writing anything locally — the VA materializes only when the resulting
 * {@code ledger.account.created} event is projected. The list/get operations read that projection.
 */
@Service
public class VirtualAccountService {

  private static final Logger log = LoggerFactory.getLogger(VirtualAccountService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_ORG_CATEGORY = "LIABILITY";

  private final VirtualAccountRepository virtualAccountRepository;
  private final LedgerAccountProvisioningClient ledgerClient;
  private final CurrencyService currencyService;

  public VirtualAccountService(
      VirtualAccountRepository virtualAccountRepository,
      LedgerAccountProvisioningClient ledgerClient,
      CurrencyService currencyService) {
    this.virtualAccountRepository = virtualAccountRepository;
    this.ledgerClient = ledgerClient;
    this.currencyService = currencyService;
  }

  /**
   * Requests creation of a virtual account from the ledger. Performs no local persistence — the VA
   * appears in the registry only after the {@code ledger.account.created} projection runs.
   *
   * @param request     the creation request
   * @param callerToken the acting user's bearer token, forwarded to the ledger ({@code null} falls
   *                    back to the configured service token)
   * @return an acceptance echo describing the forwarded request
   * @throws BadRequestException       if validation fails (unknown currency, missing fields)
   * @throws ConflictException         if the currency is inactive or the ledger reports a conflict
   * @throws BadGatewayException       if the ledger returns a server error
   * @throws ServiceUnavailableException if the ledger is unreachable / its circuit is open
   */
  public VirtualAccountRequestAccepted requestCreate(
      CreateVirtualAccountRequest request, String callerToken) {
    log.info("Requesting ledger account for VA: {}", request.name());

    AccountOwnershipType ownershipType;
    try {
      ownershipType = AccountOwnershipType.valueOf(request.ownershipType());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid ownership type: " + request.ownershipType(), null);
    }

    currencyService.assertUsable(request.currency());
    String currency = request.currency().toUpperCase();

    String accountCode = blankToNull(request.accountCode());
    String accountCategory = blankToNull(request.accountCategory());
    String organizationId = blankToNull(request.organizationId());

    if (ownershipType == AccountOwnershipType.SYSTEM) {
      if (accountCode == null) {
        throw new BadRequestException("accountCode is required for SYSTEM accounts", null);
      }
      if (accountCategory == null) {
        throw new BadRequestException("accountCategory is required for SYSTEM accounts", null);
      }
    } else {
      if (organizationId == null) {
        throw new BadRequestException("organizationId is required for ORGANIZATION accounts", null);
      }
      if (accountCategory == null) {
        accountCategory = DEFAULT_ORG_CATEGORY;
      }
    }

    var ledgerRequest =
        new CreateLedgerAccountRequest(
            accountCode,
            request.name(),
            accountCategory,
            currency,
            blankToNull(request.parentAccountId()),
            request.overdraftLimit(),
            request.minimumBalance(),
            ownershipType.name(),
            organizationId);

    try {
      String accountId = ledgerClient.createAccount(ledgerRequest, callerToken);
      log.info(
          "Forwarded VA creation to ledger (ownership={}, code={}, org={}, ledgerAccountId={})",
          ownershipType,
          accountCode,
          organizationId,
          accountId);
    } catch (LedgerProvisioningException e) {
      throw mapLedgerError(e);
    }

    return new VirtualAccountRequestAccepted(
        "REQUESTED",
        "Creation requested from the ledger; the account will appear in the registry once the "
            + "ledger.account.created event is consumed.",
        accountCode,
        organizationId,
        currency,
        ownershipType.name());
  }

  /**
   * Retrieves a virtual account by ID.
   *
   * @param vaId the virtual account ID
   * @return the virtual account
   * @throws NotFoundException if the virtual account is not found
   */
  @Transactional(readOnly = true)
  public VirtualAccountResponse getVirtualAccount(String vaId) {
    log.debug("Fetching virtual account: {}", vaId);
    var va =
        virtualAccountRepository
            .findById(vaId)
            .orElseThrow(() -> new NotFoundException("Virtual account not found: " + vaId));
    return mapToResponse(va);
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
  @Transactional(readOnly = true)
  public PageResponse<VirtualAccountResponse> listVirtualAccounts(
      Integer page,
      Integer perPage,
      String ownershipType,
      String organizationId,
      String currency,
      String status,
      String search) {
    log.debug("Listing virtual accounts");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Pageable pageable = PageRequest.of(pageNum, pageSize);

    Page<VirtualAccount> vaPage;

    if (search != null && !search.isBlank()) {
      vaPage = virtualAccountRepository.searchByNameOrId(search, pageable);
    } else if (ownershipType != null && !ownershipType.isBlank()) {
      try {
        var ownership = AccountOwnershipType.valueOf(ownershipType);
        vaPage = virtualAccountRepository.findByOwnershipType(ownership, pageable);
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Invalid ownership type: " + ownershipType, null);
      }
    } else if (organizationId != null && !organizationId.isBlank()) {
      vaPage = virtualAccountRepository.findByOrganizationId(organizationId, pageable);
    } else if (currency != null && !currency.isBlank()) {
      vaPage = virtualAccountRepository.findByCurrency(currency, pageable);
    } else if (status != null && !status.isBlank()) {
      try {
        var accountStatus = AccountStatus.valueOf(status);
        vaPage = virtualAccountRepository.findByStatus(accountStatus, pageable);
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Invalid status: " + status, null);
      }
    } else {
      vaPage = virtualAccountRepository.findAll(pageable);
    }

    var items = vaPage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, vaPage.getTotalElements());
  }

  private VirtualAccountResponse mapToResponse(VirtualAccount va) {
    return new VirtualAccountResponse(
        va.getVaId(),
        va.getName(),
        va.getOwnershipType(),
        va.getOrganizationId(),
        va.getCurrency(),
        va.getStatus(),
        va.getChannel(),
        va.getAccountRole(),
        va.getCreatedVia(),
        va.getCreatedAt(),
        va.getUpdatedAt());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * Maps a ledger provisioning failure to an HTTP-mapped exception: 4xx → 400 (409 → conflict), 5xx
   * → 502, transport/circuit failures (status 0) → 503.
   */
  private RuntimeException mapLedgerError(LedgerProvisioningException e) {
    int status = e.getStatusCode();
    if (status == 409) {
      return new ConflictException("Account already exists in the ledger: " + e.getMessage());
    }
    if (status >= 400 && status < 500) {
      return new BadRequestException("Ledger rejected the request: " + e.getMessage(), e);
    }
    if (status >= 500) {
      return new BadGatewayException("Ledger returned an error: " + e.getMessage(), e);
    }
    return new ServiceUnavailableException("Ledger is unavailable: " + e.getMessage(), e);
  }
}
