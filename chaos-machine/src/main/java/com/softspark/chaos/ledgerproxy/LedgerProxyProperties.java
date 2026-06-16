package com.softspark.chaos.ledgerproxy;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ledger read-proxy layer.
 *
 * @param forwardToken whether to forward the caller's bearer token to the ledger (default true)
 * @param serviceToken a static service token to use instead when {@code forwardToken=false}
 * @param circuitBreaker circuit-breaker tuning
 */
@ConfigurationProperties(prefix = "ledger.proxy")
public record LedgerProxyProperties(
    boolean forwardToken, @Nullable String serviceToken, CircuitBreakerConfig circuitBreaker) {

  /**
   * Circuit breaker configuration.
   *
   * @param failureThreshold consecutive failures before opening (default 5)
   * @param successThreshold consecutive successes in HALF_OPEN before closing (default 2)
   * @param openDurationMs milliseconds to stay OPEN before attempting a probe (default 30000)
   */
  public record CircuitBreakerConfig(
      int failureThreshold, int successThreshold, long openDurationMs) {}
}
