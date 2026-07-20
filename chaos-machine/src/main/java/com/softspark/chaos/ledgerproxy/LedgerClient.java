package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreaker;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.BatchBalanceItemDto;
import com.softspark.chaos.ledgerproxy.dto.BatchBalanceListDto;
import com.softspark.chaos.ledgerproxy.dto.DisbursementBatchSummaryDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerAccountDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerBalanceDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDiscrepancyDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckTriggerDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerCursorPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerExportResult;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerSpringPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionExportDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionHistoryDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionReferenceDto;
import com.softspark.chaos.ledgerproxy.dto.ReconciliationEntryDto;
import com.softspark.chaos.ledgerproxy.dto.ReservationResponse;
import com.softspark.chaos.ledgerproxy.dto.TrialBalanceDto;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gateway component that issues calls to {@code ss-ledger-service} guarded by a minimal
 * {@link CircuitBreaker}.
 *
 * <p>When the circuit is open a {@link CircuitBreakerOpenException} propagates to the controller,
 * which converts it to a {@code 503} response so the chaos machine stays responsive under
 * ledger outage.
 *
 * <p><strong>Two error conventions live here</strong> (ADR-035). The read methods collapse every
 * ledger 4xx to a {@link NotFoundException} — long-standing, documented, and depended on by the
 * frontend. The statement-export methods ({@link #createExport}, {@link #getExport},
 * {@link #listExports}, {@link #cancelExport}) propagate the ledger's status faithfully via
 * {@link LedgerStatusPropagation}, because their 400/401/403/404/409 mean five different things.
 * The latter is the target convention; migrating the reads is a follow-up, not licence to collapse
 * a new export method to a 404.
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
   * Retrieves the balance for a single account from the ledger, optionally as of a point in time.
   *
   * <p>When {@code asOf} is non-null it is appended as the ledger's {@code asOf} query param (ISO
   * local date-time); the ledger reconstructs the historical snapshot and is authoritative for the
   * not-future rule (ADR-020). A {@code 400} for a bad/future {@code asOf} is translated to a {@link
   * NotFoundException} like every other 4xx on this proxy.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID
   * @param asOf optional point-in-time; {@code null} reads the current balance
   * @return the balance DTO
   * @throws NotFoundException if the ledger returns 4xx (e.g. unknown account, future {@code asOf})
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerBalanceDto getAccountBalance(
      String callerToken, String accountId, @Nullable LocalDateTime asOf) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder = uriBuilder.path("/api/v0/accounts/{id}/balance");
                      if (asOf != null) {
                        builder = builder.queryParam("asOf", asOf);
                      }
                      return builder.build(accountId);
                    })
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
   * Retrieves balances for several accounts in one call from the ledger's batch endpoint.
   *
   * <p>Proxies {@code GET /api/v0/balances?accountId=…} (repeated param), forwarding the ids
   * verbatim and unwrapping the ledger's paged envelope to a flat list (ADR-021). Each returned item
   * carries the ledger's per-account {@code status} ({@code FOUND}/{@code NOT_FOUND}/{@code
   * FORBIDDEN}); a not-found/forbidden item has {@code null} balance fields and is not an error. The
   * caller (UI) bounds the id count to a page (≤ the ledger's batch cap).
   *
   * @param callerToken the caller's bearer token
   * @param accountIds the account UUIDs to look up
   * @return one balance item per requested id (empty if the ledger returns no rows)
   * @throws NotFoundException if the ledger returns 4xx (e.g. id count over the cap)
   * @throws InternalServerErrorException if the ledger returns 5xx
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public List<BatchBalanceItemDto> getBatchBalances(String callerToken, List<String> accountIds) {
    var token = resolveToken(callerToken);
    BatchBalanceListDto body =
        circuitBreaker.execute(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder -> {
                          var builder = uriBuilder.path("/api/v0/balances");
                          for (var id : accountIds) {
                            builder = builder.queryParam("accountId", id);
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
                    .body(BatchBalanceListDto.class));
    return body == null || body.data() == null ? List.of() : body.data();
  }

  /**
   * Exports a page of journal-entry lines for a period from the ledger's reconciliation export.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/reporting/reconciliation-export} endpoint (paged-JSON
   * mode, ADR-032) — a real cross-account, time-windowed browse of journal-entry lines (with sibling
   * legs).
   * The period bounds are forwarded verbatim; period validity ({@code from}/{@code to} required, span
   * capped at the ledger's default ~7 days) stays with the ledger, whose {@code 400} is translated to
   * a {@link NotFoundException} like every other 4xx on this proxy. The optional {@code accountId} is
   * repeatable; the remaining filters are forwarded when present.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param from the inclusive start of the window (ISO-8601 instant)
   * @param to the exclusive end of the window (ISO-8601 instant)
   * @param accountId optional repeatable account-id filter
   * @param entryType optional entry-type filter (comma-separated list accepted by the ledger)
   * @param transactionRef optional transaction-ref filter
   * @param sourceService optional source-service filter
   * @param page zero-based page number
   * @param size page size (the ledger caps it at 100)
   * @return a paginated list of reconciliation journal-entry lines
   * @throws NotFoundException if the ledger returns 4xx (e.g. missing/too-wide window)
   * @throws InternalServerErrorException if the ledger returns 5xx
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<ReconciliationEntryDto> exportJournalEntries(
      String callerToken,
      Instant from,
      Instant to,
      @Nullable List<String> accountId,
      @Nullable String entryType,
      @Nullable String transactionRef,
      @Nullable String sourceService,
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
                              .path("/api/v0/reporting/reconciliation-export")
                              .queryParam("from", from)
                              .queryParam("to", to)
                              .queryParam("page", page)
                              .queryParam("size", size);
                      if (accountId != null) {
                        for (var id : accountId) {
                          builder = builder.queryParam("accountId", id);
                        }
                      }
                      if (entryType != null && !entryType.isBlank()) {
                        builder = builder.queryParam("entryType", entryType);
                      }
                      if (transactionRef != null && !transactionRef.isBlank()) {
                        builder = builder.queryParam("transactionRef", transactionRef);
                      }
                      if (sourceService != null && !sourceService.isBlank()) {
                        builder = builder.queryParam("sourceService", sourceService);
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
                .body(new ParameterizedTypeReference<LedgerPageDto<ReconciliationEntryDto>>() {}));
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

  /**
   * Fetches a disbursement batch summary from the ledger, keyed by {@code batch_id}.
   *
   * <p>Proxies the ledger's {@code GET /api/v0/disbursement-batches/{batchId}} endpoint (verified
   * {@code DisbursementBatchQueryController}), which returns the batch's ledger-created
   * {@code reservation_id} directly plus the derived status and item counters (ADR-023). This is the
   * single place the path/params/shape live; if the ledger contract differs, adjust here. The
   * documented fallback is the ADR-018 account-reservations path filtered by
   * {@code disbursementBatchId} ({@link #getReservations}).
   *
   * @param callerToken the caller's bearer token (empty/service token for in-process callers)
   * @param batchId the batch id (the driver-controlled {@code batch_id})
   * @return the batch summary ({@code reservationId} may be null until the reservation lands)
   * @throws NotFoundException if the ledger returns 4xx (e.g. unknown batch)
   * @throws InternalServerErrorException if the ledger returns 5xx
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public DisbursementBatchSummaryDto getDisbursementBatch(String callerToken, String batchId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/disbursement-batches/{batchId}", batchId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      throw new NotFoundException("Disbursement batch not found: " + batchId);
                    })
                .onStatus(
                    HttpStatusCode::is5xxServerError,
                    (req, resp) -> {
                      throw new InternalServerErrorException(
                          "Ledger error: " + resp.getStatusCode().value());
                    })
                .body(DisbursementBatchSummaryDto.class));
  }

  /**
   * Creates an account-statement export on the ledger, or joins the one already active for the same
   * resolved window and format.
   *
   * <p>Proxies the ledger's {@code PUT /api/v0/accounts/{accountId}/transaction-exports}. Every
   * parameter is a query param — the ledger's contract has no request body — and every one is
   * forwarded verbatim: the chaos machine re-validates nothing (ADR-033). Format and range-type
   * names travel as strings so a format the ledger learns tomorrow passes through this proxy rather
   * than being rejected by it.
   *
   * <p>The returned {@link LedgerExportResult#created()} carries the ledger's {@code 201} (created)
   * vs {@code 200} (joined the active duplicate) distinction, which the controller echoes.
   *
   * <p>Errors are propagated <strong>faithfully</strong> (ADR-035) — unlike the read methods above,
   * a ledger 400/401/403/404/409 keeps its status and its message. See {@link
   * LedgerStatusPropagation}.
   *
   * @param callerToken the caller's bearer token; the ledger enforces {@code
   *     ledger_account_transactions:export::allow} and its own org scope
   * @param accountId the ledger account to export
   * @param format the artifact format ({@code CSV}/{@code PDF}, parsed case-insensitively)
   * @param rangeType the window kind ({@code DAILY}…{@code CUSTOM}, parsed case-insensitively)
   * @param from the start of the window
   * @param to the exclusive end of the window; required by the ledger only for {@code CUSTOM}
   * @return the created export, or the active duplicate it joined
   * @throws com.softspark.chaos.exception.BadRequestException if the ledger rejects the window
   * @throws com.softspark.chaos.exception.ForbiddenException if the token lacks export authority or
   *     the account is out of its org scope
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerExportResult createExport(
      String callerToken,
      String accountId,
      String format,
      String rangeType,
      Instant from,
      @Nullable Instant to) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () -> {
          var response =
              restClient
                  .put()
                  .uri(
                      uriBuilder -> {
                        var builder =
                            uriBuilder
                                .path("/api/v0/accounts/{id}/transaction-exports")
                                .queryParam("format", format)
                                .queryParam("rangeType", rangeType)
                                .queryParam("from", from);
                        if (to != null) {
                          builder = builder.queryParam("to", to);
                        }
                        return builder.build(accountId);
                      })
                  .header("Authorization", "Bearer " + token)
                  .retrieve()
                  .onStatus(
                      HttpStatusCode::isError,
                      (req, resp) -> {
                        throw LedgerStatusPropagation.toChaosException(req, resp);
                      })
                  .toEntity(LedgerTransactionExportDto.class);
          boolean created = response.getStatusCode().value() == HttpStatus.CREATED.value();
          return new LedgerExportResult(created, response.getBody());
        });
  }

  /**
   * Fetches a single statement export's status from the ledger.
   *
   * <p>Proxies {@code GET /api/v0/accounts/{accountId}/transaction-exports/{exportId}}. The returned
   * DTO carries the ledger's freshly-minted presigned {@code downloadUrl} when the export is {@code
   * COMPLETED} — that URL is for <strong>server-side use only</strong> and is dropped on the way out
   * by {@link com.softspark.chaos.ledgerproxy.dto.TransactionExportResponse} (ADR-034).
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account the export belongs to
   * @param exportId the export id
   * @return the export, including its (server-side-only) download URL when completed
   * @throws com.softspark.chaos.exception.NotFoundException if the export or account is unknown, or
   *     the export belongs to a different account
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerTransactionExportDto getExport(
      String callerToken, String accountId, String exportId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/accounts/{id}/transaction-exports/{exportId}", accountId, exportId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(LedgerTransactionExportDto.class));
  }

  /**
   * Lists an account's statement exports from the ledger, newest first.
   *
   * <p>Proxies {@code GET /api/v0/accounts/{accountId}/transaction-exports}. {@code page}/{@code
   * pageSize} are forwarded under the ledger's own names and <strong>unclamped</strong> — the ledger
   * caps {@code pageSize} at 100 and 400s beyond it, and that 400 is now the operator's answer
   * rather than a chaos-side surprise.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account whose exports are listed
   * @param status optional lifecycle-status filter (case-insensitive at the ledger)
   * @param format optional format filter (case-insensitive at the ledger)
   * @param page zero-based page number
   * @param pageSize page size (the ledger's name, and its cap)
   * @return a page of exports, newest first
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerTransactionExportDto> listExports(
      String callerToken,
      String accountId,
      @Nullable String status,
      @Nullable String format,
      int page,
      int pageSize) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/api/v0/accounts/{id}/transaction-exports")
                              .queryParam("page", page)
                              .queryParam("pageSize", pageSize);
                      if (status != null && !status.isBlank()) {
                        builder = builder.queryParam("status", status);
                      }
                      if (format != null && !format.isBlank()) {
                        builder = builder.queryParam("format", format);
                      }
                      return builder.build(accountId);
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(
                    new ParameterizedTypeReference<
                        LedgerPageDto<LedgerTransactionExportDto>>() {}));
  }

  /**
   * Cancels a pending or in-progress statement export on the ledger.
   *
   * <p>Proxies {@code DELETE /api/v0/accounts/{accountId}/transaction-exports/{exportId}}. A
   * terminal export ({@code COMPLETED}/{@code FAILED}/{@code CANCELLED}) is a {@code 409}, not a
   * no-op and emphatically not a {@code 404} — the row is visibly on the operator's screen (ADR-035).
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account the export belongs to
   * @param exportId the export id
   * @return the export in its {@code CANCELLED} state
   * @throws com.softspark.chaos.exception.ConflictException if the export is already terminal
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerTransactionExportDto cancelExport(
      String callerToken, String accountId, String exportId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .delete()
                .uri("/api/v0/accounts/{id}/transaction-exports/{exportId}", accountId, exportId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(LedgerTransactionExportDto.class));
  }

  /**
   * Triggers one or all consistency checks on the ledger.
   *
   * <p>Proxies {@code PUT /api/v0/consistency-checks?type=}. When {@code type=ALL}, the ledger
   * creates three checks (one per type); otherwise it creates one. Each check is initially {@code
   * PENDING} and processed asynchronously by the ledger's task queue.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param type the check type ({@code ALL}, {@code ACCOUNT_BALANCE_PROJECTION}, {@code
   *     ENTRY_BALANCE}, {@code SEQUENCE_INTEGRITY}), or {@code null} (defaults to {@code ALL})
   * @return the trigger response (list of triggered checks)
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerConsistencyCheckTriggerDto triggerConsistencyChecks(
      String callerToken, @Nullable String type) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .put()
                .uri(
                    uriBuilder -> {
                      var builder = uriBuilder.path("/api/v0/consistency-checks");
                      if (type != null && !type.isBlank()) {
                        builder = builder.queryParam("type", type);
                      }
                      return builder.build();
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(LedgerConsistencyCheckTriggerDto.class));
  }

  /**
   * Lists consistency checks from the ledger with optional filters.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks?type=&status=&initiatorType=&page=&size=}.
   * All filters are optional; the ledger returns checks newest-first.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param type optional filter by check type
   * @param status optional filter by status
   * @param initiatorType optional filter by initiator type
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of consistency checks
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerConsistencyCheckDto> listConsistencyChecks(
      String callerToken,
      @Nullable String type,
      @Nullable String status,
      @Nullable String initiatorType,
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
                              .path("/api/v0/consistency-checks")
                              .queryParam("page", page)
                              .queryParam("size", size);
                      if (type != null && !type.isBlank()) {
                        builder = builder.queryParam("type", type);
                      }
                      if (status != null && !status.isBlank()) {
                        builder = builder.queryParam("status", status);
                      }
                      if (initiatorType != null && !initiatorType.isBlank()) {
                        builder = builder.queryParam("initiatorType", initiatorType);
                      }
                      return builder.build();
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(
                    new ParameterizedTypeReference<LedgerPageDto<LedgerConsistencyCheckDto>>() {}));
  }

  /**
   * Retrieves a single consistency check from the ledger.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks/{checkId}}.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param checkId the check ID
   * @return the consistency check
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerConsistencyCheckDto getConsistencyCheck(String callerToken, String checkId) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri("/api/v0/consistency-checks/{checkId}", checkId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(LedgerConsistencyCheckDto.class));
  }

  /**
   * Lists discrepancies (findings) for a single consistency check with optional code filter.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks/{checkId}/discrepancies?code=&page=&size=}.
   * The {@code code} filter is optional; the ledger returns findings ordered by detection time.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param checkId the check ID
   * @param code optional filter by discrepancy code
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of discrepancies
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public LedgerPageDto<LedgerConsistencyCheckDiscrepancyDto> listConsistencyCheckDiscrepancies(
      String callerToken, String checkId, @Nullable String code, int page, int size) {
    var token = resolveToken(callerToken);
    return circuitBreaker.execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      var builder =
                          uriBuilder
                              .path("/api/v0/consistency-checks/{checkId}/discrepancies")
                              .queryParam("page", page)
                              .queryParam("size", size);
                      if (code != null && !code.isBlank()) {
                        builder = builder.queryParam("code", code);
                      }
                      return builder.build(checkId);
                    })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw LedgerStatusPropagation.toChaosException(req, resp);
                    })
                .body(
                    new ParameterizedTypeReference<
                        LedgerPageDto<LedgerConsistencyCheckDiscrepancyDto>>() {}));
  }

  /**
   * Cancels a running or pending consistency check.
   *
   * <p>Proxies {@code DELETE /api/v0/consistency-checks/{checkId}}. The ledger will attempt to
   * cancel the check if it is still {@code PENDING} or {@code IN_PROGRESS}. If the check has
   * already completed or failed, the ledger returns an appropriate error status.
   *
   * @param callerToken the caller's bearer token (forwarded or replaced by service token)
   * @param checkId the check ID to cancel
   * @throws CircuitBreakerOpenException if the circuit is open
   */
  public void cancelConsistencyCheck(String callerToken, String checkId) {
    var token = resolveToken(callerToken);
    circuitBreaker.execute(
        () -> {
          restClient
              .delete()
              .uri("/api/v0/consistency-checks/{checkId}", checkId)
              .header("Authorization", "Bearer " + token)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (req, resp) -> {
                    throw LedgerStatusPropagation.toChaosException(req, resp);
                  })
              .toBodilessEntity();
          return null;
        });
  }

  private String resolveToken(String callerToken) {
    if (proxyProperties.forwardToken()) {
      return callerToken != null ? callerToken : "";
    }
    var svc = proxyProperties.serviceToken();
    return svc != null && !svc.isBlank() ? svc : "";
  }
}
