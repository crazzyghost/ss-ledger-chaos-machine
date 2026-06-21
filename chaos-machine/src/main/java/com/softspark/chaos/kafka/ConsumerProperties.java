package com.softspark.chaos.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the chaos machine's Kafka consumer infrastructure.
 *
 * <p>Bound from the {@code chaos.kafka.consumer.*} prefix. These control the first (and currently
 * only) consumer in the service — the {@code ledger.account.created} projection listener. The
 * {@link #enabled()} flag gates listener-container startup so the consumer can be disabled in
 * environments that should not project ledger accounts.
 *
 * @param enabled          whether consumer listener containers should start (default {@code true})
 * @param groupId          the Kafka consumer group id (default {@code chaos-machine})
 * @param concurrency      number of concurrent consumer threads per listener (default {@code 1})
 * @param autoOffsetReset  the {@code auto.offset.reset} policy (default {@code earliest})
 * @param maxAttempts      total delivery attempts before a record is dead-lettered (default 3)
 * @param backoffInitialMs initial retry back-off in milliseconds (default 1000)
 * @param backoffMultiplier exponential back-off multiplier (default 2.0)
 */
@ConfigurationProperties(prefix = "chaos.kafka.consumer")
public record ConsumerProperties(
    Boolean enabled,
    String groupId,
    Integer concurrency,
    String autoOffsetReset,
    Integer maxAttempts,
    Long backoffInitialMs,
    Double backoffMultiplier) {

  /** Applies defaults for any property left unset. */
  public ConsumerProperties {
    enabled = enabled == null ? Boolean.TRUE : enabled;
    groupId = groupId == null || groupId.isBlank() ? "chaos-machine" : groupId;
    concurrency = concurrency == null || concurrency < 1 ? 1 : concurrency;
    autoOffsetReset =
        autoOffsetReset == null || autoOffsetReset.isBlank() ? "earliest" : autoOffsetReset;
    maxAttempts = maxAttempts == null || maxAttempts < 1 ? 3 : maxAttempts;
    backoffInitialMs = backoffInitialMs == null || backoffInitialMs < 0 ? 1000L : backoffInitialMs;
    backoffMultiplier =
        backoffMultiplier == null || backoffMultiplier < 1.0 ? 2.0 : backoffMultiplier;
  }
}
