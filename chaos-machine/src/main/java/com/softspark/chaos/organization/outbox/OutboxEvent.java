package com.softspark.chaos.organization.outbox;

import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.base.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Transactional-outbox row for asynchronously published events.
 *
 * <p>A row is inserted in the same database transaction as the aggregate it describes, carrying the
 * fully serialized {@link com.softspark.chaos.kafka.EventEnvelope} as JSON. A scheduled relay later
 * claims {@link OutboxStatus#PENDING} rows in creation order and publishes them, preserving the
 * stored {@code eventId} and idempotency key across retries.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends AuditableEntity {

  @Id
  @Column(name = "outbox_id", nullable = false)
  private String outboxId;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "partition_key")
  private String partitionKey;

  @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
  private String payloadJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OutboxStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "published_at")
  private Instant publishedAt;

  public String getOutboxId() {
    return outboxId;
  }

  public void setOutboxId(String outboxId) {
    this.outboxId = outboxId;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public void setAggregateType(String aggregateType) {
    this.aggregateType = aggregateType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(String aggregateId) {
    this.aggregateId = aggregateId;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPartitionKey() {
    return partitionKey;
  }

  public void setPartitionKey(String partitionKey) {
    this.partitionKey = partitionKey;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public void setStatus(OutboxStatus status) {
    this.status = status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }
}
