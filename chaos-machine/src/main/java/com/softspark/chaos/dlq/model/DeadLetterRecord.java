package com.softspark.chaos.dlq.model;

import com.softspark.chaos.base.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity projecting one ledger inbound dead letter (ADR-029). Domain-tagged and format-agnostic;
 * deduped by the {@code (dlt_topic, dlt_partition, dlt_offset)} unique key.
 */
@Entity
@Table(name = "dlq")
public class DeadLetterRecord {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "dlt_topic")
  private String dltTopic;

  @Column(name = "dlt_partition")
  private int dltPartition;

  @Column(name = "dlt_offset")
  private long dltOffset;

  @Column(name = "dead_letter_id")
  private String deadLetterId;

  @Column(name = "original_topic")
  private String originalTopic;

  @Column(name = "domain")
  private String domain;

  @Column(name = "source")
  private String source;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "event_id")
  private String eventId;

  @Column(name = "transaction_id")
  private String transactionId;

  @Column(name = "transaction_type")
  private String transactionType;

  @Column(name = "failure_classification")
  private String failureClassification;

  @Column(name = "error_type")
  private String errorType;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "retry_count")
  private Integer retryCount;

  @Column(name = "original_partition")
  private Integer originalPartition;

  @Column(name = "original_offset")
  private Long originalOffset;

  @Column(name = "original_key")
  private String originalKey;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "dead_lettered_at")
  private Instant deadLetteredAt;

  @Column(name = "original_payload_json", columnDefinition = "TEXT")
  private String originalPayloadJson;

  @Column(name = "raw_dlt_json", columnDefinition = "TEXT")
  private String rawDltJson;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "received_at")
  private Instant receivedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDltTopic() {
    return dltTopic;
  }

  public void setDltTopic(String dltTopic) {
    this.dltTopic = dltTopic;
  }

  public int getDltPartition() {
    return dltPartition;
  }

  public void setDltPartition(int dltPartition) {
    this.dltPartition = dltPartition;
  }

  public long getDltOffset() {
    return dltOffset;
  }

  public void setDltOffset(long dltOffset) {
    this.dltOffset = dltOffset;
  }

  public String getDeadLetterId() {
    return deadLetterId;
  }

  public void setDeadLetterId(String deadLetterId) {
    this.deadLetterId = deadLetterId;
  }

  public String getOriginalTopic() {
    return originalTopic;
  }

  public void setOriginalTopic(String originalTopic) {
    this.originalTopic = originalTopic;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(String transactionType) {
    this.transactionType = transactionType;
  }

  public String getFailureClassification() {
    return failureClassification;
  }

  public void setFailureClassification(String failureClassification) {
    this.failureClassification = failureClassification;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getOriginalPartition() {
    return originalPartition;
  }

  public void setOriginalPartition(Integer originalPartition) {
    this.originalPartition = originalPartition;
  }

  public Long getOriginalOffset() {
    return originalOffset;
  }

  public void setOriginalOffset(Long originalOffset) {
    this.originalOffset = originalOffset;
  }

  public String getOriginalKey() {
    return originalKey;
  }

  public void setOriginalKey(String originalKey) {
    this.originalKey = originalKey;
  }

  public Instant getDeadLetteredAt() {
    return deadLetteredAt;
  }

  public void setDeadLetteredAt(Instant deadLetteredAt) {
    this.deadLetteredAt = deadLetteredAt;
  }

  public String getOriginalPayloadJson() {
    return originalPayloadJson;
  }

  public void setOriginalPayloadJson(String originalPayloadJson) {
    this.originalPayloadJson = originalPayloadJson;
  }

  public String getRawDltJson() {
    return rawDltJson;
  }

  public void setRawDltJson(String rawDltJson) {
    this.rawDltJson = rawDltJson;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }
}
