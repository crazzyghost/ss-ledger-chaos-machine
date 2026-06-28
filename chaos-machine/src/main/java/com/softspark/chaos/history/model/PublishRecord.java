package com.softspark.chaos.history.model;

import com.softspark.chaos.base.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity recording a single Kafka publish attempt by the flow engine.
 *
 * <p>Does not extend {@link com.softspark.chaos.base.AuditableEntity} because there is no
 * {@code updated_at} column — publish records are immutable once written.
 */
@Entity
@Table(name = "publish_record")
public class PublishRecord {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "event_id")
  private String eventId;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "topic")
  private String topic;

  @Column(name = "source")
  private String source;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "transaction_request_id")
  private String transactionRequestId;

  @Column(name = "source_va_id")
  private String sourceVaId;

  @Column(name = "destination_va_id")
  private String destinationVaId;

  @Column(name = "status")
  private String status;

  @Column(name = "intentional_failure")
  private boolean intentionalFailure;

  @Column(name = "chaos_strategy")
  private String chaosStrategy;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "batch_id")
  private String batchId;

  @Column(name = "batch_row_id")
  private String batchRowId;

  @Column(name = "kafka_offset")
  private Long kafkaOffset;

  @Column(name = "kafka_partition")
  private Integer kafkaPartition;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "created_at")
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getTransactionRequestId() {
    return transactionRequestId;
  }

  public void setTransactionRequestId(String transactionRequestId) {
    this.transactionRequestId = transactionRequestId;
  }

  public String getSourceVaId() {
    return sourceVaId;
  }

  public void setSourceVaId(String sourceVaId) {
    this.sourceVaId = sourceVaId;
  }

  public String getDestinationVaId() {
    return destinationVaId;
  }

  public void setDestinationVaId(String destinationVaId) {
    this.destinationVaId = destinationVaId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isIntentionalFailure() {
    return intentionalFailure;
  }

  public void setIntentionalFailure(boolean intentionalFailure) {
    this.intentionalFailure = intentionalFailure;
  }

  public String getChaosStrategy() {
    return chaosStrategy;
  }

  public void setChaosStrategy(String chaosStrategy) {
    this.chaosStrategy = chaosStrategy;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public String getBatchRowId() {
    return batchRowId;
  }

  public void setBatchRowId(String batchRowId) {
    this.batchRowId = batchRowId;
  }

  public Long getKafkaOffset() {
    return kafkaOffset;
  }

  public void setKafkaOffset(Long kafkaOffset) {
    this.kafkaOffset = kafkaOffset;
  }

  public Integer getKafkaPartition() {
    return kafkaPartition;
  }

  public void setKafkaPartition(Integer kafkaPartition) {
    this.kafkaPartition = kafkaPartition;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
