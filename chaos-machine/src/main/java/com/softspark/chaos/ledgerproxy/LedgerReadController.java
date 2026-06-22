package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.base.CursorPageResponse;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerAccountDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerBalanceDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerCursorPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionHistoryDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionReferenceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy controller that surfaces ledger-owned reads as chaos gateway endpoints.
 *
 * <p>All outbound calls are guarded by a circuit breaker in {@link LedgerClient}. When the
 * circuit is open a {@code 503} is returned so the chaos machine stays responsive during
 * ledger stress tests.
 */
@RestController
@RequestMapping("/api/v0/ledger")
@Tag(name = "Ledger Proxy", description = "Proxied read-through to the ledger service")
@SecurityRequirement(name = "bearerAuth")
public class LedgerReadController {

  private final LedgerClient ledgerClient;

  /**
   * Constructs the controller.
   *
   * @param ledgerClient the ledger read client
   */
  public LedgerReadController(LedgerClient ledgerClient) {
    this.ledgerClient = ledgerClient;
  }

  /**
   * Lists accounts from the ledger with optional filters.
   *
   * @param ownershipType optional ownership type filter
   * @param organizationId optional organization id filter
   * @param status optional account status filter
   * @param page zero-based page number (default 0)
   * @param size page size (default 20)
   * @param request the HTTP request (for token extraction)
   * @return paginated list of ledger accounts
   */
  @GetMapping("/accounts")
  @Operation(summary = "List accounts", description = "Proxy to the ledger account list")
  public ResponseEntity<PageResponse<LedgerAccountDto>> listAccounts(
      @RequestParam(required = false) String ownershipType,
      @RequestParam(required = false) String organizationId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String currency,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var result =
          ledgerClient.listAccounts(
              token, ownershipType, organizationId, status, currency, page, size);
      return ResponseEntity.ok(toPageResponse(result));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Retrieves a single account from the ledger.
   *
   * @param id the ledger account UUID
   * @param request the HTTP request
   * @return the account DTO
   */
  @GetMapping("/accounts/{id}")
  @Operation(summary = "Get account", description = "Proxy to a single ledger account")
  public ResponseEntity<LedgerAccountDto> getAccount(
      @PathVariable String id, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      return ResponseEntity.ok(ledgerClient.getAccount(token, id));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Retrieves balances for a single account from the ledger.
   *
   * @param id the ledger account UUID
   * @param request the HTTP request
   * @return the balance DTO
   */
  @GetMapping("/accounts/{id}/balance")
  @Operation(summary = "Get account balance", description = "Proxy to a ledger account balance")
  public ResponseEntity<LedgerBalanceDto> getAccountBalance(
      @PathVariable String id, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      return ResponseEntity.ok(ledgerClient.getAccountBalance(token, id));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Fetches a cursor page of an account's transaction history from the ledger.
   *
   * <p>This is the correct way to read a single virtual account's ledger movements: the ledger
   * exposes them keyset-paginated by opaque cursor (it rejects {@code page}). Callers walk pages
   * via the {@code nextCursor} / {@code previousCursor} returned in the envelope.
   *
   * @param id the ledger account UUID
   * @param from optional ISO-8601 start of the posted-at range
   * @param to optional ISO-8601 end of the posted-at range
   * @param entryType optional entry-type filter
   * @param direction optional {@code DEBIT}/{@code CREDIT} filter
   * @param transactionRef optional transaction-ref filter
   * @param cursor opaque page cursor; absent fetches the first page
   * @param size optional page size; absent lets the ledger apply its default
   * @param request the HTTP request
   * @return a cursor page of transaction-history records
   */
  @GetMapping("/accounts/{id}/transactions")
  @Operation(
      summary = "Get account transaction history",
      description = "Proxy to the ledger's cursor-paginated account transaction history")
  public ResponseEntity<CursorPageResponse<LedgerTransactionHistoryDto>> getAccountTransactionHistory(
      @PathVariable String id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String entryType,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String transactionRef,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var result =
          ledgerClient.getAccountTransactionHistory(
              token, id, from, to, entryType, direction, transactionRef, cursor, size);
      return ResponseEntity.ok(toCursorResponse(result));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Lists transactions from the ledger with optional filters.
   *
   * @param vaId optional virtual account id filter (matches source or destination)
   * @param eventType optional event type filter
   * @param correlationId optional correlation id filter
   * @param from optional ISO-8601 start of time range
   * @param to optional ISO-8601 end of time range
   * @param page zero-based page number (default 0)
   * @param size page size (default 20)
   * @param request the HTTP request
   * @return paginated list of ledger transactions
   */
  @GetMapping("/transactions")
  @Operation(summary = "List transactions", description = "Proxy to the ledger transaction list")
  public ResponseEntity<PageResponse<LedgerTransactionDto>> listTransactions(
      @RequestParam(required = false) String vaId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String correlationId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var result =
          ledgerClient.listTransactions(
              token, vaId, eventType, correlationId, from, to, page, size);
      return ResponseEntity.ok(toPageResponse(result));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Fetches every leg of a single transaction by its reference.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/transactions/{ref}} endpoint. Backs the transaction
   * detail page reached by clicking a row in an account's transaction history.
   *
   * @param ref the transaction reference (idempotency key)
   * @param request the HTTP request
   * @return the legs of the transaction as a page response
   */
  @GetMapping("/transactions/{ref}")
  @Operation(
      summary = "Get transaction by reference",
      description = "Proxy to the ledger's transaction-by-reference history")
  public ResponseEntity<PageResponse<LedgerTransactionReferenceDto>> getTransactionByReference(
      @PathVariable String ref, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      return ResponseEntity.ok(toPageResponse(ledgerClient.getTransactionByReference(token, ref)));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  private String extractToken(HttpServletRequest request) {
    var header = request.getHeader("Authorization");
    return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
  }

  private <T> PageResponse<T> toPageResponse(LedgerPageDto<T> page) {
    return new PageResponse<>(page.data(), page.page(), page.pageSize(), page.total());
  }

  private <T> CursorPageResponse<T> toCursorResponse(LedgerCursorPageDto<T> page) {
    return new CursorPageResponse<>(
        page.data(), page.nextCursor(), page.previousCursor(), page.hasMore(), page.size());
  }
}
