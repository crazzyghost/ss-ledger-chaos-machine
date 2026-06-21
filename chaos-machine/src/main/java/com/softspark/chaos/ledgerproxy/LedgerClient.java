package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreaker;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerAccountDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerBalanceDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionDto;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
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

  /**
   * Constructs the client.
   *
   * @param restClient the ledger proxy {@link RestClient} bean
   * @param proxyProperties the proxy configuration
   */
  public LedgerClient(
      @Qualifier("ledgerProxyRestClient") RestClient restClient,
      LedgerProxyProperties proxyProperties) {
    this.restClient = restClient;
    this.proxyProperties = proxyProperties;
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

  private String resolveToken(String callerToken) {
    if (proxyProperties.forwardToken()) {
      return callerToken != null ? callerToken : "";
    }
    var svc = proxyProperties.serviceToken();
    return svc != null && !svc.isBlank() ? svc : "";
  }
}
