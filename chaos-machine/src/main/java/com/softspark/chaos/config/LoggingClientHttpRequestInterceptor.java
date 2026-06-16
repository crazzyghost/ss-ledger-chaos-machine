package com.softspark.chaos.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ClientHttpRequestInterceptor} that logs outbound HTTP requests and their responses at
 * INFO level (request line, response status + duration) and at DEBUG level (headers, bodies).
 *
 * <p>The {@code Authorization} header value is redacted to prevent token leakage in logs.
 * The response body is buffered so it can be both logged and consumed by the caller.
 */
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log =
      LoggerFactory.getLogger(LoggingClientHttpRequestInterceptor.class);

  private final String clientName;

  /**
   * Constructs the interceptor.
   *
   * @param clientName a short label (e.g. "auth", "ledger") included in log lines for tracing
   */
  public LoggingClientHttpRequestInterceptor(String clientName) {
    this.clientName = clientName;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    logRequest(request, body);
    long start = System.currentTimeMillis();

    ClientHttpResponse response = execution.execute(request, body);

    byte[] responseBody = response.getBody().readAllBytes();
    long duration = System.currentTimeMillis() - start;

    logResponse(request, response, responseBody, duration);

    return new BufferedClientHttpResponse(response, responseBody);
  }

  private void logRequest(HttpRequest request, byte[] body) {
    log.info("[{}] --> {} {}", clientName, request.getMethod(), request.getURI());
    if (log.isDebugEnabled()) {
      request
          .getHeaders()
          .forEach(
              (name, values) -> {
                String headerValue =
                    "Authorization".equalsIgnoreCase(name)
                        ? values.stream()
                            .map(v -> v.startsWith("Bearer ") ? "Bearer ***" : "***")
                            .findFirst()
                            .orElse("***")
                        : String.join(", ", values);
                log.debug("[{}]     {}: {}", clientName, name, headerValue);
              });
      if (body != null && body.length > 0) {
        log.debug("[{}]     Body: {}", clientName, new String(body, StandardCharsets.UTF_8));
      }
    }
  }

  private void logResponse(
      HttpRequest request, ClientHttpResponse response, byte[] body, long duration) {
    try {
      log.info(
          "[{}] <-- {} {} ({}ms)",
          clientName,
          response.getStatusCode().value(),
          request.getURI(),
          duration);
      if (log.isDebugEnabled()) {
        response
            .getHeaders()
            .forEach(
                (name, values) ->
                    log.debug("[{}]     {}: {}", clientName, name, String.join(", ", values)));
        if (body.length > 0) {
          log.debug("[{}]     Body: {}", clientName, new String(body, StandardCharsets.UTF_8));
        }
      }
    } catch (IOException e) {
      log.warn("[{}] Could not read response status for logging: {}", clientName, e.getMessage());
    }
  }

  /** Wraps a {@link ClientHttpResponse} with a pre-buffered body so it can be read twice. */
  private static class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private final byte[] body;

    BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
      this.delegate = delegate;
      this.body = body;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
      return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
