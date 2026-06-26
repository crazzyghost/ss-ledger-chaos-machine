package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreaker;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerAccountDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerBalanceDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerCursorPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerSpringPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionHistoryDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionReferenceDto;
import com.softspark.chaos.ledgerproxy.dto.ReservationResponse;
import com.softspark.chaos.ledgerproxy.dto.TrialBalanceDto;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gateway component that issues read-only calls to {@code ss-ledger-service} guarded by a
 * minimal {@link CircuitBreaker}.
 *
 * <p>When the circuit is open a {@link CircuitBreakerOpenException} propagates to the controller,
 * which converts it to a {@code 503} response so the chaos machine stays responsive under
 * ledger outage.
 */
@Component
public class LedgerClient {

  private final RestClient restClient;
  private final LedgerProxyProperties proxyProperties;
  private final CircuitBreaker circuitBreaker;
  private final int reservationPageSize;

  /**
   * Constructs the client.
   *
   * @param restClient the ledger proxy {@link RestClient} bean
   * @param proxyProperties the proxy configuration
   * @param reservationPageSize the page size requested when listing reservations (newest first)
   */
  public LedgerClient(
      @Qualifier("ledgerProxyRestClient") RestClient restClient,
      LedgerProxyProperties proxyProperties,
      @Value("${chaos.ledger.reservation.page-size:100}") int reservationPageSize) {
    this.restClient = restClient;
    this.proxyProperties = proxyProperties;
    this.reservationPageSize = reservationPageSize;
    var cb = proxyProperties.circuitBreaker();
    this.circuitBreaker =
        new CircuitBreaker(cb.failureThreshold(), cb.successThreshold(), cb.openDurationMs());
  }

