package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.config.LedgerProperties;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for provisioning system accounts in the ledger service.
 *
 * <p>Implements idempotent account creation: a 409 Conflict response triggers a lookup by account
 * code so that the locally assigned role maps to the pre-existing ledger account ID. Transient 5xx
 * errors are retried with exponential back-off up to {@link LedgerProperties.Retry#maxAttempts()}.
 */
@Component
public class LedgerAccountProvisioningClient {

  private static final Logger log = LoggerFactory.getLogger(LedgerAccountProvisioningClient.class);

  private final RestClient restClient;
  private final LedgerProperties ledgerProperties;

  /**
   * Constructs the client.
   *
   * @param restClient       the ledger-dedicated {@link RestClient} bean
   * @param ledgerProperties bound ledger configuration
   */
  public LedgerAccountProvisioningClient(
      @Qualifier("ledgerRestClient") RestClient restClient, LedgerProperties ledgerProperties) {
    this.restClient = restClient;
    this.ledgerProperties = ledgerProperties;
  }

  /**
   * Creates an account in the ledger and returns its ledger-assigned account ID.
   *
   * <p>On a 409 Conflict the method performs a code-based lookup and returns the existing account
   * ID, making the operation idempotent. On non-retryable 4xx errors a
   * {@link LedgerProvisioningException} is thrown immediately. On 5xx errors the request is
   * retried with exponential back-off.
   *
   * @param definition      the system account definition to provision
   * @param parentAccountId the ledger account ID of the parent, or {@code null} for root accounts
   * @return the ledger-assigned account ID
   * @throws LedgerProvisioningException if provisioning fails after all retries
   */
  public String createAccount(
      SystemAccountDefinition definition, @Nullable String parentAccountId) {
    var request = buildCreateRequest(definition, parentAccountId);
    try {
      return createAccountWithRetry(request);
    } catch (LedgerProvisioningException e) {
      if (e.getStatusCode() == 409) {
        log.info(
            "Ledger conflict for code '{}' — resolving via code lookup", definition.accountCode());
        return findAccountByCode(definition.accountCode())
            .orElseThrow(
                () ->
                    new LedgerProvisioningException(
                        "Account conflict but code lookup returned no match: "
                            + definition.accountCode(),
                        409));
      }
      throw e;
    }
  }

  /**
   * Looks up an account by its hierarchical code in the SYSTEM-owned account list.
   *
   * @param accountCode the code to search for
   * @return an {@link Optional} containing the ledger account ID, or empty if not found
   * @throws LedgerProvisioningException if the ledger returns an error
   */
  public Optional<String> findAccountByCode(String accountCode) {
    log.debug("Looking up ledger account by code: {}", accountCode);
    var response =
        restClient
            .get()
            .uri(
                uri ->
                    uri.path(ledgerProperties.accountsPath())
                        .queryParam("accountOwnershipType", "SYSTEM")
                        .queryParam("size", 200)
                        .queryParam("page", 0)
                        .build())
            .retrieve()
            .onStatus(
                status -> !status.is2xxSuccessful(),
                (req, resp) -> {
                  throw new LedgerProvisioningException(
                      "Failed to query ledger accounts: " + resp.getStatusCode().value(),
                      resp.getStatusCode().value());
                })
            .body(LedgerAccountsPageResponse.class);

    if (response == null || response.content() == null) {
      return Optional.empty();
    }

    return response.content().stream()
        .filter(a -> accountCode.equals(a.accountCode()))
        .map(LedgerAccountResponse::accountId)
        .findFirst();
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private String createAccountWithRetry(CreateLedgerAccountRequest request) {
    int attempts = 0;
    LedgerProvisioningException lastException = null;

    while (attempts < ledgerProperties.retry().maxAttempts()) {
      try {
        return doCreateAccount(request);
      } catch (LedgerProvisioningException e) {
        if (e.getStatusCode() > 0 && e.getStatusCode() < 500) {
          throw e; // 4xx — do not retry
        }
        lastException = e;
        attempts++;
        if (attempts >= ledgerProperties.retry().maxAttempts()) {
          break;
        }
        long delayMs = ledgerProperties.retry().initialDelayMs() * (long) Math.pow(2, attempts - 1);
        log.warn(
            "Ledger request failed (attempt {}/{}), retrying in {}ms — {}",
            attempts,
            ledgerProperties.retry().maxAttempts(),
            delayMs,
            e.getMessage());
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new LedgerProvisioningException(
              "Interrupted while waiting to retry account creation: " + request.accountCode(), ie);
        }
      }
    }

    throw new LedgerProvisioningException(
        "Exhausted "
            + ledgerProperties.retry().maxAttempts()
            + " retries creating account '"
            + request.accountCode()
            + "'",
        lastException);
  }

  private String doCreateAccount(CreateLedgerAccountRequest request) {
    var response =
        restClient
            .post()
            .uri(ledgerProperties.accountsPath())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(
                HttpStatusCode::is4xxClientError,
                (req, resp) -> {
                  int statusCode = resp.getStatusCode().value();
                  String body = readResponseBody(resp);
                  throw new LedgerProvisioningException(
                      "Ledger client error " + statusCode + ": " + body, statusCode);
                })
            .body(LedgerAccountResponse.class);

    if (response == null || response.accountId() == null) {
      throw new LedgerProvisioningException(
          "Empty or incomplete response from ledger when creating account: "
              + request.accountCode());
    }
    return response.accountId();
  }

  private CreateLedgerAccountRequest buildCreateRequest(
      SystemAccountDefinition def, @Nullable String parentAccountId) {
    return new CreateLedgerAccountRequest(
        def.accountCode(),
        def.accountName(),
        def.accountCategory(),
        def.currency(),
        parentAccountId,
        def.overdraftLimit(),
        def.minimumBalance(),
        def.ownershipType(),
        null);
  }

  private String readResponseBody(ClientHttpResponse response) {
    try (var body = response.getBody()) {
      return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "<could not read response body>";
    }
  }
}
