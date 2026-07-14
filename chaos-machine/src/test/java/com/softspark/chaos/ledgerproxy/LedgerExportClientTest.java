package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.ForbiddenException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.ledgerproxy.LedgerProxyProperties.CircuitBreakerConfig;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
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
 * Tests the four statement-export calls on {@link LedgerClient} against a
 * {@link MockRestServiceServer}-backed {@link RestClient}: the URIs and query params the ledger
 * actually expects, bearer-token forwarding, the {@code 201}-vs-{@code 200} distinction on create,
 * the ledger's list envelope, and faithful error propagation (ADR-035).
 */
@DisplayName("LedgerClient statement exports")
class LedgerExportClientTest {

  private static final String BASE = "http://ledger.test";
  private static final String ACCOUNT_ID = "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44";
  private static final String EXPORT_ID = "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11";
  private static final String EXPORTS_PATH =
      "/api/v0/accounts/" + ACCOUNT_ID + "/transaction-exports";
  private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

  private static final String PENDING_EXPORT_JSON =
      """
      {
        "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
        "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
        "status": "PENDING",
        "format": "CSV",
        "rangeType": "MONTHLY",
        "rangeFrom": "2026-06-01T00:00:00",
        "rangeTo": "2026-07-01T00:00:00",
        "downloadUrl": null,
        "downloadUrlExpiresAt": null,
        "errorCode": null,
        "initiatedBy": null,
        "initiatedAt": "2026-07-13T10:00:00",
        "completedAt": null,
        "cancelledAt": null,
        "erroredAt": null,
        "createdAt": "2026-07-13T10:00:00"
      }
      """;

