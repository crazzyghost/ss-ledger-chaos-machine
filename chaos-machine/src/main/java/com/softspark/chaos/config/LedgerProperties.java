package com.softspark.chaos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External configuration for the ledger HTTP client.
 *
 * <p>All properties are bound from the {@code ledger.*} namespace in {@code application.yml} (or
 * environment-variable overrides). Defaults ensure the application starts without additional
 * configuration in a local-dev environment.
 *
 * @param baseUrl      base URL of the ledger service, e.g. {@code http://localhost:27000}
 * @param accountsPath path for the accounts resource, default {@code /api/v0/accounts}
 * @param timeouts     connection and read timeout settings
 * @param retry        retry policy for transient failures
 */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
    String baseUrl, String accountsPath, Timeouts timeouts, Retry retry, String authToken) {

  /**
   * HTTP timeout settings for the ledger client.
   *
   * @param connectMs maximum time in milliseconds to establish a connection
   * @param readMs    maximum time in milliseconds to wait for a response
   */
  public record Timeouts(int connectMs, int readMs) {}

  /**
   * Retry policy applied to transient (5xx) ledger errors.
   *
   * @param maxAttempts    maximum number of attempts (including the first)
   * @param initialDelayMs base delay in milliseconds before the first retry; doubled on each
   *                       subsequent attempt (exponential back-off)
   */
  public record Retry(int maxAttempts, long initialDelayMs) {}
}
