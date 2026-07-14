package com.softspark.chaos.ledgerproxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.ApiError;
import com.softspark.chaos.exception.BadGatewayException;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.ForbiddenException;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.exception.UnauthorizedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * Translates a ledger error response into the matching chaos exception, preserving the ledger's
 * status code and its own error message (ADR-035).
 *
 * <p>This is the <strong>export proxy's</strong> error convention, and the intended target
 * convention for the whole proxy. The read methods on {@link LedgerClient} still collapse every 4xx
 * to a {@link NotFoundException} — defensible where the only realistic client error is "no such
 * account", and a lie here, where the ledger's 400/401/403/404/409 mean five different things an
 * operator must be able to tell apart (a 403 on a SYSTEM account and a 409 on an already-finished
 * export are the two failures a chaos operator hits first). Retrofitting the read methods is a
 * follow-up; do not apply the collapse-to-404 pattern to a new export method.
 *
 * <p>Parsing the ledger's error body is strictly best-effort: an absent, truncated, HTML or
 * otherwise unparseable body falls back to a generic per-status message. A parse failure must never
 * mask the status — that would turn an honest 403 into a 500.
 */
final class LedgerStatusPropagation {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private LedgerStatusPropagation() {}

  /**
   * Maps an error response from the ledger onto the chaos exception carrying the same status.
   *
   * <p>Intended as the {@code onStatus(HttpStatusCode::isError, …)} handler on the export calls:
   *
   * <pre>{@code
   * .onStatus(HttpStatusCode::isError, (req, resp) -> {
   *   throw LedgerStatusPropagation.toChaosException(req, resp);
   * })
   * }</pre>
   *
   * @param request the outbound ledger request (unused; part of the {@code onStatus} handler
   *     contract)
   * @param response the ledger's error response; its body is read once
   * @return the chaos exception to throw — 400/401/403/404/409 keep their meaning, any other 4xx
   *     becomes a {@link BadGatewayException} (a loud default beats a silent misclassification), and
   *     5xx becomes an {@link InternalServerErrorException}
   * @throws IOException if the response status cannot be read
   */
  static RuntimeException toChaosException(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    int status = response.getStatusCode().value();
    String message = messageFrom(response, status);

    return switch (status) {
      case 400 -> new BadRequestException(message);
      case 401 -> new UnauthorizedException(message);
      case 403 -> new ForbiddenException(message);
      case 404 -> new NotFoundException(message);
      case 409 -> new ConflictException(message);
      default ->
          status >= 500
              ? new InternalServerErrorException(message)
              : new BadGatewayException(message);
    };
  }

  /**
   * Reads the ledger's {@link ApiError} body for its message, falling back to a generic per-status
   * message on any failure. Never throws.
   */
  private static String messageFrom(ClientHttpResponse response, int status) {
    try {
      var body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
      if (!body.isBlank()) {
        var error = MAPPER.readValue(body, ApiError.class);
        if (error.message() != null && !error.message().isBlank()) {
          return error.message();
        }
      }
    } catch (Exception e) {
      // Body absent, truncated, HTML, or not an ApiError — the status is what matters.
    }
    return genericMessage(status);
  }

  private static String genericMessage(int status) {
    return switch (status) {
      case 400 -> "The ledger rejected the export request";
      case 401 -> "Ledger authentication failed";
      case 403 -> "Not authorized to export statements for this account";
      case 404 -> "Export not found";
      case 409 -> "The export is no longer in a state that allows this operation";
      default ->
          status >= 500
              ? "Ledger error: " + status
              : "Ledger returned an unexpected status: " + status;
    };
  }
}