  /**
   * Lists accounts from the ledger with optional filters.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param ownershipType optional filter by ownership type
   * @param organizationId optional filter by owning org
   * @param status optional filter by account status
   * @param currency optional filter by ISO-4217 currency code
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of ledger accounts
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerAccountDto> listAccounts(
      String callerToken,
      @Nullable String ownershipType,
      @Nullable String organizationId,
      @Nullable String status,
      @Nullable String currency,
      int page,
      int size) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/api/v0/accounts")
                              .queryParam("page", page)
                              .queryParam("size", size);
                      if (ownershipType != null)
                        builder = builder.queryParam("accountOwnershipType", ownershipType);
                      if (organizationId != null)
                        builder = builder.queryParam("organizationId", organizationId);
                      if (status != null) builder = builder.queryParam("status", status);
                      if (currency != null) builder = builder.queryParam("currency", currency);
                      return builder.build();
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException(
                          "Ledger returned: " + resp.getStatusCode().value());
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(new ParameterizedTypeReference<LedgerPageDto<LedgerAccountDto>>() {}));
  }

  /**
   * Retrieves a single account by id from the ledger.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID
   * @return the account DTO
   * @throws NotFoundException if the ledger returns 404
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerAccountDto getAccount(String callerToken, String accountId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/accounts/{id}", accountId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException("Account not found: " + accountId);
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(LedgerAccountDto.class));
  }

  /**
   * Retrieves the balance for a single account from the ledger.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID
   * @return the balance DTO
   * @throws NotFoundException if the ledger returns 404
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerBalanceDto getAccountBalance(String callerToken, String accountId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/accounts/{id}/balance", accountId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException("Account not found: " + accountId);
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(LedgerBalanceDto.class));
  }

  /**
   * Lists transactions from the ledger with optional filters.
   *
   * @param callerToken the caller's bearer token
   * @param vaId optional filter by source or destination VA id
   * @param eventType optional filter by event type
   * @param correlationId optional filter by correlation id
   * @param from optional ISO-8601 start of time range
   * @param to optional ISO-8601 end of time range
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of transactions
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerTransactionDto> listTransactions(
      String callerToken,
      @Nullable String vaId,
      @Nullable String eventType,
      @Nullable String correlationId,
      @Nullable String from,
      @Nullable String to,
      int page,
      int size) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/api/v0/transactions")
                              .queryParam("page", page)
                              .queryParam("size", size);
                      if (vaId != null) builder = builder.queryParam("vaId", vaId);
                      if (eventType != null) builder = builder.queryParam("eventType", eventType);
                      if (correlationId != null)
                        builder = builder.queryParam("correlationId", correlationId);
                      if (from != null) builder = builder.queryParam("from", from);
                      if (to != null) builder = builder.queryParam("to", to);
                      return builder.build();
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException(
                          "Ledger returned: " + resp.getStatusCode().value());
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(new ParameterizedTypeReference<LedgerPageDto<LedgerTransactionDto>>() {}));
  }

  /**
   * Fetches a single cursor page of an account's transaction history from the ledger.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/accounts/{id}/transactions} endpoint, which
   * is keyset (cursor) paginated rather than offset paginated. The ledger rejects {@code page}
   * params on this endpoint, so callers walk pages via the returned {@code nextCursor} /
   * {@code previousCursor}.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID whose history is requested
   * @param from optional ISO-8601 start of the posted-at range (inclusive)
   * @param to optional ISO-8601 end of the posted-at range (inclusive)
   * @param entryType optional entry-type filter (comma-separated list accepted by the ledger)
   * @param direction optional {@code DEBIT}/{@code CREDIT} direction filter
   * @param transactionRef optional transaction-ref (idempotency key) filter
   * @param cursor opaque page cursor; {@code null} fetches the first page
   * @param size optional page size; {@code null} lets the ledger apply its default
   * @return a cursor page of history records
   * @throws NotFoundException if the ledger returns 4xx (e.g. unknown account)
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerCursorPageDto<LedgerTransactionHistoryDto> getAccountTransactionHistory(
      String callerToken,
      String accountId,
      @Nullable String from,
      @Nullable String to,
      @Nullable String entryType,
      @Nullable String direction,
      @Nullable String transactionRef,
      @Nullable String cursor,
      @Nullable Integer size) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder = uriBuilder.path("/api/v0/accounts/{id}/transactions");
                      if (from != null) builder = builder.queryParam("from", from);
                      if (to != null) builder = builder.queryParam("to", to);
                      if (entryType != null) builder = builder.queryParam("entryType", entryType);
                      if (entryType != null)
                        builder = builder.queryParam("entryLineType", entryType);
                      if (direction != null) builder = builder.queryParam("direction", direction);
                      if (transactionRef != null)
                        builder = builder.queryParam("transactionRef", transactionRef);
                      if (cursor != null) builder = builder.queryParam("cursor", cursor);
                      if (size != null) builder = builder.queryParam("size", size);
                      return builder.build(accountId);
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException(
                          "Ledger returned: " + resp.getStatusCode().value());
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(
                    new ParameterizedTypeReference<
                        LedgerCursorPageDto<LedgerTransactionHistoryDto>>() {}));
  }

  /**
   * Fetches every journal-entry line that shares a single transaction reference.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/transactions/{ref}} endpoint, which returns all legs
   * of one transaction across the accounts that participated in it. Backs the transaction detail
   * page.
   *
   * @param callerToken the caller's bearer token
   * @param transactionRef the transaction reference (idempotency key) to resolve
   * @return a page of transaction-reference records (one per leg)
   * @throws NotFoundException if the ledger returns 4xx (e.g. unknown reference)
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerTransactionReferenceDto> getTransactionByReference(
      String callerToken, String transactionRef) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/transactions/{ref}", transactionRef)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException("Transaction not found: " + transactionRef);
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(
                    new ParameterizedTypeReference<
                        LedgerPageDto<LedgerTransactionReferenceDto>>() {}));
  }

  /**
   * Fetches the unadjusted trial balance for a period from the ledger.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/reporting/trial-balance} endpoint, which computes the
   * report authoritatively. The chaos machine forwards the period (and optional currency scope)
   * verbatim and passes the response through unchanged (ADR-015). Period validation
   * ({@code from < to}, span &le; 366 days) stays with the ledger; a {@code 400} it returns is
   * translated to a {@link NotFoundException} like every other 4xx on this proxy.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param from the inclusive start of the period (ISO-8601 instant)
   * @param to the exclusive end of the period (ISO-8601 instant)
   * @param currency optional ISO-4217 currency scope; {@code null}/blank reports all currencies
   * @return the trial-balance report
   * @throws NotFoundException if the ledger returns 4xx (e.g. {@code from >= to}, span &gt; 366 days)
   * @throws InternalServerErrorException if the ledger returns 5xx
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public TrialBalanceDto getTrialBalance(
      String callerToken, Instant from, Instant to, @Nullable String currency) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/api/v0/reporting/trial-balance")
                              .queryParam("from", from)
                              .queryParam("to", to);
                      if (currency != null && !currency.isBlank()) {
                        builder = builder.queryParam("currency", currency);
                      }
                      return builder.build();
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException(
                          "Ledger returned: " + resp.getStatusCode().value());
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(TrialBalanceDto.class));
  }

  /**
   * Lists an account's reservations from the ledger, filtered by {@code transactionRef} in-process.
   *
   * <p>Contract (verified against {@code ss-ledger-service}): the ledger's
   * {@code GET /api/v0/accounts/{accountId}/reservations} returns a <em>raw</em> Spring {@code Page}
   * ({@code content}/{@code totalElements}/…) and does <strong>not</strong> filter by
   * {@code transactionRef} server-side yet. This method requests the newest page
   * ({@code sort=createdAt,desc}, generous size) — a reservation just created for our disbursement is
   * the newest — and filters the returned {@code content} by {@code transactionRef} here. The
   * {@code transactionRef} query param is still forwarded so the call becomes O(1) at the ledger the
   * day server-side filtering lands, with no change here.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID (the disbursement's org VA)
   * @param transactionRef the transaction reference to match (= the disbursement {@code
   *     transaction_id}); when null/blank the unfiltered page content is returned
   * @return the matching reservations (filtered by {@code transactionRef}); empty if none
   * @throws NotFoundException if the ledger returns 4xx (e.g. unknown account)
   * @throws InternalServerErrorException if the ledger returns 5xx
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public List<ReservationResponse> getReservations(
      String callerToken, String accountId, @Nullable String transactionRef) {
    var token = resolveToken(callerToken);
    LedgerSpringPageDto<ReservationResponse> page =
        circuitBreaker.execute(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder -> {
                          var builder =
                              uriBuilder
                                  .path("/api/v0/accounts/{id}/reservations")
                                  .queryParam("size", reservationPageSize)
                                  .queryParam("sort", "createdAt,desc");
                          if (transactionRef != null && !transactionRef.isBlank()) {
                            builder = builder.queryParam("transactionRef", transactionRef);
                          }
                          return builder.build(accountId);
                        })
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(
                        HttpStatusCode::is4xxClientError,
                        (req, resp) -> {
                          throw new NotFoundException(
                              "Ledger returned: " + resp.getStatusCode().value());
                        })
                    .onStatus(
                        HttpStatusCode::is5xxServerError,
                        (req, resp) -> {
                          throw new InternalServerErrorException(
                              "Ledger error: " + resp.getStatusCode().value());
                        })
                    .body(
                        new ParameterizedTypeReference<
                            LedgerSpringPageDto<ReservationResponse>>() {}));

    List<ReservationResponse> content =
        page != null && page.content() != null ? page.content() : List.of();
    if (transactionRef == null || transactionRef.isBlank()) {
      return content;
    }
    return content.stream().filter(r -> transactionRef.equals(r.transactionRef())).toList();
  }

  private String resolveToken(String callerToken) {
    if (proxyProperties.forwardToken()) {
      return callerToken != null ? callerToken : "";
    }
    var svc = proxyProperties.serviceToken();
    return svc != null && !svc.isBlank() ? svc : "";
  }
}
