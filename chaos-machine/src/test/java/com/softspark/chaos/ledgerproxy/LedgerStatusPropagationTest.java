package com.softspark.chaos.ledgerproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.softspark.chaos.exception.BadGatewayException;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.ForbiddenException;
import com.softspark.chaos.exception.HttpException;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.exception.UnauthorizedException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/**
 * The ADR-035 status matrix, asserted end to end on the helper: a ledger 403 must not arrive as a
 * 404, a 409 must not arrive as a 404, and a body the ledger did not send — or sent malformed —
 * must never turn an honest 4xx into a 500.
 */
@DisplayName("LedgerStatusPropagation")
class LedgerStatusPropagationTest {

  private static final MockClientHttpRequest REQUEST =
      new MockClientHttpRequest(HttpMethod.PUT, URI.create("http://ledger.test/api/v0/accounts"));

  private static RuntimeException translate(int status, String body) throws IOException {
    var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    return LedgerStatusPropagation.toChaosException(REQUEST, response);
  }

  private static String apiError(String message) {
    return "{\"requestId\":\"req-1\",\"message\":\"" + message + "\",\"errors\":[]}";
  }

  @ParameterizedTest(name = "ledger {0} -> chaos {0}")
  @CsvSource({"400", "401", "403", "404", "409", "418", "500", "503"})
  @DisplayName("preserves the ledger's status code")
  void preservesStatus(int ledgerStatus) throws IOException {
    var exception = translate(ledgerStatus, apiError("something the ledger said"));

    assertThat(exception).isInstanceOf(HttpException.class);
    int expected =
        switch (ledgerStatus) {
          case 418 -> 502; // an unmapped 4xx is a loud 502, not a silent misclassification
          case 503 -> 500; // 5xx collapses to the chaos machine's own internal error
          default -> ledgerStatus;
        };
    assertThat(((HttpException) exception).getStatusCode()).isEqualTo(expected);
  }

  @Test
  @DisplayName("maps each status onto the exception type the operator's UI reacts to")
  void mapsToTheRightExceptionTypes() throws IOException {
    assertThat(translate(400, apiError("bad"))).isInstanceOf(BadRequestException.class);
    assertThat(translate(401, apiError("expired"))).isInstanceOf(UnauthorizedException.class);
    assertThat(translate(403, apiError("nope"))).isInstanceOf(ForbiddenException.class);
    assertThat(translate(404, apiError("gone"))).isInstanceOf(NotFoundException.class);
    assertThat(translate(409, apiError("terminal"))).isInstanceOf(ConflictException.class);
    assertThat(translate(418, apiError("teapot"))).isInstanceOf(BadGatewayException.class);
    assertThat(translate(500, apiError("boom"))).isInstanceOf(InternalServerErrorException.class);
  }

  @Test
  @DisplayName("carries the ledger's own message through — the operator's only clue")
  void carriesTheLedgerMessage() throws IOException {
    var exception =
        translate(400, apiError("the resolved export window exceeds the maximum of 366 days"));

    assertThat(exception).hasMessage("the resolved export window exceeds the maximum of 366 days");
  }

  @ParameterizedTest(name = "body: {0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "''",
        "'   '",
        "'<html><body>502 Bad Gateway</body></html>'",
        "'{\"unexpected\":\"shape\"}'",
        "'{\"message\":null}'",
        "'{ truncated json'",
        "'[\"an\",\"array\"]'",
      })
  @DisplayName("an absent or unparseable body still yields the right status, never a parse error")
  void unparseableBodyKeepsTheStatus(String body) {
    assertThatCode(
            () -> {
              var exception = translate(403, body);
              assertThat(exception).isInstanceOf(ForbiddenException.class);
              assertThat(exception.getMessage()).isNotBlank();
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("falls back to a generic per-status message when the ledger says nothing useful")
  void fallsBackToGenericMessages() throws IOException {
    assertThat(translate(400, "")).hasMessage("The ledger rejected the export request");
    assertThat(translate(401, "")).hasMessage("Ledger authentication failed");
    assertThat(translate(403, ""))
        .hasMessage("Not authorized to export statements for this account");
    assertThat(translate(404, "")).hasMessage("Export not found");
    assertThat(translate(409, ""))
        .hasMessage("The export is no longer in a state that allows this operation");
    assertThat(translate(500, "")).hasMessage("Ledger error: 500");
  }
}
