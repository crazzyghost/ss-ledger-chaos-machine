package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.LedgerProxyProperties.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link LedgerClient#getTrialBalance} against a {@link MockRestServiceServer}-backed
 * {@link RestClient}, covering query-param forwarding (currency present vs omitted), bearer-token
 * forwarding, and the 4xx/5xx → project-exception translation.
 */
@DisplayName("LedgerClient.getTrialBalance")
class LedgerClientTest {

  private static final String BASE = "http://ledger.test";
  private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

  private static final String SAMPLE_JSON =
      """
      {
        "from": "2026-06-01T00:00:00Z",
        "to": "2026-07-01T00:00:00Z",
        "currency": "GHS",
        "totalDebits": "100.00",
        "totalCredits": "100.00",
        "isBalanced": true,
        "numberOfAccounts": 1,
        "accounts": [
          {
            "accountId": "acct-1",
            "accountCode": "ASSET.PLATFORM.FLOAT",
            "accountName": "Platform Float",
            "accountOwnerId": null,
            "accountOwnershipType": "SYSTEM",
            "currency": "GHS",
            "totalDebits": "100.00",
            "totalCredits": "0.00",
            "netMovement": "100.00"
          }
        ]
      }
      """;

  private MockRestServiceServer server;
  private LedgerClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    var restClient = builder.build();
    var props = new LedgerProxyProperties(true, null, new CircuitBreakerConfig(5, 2, 30_000));
    client = new LedgerClient(restClient, props, 100);
  }

  @Test
  @DisplayName("forwards from/to/currency and the bearer token, returning the parsed report")
  void forwardsParamsAndReturnsReport() {
    server
        .expect(requestTo(containsString("/api/v0/reporting/trial-balance")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(requestTo(containsString("from=2026-06-01T00")))
        .andExpect(requestTo(containsString("to=2026-07-01T00")))
        .andExpect(queryParam("currency", "GHS"))
        .andExpect(header("Authorization", "Bearer caller-token"))
        .andRespond(withSuccess(SAMPLE_JSON, MediaType.APPLICATION_JSON));

    var result = client.getTrialBalance("caller-token", FROM, TO, "GHS");

    assertThat(result.from()).isEqualTo(FROM);
    assertThat(result.to()).isEqualTo(TO);
    assertThat(result.currency()).isEqualTo("GHS");
    assertThat(result.isBalanced()).isTrue();
    assertThat(result.numberOfAccounts()).isEqualTo(1);
    assertThat(result.totalDebits()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(result.accounts()).hasSize(1);
    assertThat(result.accounts().get(0).accountOwnerId()).isNull();
    server.verify();
  }

  @Test
  @DisplayName("omits the currency query param when currency is null")
  void omitsCurrencyWhenNull() {
    server
        .expect(requestTo(containsString("/api/v0/reporting/trial-balance")))
        .andExpect(requestTo(not(containsString("currency"))))
        .andRespond(withSuccess(SAMPLE_JSON, MediaType.APPLICATION_JSON));

    var result = client.getTrialBalance("caller-token", FROM, TO, null);

    assertThat(result).isNotNull();
    server.verify();
  }

  @Test
  @DisplayName("omits the currency query param when currency is blank")
  void omitsCurrencyWhenBlank() {
    server
        .expect(requestTo(not(containsString("currency"))))
        .andRespond(withSuccess(SAMPLE_JSON, MediaType.APPLICATION_JSON));

    client.getTrialBalance("caller-token", FROM, TO, "   ");

    server.verify();
  }

  @Test
  @DisplayName("translates a ledger 4xx (e.g. invalid period) to NotFoundException")
  void translates4xxToNotFound() {
    server
        .expect(requestTo(containsString("/api/v0/reporting/trial-balance")))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.getTrialBalance("caller-token", FROM, TO, null))
        .isInstanceOf(NotFoundException.class);
    server.verify();
  }

  @Test
  @DisplayName("translates a ledger 5xx to InternalServerErrorException")
  void translates5xxToInternalServerError() {
    server
        .expect(requestTo(containsString("/api/v0/reporting/trial-balance")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.getTrialBalance("caller-token", FROM, TO, null))
        .isInstanceOf(InternalServerErrorException.class);
    server.verify();
  }

  @Nested
  @DisplayName("exportJournalEntries")
  class ExportJournalEntries {

    private static final String ENTRIES_JSON =
        """
        {
          "data": [
            {
              "lineId": "line-1",
              "journalEntryId": "je-1",
              "postedAt": "2026-06-23T10:00:00Z",
              "entrySequence": 42,
              "accountSequence": 7,
              "accountId": "acct-1",
              "accountCode": "ASSET.PLATFORM.FLOAT",
              "organizationId": "org-1",
              "currency": "GHS",
              "direction": "DEBIT",
              "amount": "100.00",
              "runningBalance": "900.00",
              "transactionRef": "txn-ref-1",
              "entryType": "DISBURSEMENT",
              "narrative": "Disbursement",
              "memo": null,
              "sourceService": "ss-disbursement-service",
              "sourceEventId": "evt-1",
              "metadata": null,
              "siblingLines": [
                {
                  "lineId": "line-2",
                  "accountId": "acct-2",
                  "direction": "CREDIT",
                  "amount": "100.00",
                  "currency": "GHS"
                }
              ]
            }
          ],
          "page": 0,
          "pageSize": 20,
          "total": 1,
          "pages": 1
        }
        """;

    @Test
    @DisplayName("forwards the window/paging + bearer token and parses entries with sibling legs")
    void forwardsWindowAndParsesEntries() {
      server
          .expect(requestTo(containsString("/api/v0/reporting/reconciliation-export")))
          .andExpect(method(HttpMethod.GET))
          .andExpect(requestTo(containsString("from=2026-06-22T00")))
          .andExpect(requestTo(containsString("to=2026-06-29T00")))
          .andExpect(queryParam("page", "0"))
          .andExpect(queryParam("size", "20"))
          .andExpect(header("Authorization", "Bearer caller-token"))
          .andRespond(withSuccess(ENTRIES_JSON, MediaType.APPLICATION_JSON));

      var result =
          client.exportJournalEntries(
              "caller-token",
              Instant.parse("2026-06-22T00:00:00Z"),
              Instant.parse("2026-06-29T00:00:00Z"),
              null,
              null,
              null,
              null,
              0,
              20);

      assertThat(result.data()).hasSize(1);
      assertThat(result.total()).isEqualTo(1L);
      var entry = result.data().get(0);
      assertThat(entry.lineId()).isEqualTo("line-1");
      assertThat(entry.transactionRef()).isEqualTo("txn-ref-1");
      assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
      assertThat(entry.entrySequence()).isEqualTo(42L);
      assertThat(entry.memo()).isNull();
      assertThat(entry.metadata()).isNull();
      assertThat(entry.siblingLines()).hasSize(1);
      assertThat(entry.siblingLines().get(0).accountId()).isEqualTo("acct-2");
      server.verify();
    }

    @Test
    @DisplayName("encodes repeatable accountId params and forwards optional filters")
    void forwardsRepeatableAccountIdAndFilters() {
      server
          .expect(requestTo(containsString("accountId=acct-1")))
          .andExpect(requestTo(containsString("accountId=acct-2")))
          .andExpect(queryParam("entryType", "DISBURSEMENT"))
          .andExpect(queryParam("transactionRef", "txn-ref-1"))
          .andExpect(queryParam("sourceService", "ss-disbursement-service"))
          .andRespond(withSuccess(ENTRIES_JSON, MediaType.APPLICATION_JSON));

      client.exportJournalEntries(
          "caller-token",
          FROM,
          TO,
          java.util.List.of("acct-1", "acct-2"),
          "DISBURSEMENT",
          "txn-ref-1",
          "ss-disbursement-service",
          0,
          20);

      server.verify();
    }

    @Test
    @DisplayName("omits optional filters when null/blank")
    void omitsOptionalFiltersWhenAbsent() {
      server
          .expect(requestTo(not(containsString("accountId"))))
          .andExpect(requestTo(not(containsString("entryType"))))
          .andExpect(requestTo(not(containsString("transactionRef"))))
          .andExpect(requestTo(not(containsString("sourceService"))))
          .andRespond(withSuccess(ENTRIES_JSON, MediaType.APPLICATION_JSON));

      client.exportJournalEntries("caller-token", FROM, TO, null, null, "  ", null, 0, 20);

      server.verify();
    }

    @Test
    @DisplayName("translates a ledger 4xx (e.g. too-wide window) to NotFoundException")
    void translates4xxToNotFound() {
      server
          .expect(requestTo(containsString("/api/v0/reporting/reconciliation-export")))
          .andRespond(withStatus(HttpStatus.BAD_REQUEST));

      assertThatThrownBy(
              () ->
                  client.exportJournalEntries(
                      "caller-token", FROM, TO, null, null, null, null, 0, 20))
          .isInstanceOf(NotFoundException.class);
      server.verify();
    }

    @Test
    @DisplayName("translates a ledger 5xx to InternalServerErrorException")
    void translates5xxToInternalServerError() {
      server
          .expect(requestTo(containsString("/api/v0/reporting/reconciliation-export")))
          .andRespond(withServerError());

      assertThatThrownBy(
              () ->
                  client.exportJournalEntries(
                      "caller-token", FROM, TO, null, null, null, null, 0, 20))
          .isInstanceOf(InternalServerErrorException.class);
      server.verify();
    }
  }
}
