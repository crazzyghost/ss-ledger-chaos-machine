package com.softspark.chaos.organization.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the organization onboarding outbox relay.
 *
 * @param enabled        whether the relay is active (default {@code true})
 * @param pollIntervalMs the relay poll interval in milliseconds (default {@code 1000})
 * @param batchSize      the maximum number of rows claimed per tick (default {@code 50})
 * @param maxAttempts    the publish-attempt cap after which a row is marked
 *                       {@link OutboxStatus#FAILED} (default {@code 5})
 */
@ConfigurationProperties(prefix = "chaos.organization.outbox")
public record OutboxProperties(
    boolean enabled, long pollIntervalMs, int batchSize, int maxAttempts) {

  /**
   * Applies defaults for any unbound properties.
   *
   * @param enabled        whether the relay is active
   * @param pollIntervalMs the relay poll interval in milliseconds
   * @param batchSize      the maximum number of rows claimed per tick
   * @param maxAttempts    the publish-attempt cap before a row is marked failed
   */
  public OutboxProperties {
    if (pollIntervalMs <= 0) {
      pollIntervalMs = 1000L;
    }
    if (batchSize <= 0) {
      batchSize = 50;
    }
    if (maxAttempts <= 0) {
      maxAttempts = 5;
    }
  }
}