  private MockRestServiceServer server;
  private LedgerClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    var props = new LedgerProxyProperties(true, null, new CircuitBreakerConfig(5, 2, 30_000));
    client = new LedgerClient(builder.build(), props, 100);
  }

  private static String apiError(String message) {
    return "{\"requestId\":\"req-1\",\"message\":\"" + message + "\",\"errors\":[]}";
  }

  @Nested
  @DisplayName("createExport")
  class CreateExport {

    @Test
    @DisplayName("PUTs the ledger's query-param-only contract with the caller's bearer token")
    void putsQueryParamsAndToken() {
      server
          .expect(requestTo(containsString(EXPORTS_PATH)))
          .andExpect(method(HttpMethod.PUT))
          .andExpect(queryParam("format", "csv"))
          .andExpect(queryParam("rangeType", "monthly"))
          .andExpect(requestTo(containsString("from=2026-06-01T00")))
          .andExpect(requestTo(containsString("to=2026-07-01T00")))
          .andExpect(header("Authorization", "Bearer caller-token"))
          .andRespond(
              withStatus(HttpStatus.CREATED)
                  .body(PENDING_EXPORT_JSON)
                  .contentType(MediaType.APPLICATION_JSON));

      var result = client.createExport("caller-token", ACCOUNT_ID, "csv", "monthly", FROM, TO);

      assertThat(result.created()).isTrue();
      assertThat(result.export().id()).isEqualTo(UUID.fromString(EXPORT_ID));
      assertThat(result.export().status()).isEqualTo("PENDING");
      server.verify();
    }

    @Test
    @DisplayName("omits 'to' entirely when the caller does not supply one")
    void omitsAbsentTo() {
      server
          .expect(requestTo(not(containsString("to="))))
          .andExpect(method(HttpMethod.PUT))
          .andRespond(
              withStatus(HttpStatus.CREATED)
                  .body(PENDING_EXPORT_JSON)
                  .contentType(MediaType.APPLICATION_JSON));

      client.createExport("caller-token", ACCOUNT_ID, "csv", "monthly", FROM, null);

      server.verify();
    }

    @Test
    @DisplayName("a ledger 200 means 'joined the active duplicate', not 'created'")
    void ledger200MeansJoined() {
      server
          .expect(method(HttpMethod.PUT))
          .andRespond(withSuccess(PENDING_EXPORT_JSON, MediaType.APPLICATION_JSON));

      var result = client.createExport("caller-token", ACCOUNT_ID, "csv", "monthly", FROM, TO);

      assertThat(result.created()).isFalse();
      assertThat(result.export().id()).isEqualTo(UUID.fromString(EXPORT_ID));
    }

    @Test
    @DisplayName("a ledger 400 stays a 400, carrying the ledger's field message")
    void ledger400StaysA400() {
      server
          .expect(method(HttpMethod.PUT))
          .andRespond(
              withStatus(HttpStatus.BAD_REQUEST)
                  .body(apiError("the resolved export window exceeds the maximum of 366 days"))
                  .contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(
              () -> client.createExport("caller-token", ACCOUNT_ID, "csv", "custom", FROM, TO))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("the resolved export window exceeds the maximum of 366 days");
    }

    @Test
    @DisplayName("a ledger 403 stays a 403 — not the read proxy's blanket 404")
    void ledger403StaysA403() {
      server
          .expect(method(HttpMethod.PUT))
          .andRespond(
              withStatus(HttpStatus.FORBIDDEN)
                  .body(apiError("requires ledger_account_transactions:export::allow"))
                  .contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(
              () -> client.createExport("caller-token", ACCOUNT_ID, "pdf", "monthly", FROM, null))
          .isInstanceOf(ForbiddenException.class)
          .hasMessage("requires ledger_account_transactions:export::allow");
    }
  }

  @Nested
  @DisplayName("getExport")
  class GetExport {

    @Test
    @DisplayName("GETs the export and keeps the presigned URL server-side")
    void getsTheExport() {
      var completedJson =
          """
          {
            "id": "1f3c1b6e-6a2e-4a4d-93f8-6f6c1f5a1c11",
            "accountId": "9c1a8b70-6ab4-4a67-9b1e-9f0f5a2b3c44",
            "status": "COMPLETED",
            "format": "PDF",
            "rangeType": "MONTHLY",
            "rangeFrom": "2026-06-01T00:00:00",
            "rangeTo": "2026-07-01T00:00:00",
            "downloadUrl": "https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc",
            "downloadUrlExpiresAt": "2026-07-13T10:15:30",
            "initiatedAt": "2026-07-13T10:00:00",
            "completedAt": "2026-07-13T10:00:12",
            "createdAt": "2026-07-13T10:00:00"
          }
          """;
      server
          .expect(requestTo(BASE + EXPORTS_PATH + "/" + EXPORT_ID))
          .andExpect(method(HttpMethod.GET))
          .andExpect(header("Authorization", "Bearer caller-token"))
          .andRespond(withSuccess(completedJson, MediaType.APPLICATION_JSON));

      var export = client.getExport("caller-token", ACCOUNT_ID, EXPORT_ID);

      assertThat(export.status()).isEqualTo("COMPLETED");
      assertThat(export.downloadUrl())
          .isEqualTo(URI.create("https://s3.test/bucket/statement.pdf?X-Amz-Signature=abc"));
      server.verify();
    }

    @Test
    @DisplayName("a ledger 404 is a 404")
    void ledger404StaysA404() {
      server
          .expect(method(HttpMethod.GET))
          .andRespond(
              withStatus(HttpStatus.NOT_FOUND)
                  .body(apiError("export not found"))
                  .contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> client.getExport("caller-token", ACCOUNT_ID, EXPORT_ID))
          .isInstanceOf(NotFoundException.class)
          .hasMessage("export not found");
    }
  }

  @Nested
  @DisplayName("listExports")
  class ListExports {

    @Test
    @DisplayName("forwards page/pageSize under the ledger's names, plus the status/format filters")
    void forwardsPagingAndFilters() {
      var listJson =
          """
          {
            "data": [%s],
            "page": 0,
            "pageSize": 20,
            "total": 1,
            "pages": 1
          }
          """
              .formatted(PENDING_EXPORT_JSON);

      server
          .expect(requestTo(containsString(EXPORTS_PATH)))
          .andExpect(method(HttpMethod.GET))
          .andExpect(queryParam("page", "0"))
          .andExpect(queryParam("pageSize", "20"))
          .andExpect(queryParam("status", "PENDING"))
          .andExpect(queryParam("format", "CSV"))
          .andRespond(withSuccess(listJson, MediaType.APPLICATION_JSON));

      var page = client.listExports("caller-token", ACCOUNT_ID, "PENDING", "CSV", 0, 20);

      assertThat(page.data()).hasSize(1);
      assertThat(page.data().getFirst().status()).isEqualTo("PENDING");
      assertThat(page.page()).isZero();
      assertThat(page.pageSize()).isEqualTo(20);
      assertThat(page.total()).isEqualTo(1L);
      server.verify();
    }

    @Test
    @DisplayName("forwards pageSize unclamped — the ledger owns the cap, and its 400")
    void forwardsPageSizeUnclamped() {
      server
          .expect(queryParam("pageSize", "500"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(
              withStatus(HttpStatus.BAD_REQUEST)
                  .body(apiError("pageSize must be less than or equal to 100"))
                  .contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> client.listExports("caller-token", ACCOUNT_ID, null, null, 0, 500))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("pageSize must be less than or equal to 100");
      server.verify();
    }

    @Test
    @DisplayName("omits blank filters rather than sending empty params")
    void omitsBlankFilters() {
      server
          .expect(requestTo(not(containsString("status="))))
          .andExpect(requestTo(not(containsString("format="))))
          .andRespond(
              withSuccess(
                  "{\"data\":[],\"page\":0,\"pageSize\":0,\"total\":0,\"pages\":0}",
                  MediaType.APPLICATION_JSON));

      var page = client.listExports("caller-token", ACCOUNT_ID, "  ", null, 0, 20);

      assertThat(page.data()).isEmpty();
      server.verify();
    }
  }

  @Nested
  @DisplayName("cancelExport")
  class CancelExport {

    @Test
    @DisplayName("DELETEs the export and returns it CANCELLED")
    void cancels() {
      var cancelledJson = PENDING_EXPORT_JSON.replace("\"PENDING\"", "\"CANCELLED\"");
      server
          .expect(requestTo(BASE + EXPORTS_PATH + "/" + EXPORT_ID))
          .andExpect(method(HttpMethod.DELETE))
          .andExpect(header("Authorization", "Bearer caller-token"))
          .andRespond(withSuccess(cancelledJson, MediaType.APPLICATION_JSON));

      var export = client.cancelExport("caller-token", ACCOUNT_ID, EXPORT_ID);

      assertThat(export.status()).isEqualTo("CANCELLED");
      server.verify();
    }

    @Test
    @DisplayName("cancelling a terminal export is a 409 — the row is right there on screen")
    void terminalCancelIsAConflict() {
      server
          .expect(method(HttpMethod.DELETE))
          .andRespond(
              withStatus(HttpStatus.CONFLICT)
                  .body(apiError("export is already COMPLETED"))
                  .contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> client.cancelExport("caller-token", ACCOUNT_ID, EXPORT_ID))
          .isInstanceOf(ConflictException.class)
          .hasMessage("export is already COMPLETED");
    }
  }
}
