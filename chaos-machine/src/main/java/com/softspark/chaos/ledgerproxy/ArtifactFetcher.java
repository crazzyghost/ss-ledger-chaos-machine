package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.exception.BadGatewayException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Fetches a completed statement artifact from the ledger's object store, server-side (ADR-034).
 *
 * <p><strong>The URI handed to {@link #fetch} is a bearer capability.</strong> Anyone holding that
 * presigned URL can fetch the account's statement until it expires — no token, no authorization
 * check. It must therefore <strong>never be logged, at any level; never be put in an exception
 * message; never be returned in a response header or body; and never be cached or persisted.</strong>
 * The ledger refuses to log it (ledger ADR 071) and this class extends that rule across the gateway.
 * Every diagnostic here names the <em>export id</em> instead — that is what the {@code exportId}
 * parameter is for, and it is the only reason it exists.
 *
 * <p>This is why the fetch runs on a <strong>dedicated JDK {@link HttpClient}</strong> rather than
 * the {@code ledgerProxyRestClient}, which would be wrong on four counts:
 *
 * <ol>
 *   <li>It attaches an {@code Authorization} header to every request. The presigned URL <em>is</em>
 *       the credential; a bearer token alongside a SigV4-presigned request is at best ignored and at
 *       worst breaks the signature.
 *   <li>It carries {@code LoggingClientHttpRequestInterceptor}, which logs the request URI — writing
 *       the capability straight into the chaos logs — and {@code readAllBytes()} the response body,
 *       buffering the whole artifact.
 *   <li>It is pinned to the ledger's base URL. The presigned URL is absolute and points at S3.
 *   <li>Its timeouts are sized for a sub-second JSON read, not a multi-megabyte transfer.
 * </ol>
 *
 * <p>The object-store hop deliberately rides <strong>no circuit breaker</strong>. It is a different
 * dependency from the ledger, and tripping the ledger's breaker on an object-store outage would take
 * out the read proxy — accounts, balances, transactions — over a failure that has nothing to do with
 * the ledger. An S3 outage is a {@code 502} on the download route and nowhere else.
 */
@Component
public class ArtifactFetcher {

  private final HttpClient httpClient;
  private final Duration readTimeout;
  private final long maxArtifactBytes;

  /**
   * Constructs the fetcher over a dedicated HTTP client — no base URL, no interceptor, no auth.
   *
   * @param properties the statement-artifact timeouts and size cap
   */
  public ArtifactFetcher(StatementProperties properties) {
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.artifact().connectMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    this.readTimeout = Duration.ofMillis(properties.artifact().readMs());
    this.maxArtifactBytes = properties.maxArtifactBytes();
  }

  /**
   * Performs the server-side {@code GET} against the presigned URL and returns the open, bounded
   * stream of artifact bytes.
   *
   * <p>The URI is passed to the client <strong>exactly as the ledger minted it</strong>. It is never
   * re-encoded, normalized, or rebuilt through a {@code UriBuilder}: its query string is part of the
   * SigV4 signature, and any re-encoding invalidates it.
   *
   * <p>No {@code Authorization} header is set — see the class javadoc.
   *
   * @param presignedUrl the ledger-minted presigned {@code GET} URL; a bearer capability — never log
   *     it
   * @param exportId the export id, used <em>only</em> so failures can be diagnosed without naming
   *     the URL
   * @return the open artifact stream; the caller must {@link ArtifactStream#writeTo} it (which
   *     closes it)
   * @throws BadGatewayException if the object store answers non-2xx, reports an artifact over the
   *     configured maximum, or the transfer cannot be started — never a 200 with an empty body
   */
  public ArtifactStream fetch(URI presignedUrl, String exportId) {
    var request = HttpRequest.newBuilder(presignedUrl).GET().timeout(readTimeout).build();

    HttpResponse<InputStream> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new BadGatewayException(
          "Could not reach the statement artifact store for export " + exportId, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BadGatewayException(
          "Interrupted while retrieving the statement artifact for export " + exportId, e);
    }

    if (response.statusCode() / 100 != 2) {
      close(response.body());
      throw new BadGatewayException(
          "The statement artifact store returned "
              + response.statusCode()
              + " for export "
              + exportId);
    }

    var reportedLength = response.headers().firstValueAsLong("Content-Length");
    Long contentLength = reportedLength.isPresent() ? reportedLength.getAsLong() : null;

    if (contentLength != null && contentLength > maxArtifactBytes) {
      close(response.body());
      throw new BadGatewayException(
          "The statement artifact for export "
              + exportId
              + " is "
              + contentLength
              + " bytes, over the maximum of "
              + maxArtifactBytes);
    }

    return new ArtifactStream(response.body(), contentLength, maxArtifactBytes);
  }

  private static void close(InputStream body) {
    try {
      body.close();
    } catch (IOException ignored) {
      // Nothing useful to do — we are already on a failure path, and the URL must not be logged.
    }
  }
}
