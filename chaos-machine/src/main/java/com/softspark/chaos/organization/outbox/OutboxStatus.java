package com.softspark.chaos.organization.outbox;

/**
 * Lifecycle status of a transactional outbox row.
 *
 * <ul>
 *   <li>{@link #PENDING} — enqueued, awaiting publication (retried on the next relay tick).
 *   <li>{@link #PUBLISHED} — successfully published to Kafka and acknowledged by the broker.
 *   <li>{@link #FAILED} — exhausted the maximum publish attempts; retained for inspection.
 * </ul>
 */
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
