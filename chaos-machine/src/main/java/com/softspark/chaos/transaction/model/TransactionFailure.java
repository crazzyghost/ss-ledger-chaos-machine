package com.softspark.chaos.transaction.model;

import com.softspark.chaos.base.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity projecting a single {@code ledger.transaction.failed} event.
 *
 * <p>Immutable once written (a transaction fails at most once per recording), so it does not extend
 * {@link com.softspark.chaos.base.AuditableEntity} — there is no {@code updated_at}. Timestamps are
 * stored as ISO-8601 TEXT via {@link InstantStringConverter}, matching {@code publish_record}.
 */
@Entity
@Table(name = "transaction_failure")
public class TransactionFailure {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "event_id")
  private String eventId;

  @Column(name = "transaction_request_id")
  private String transactionRequestId;

  @Column(name = "ledger_transaction_id")
  private String ledgerTransactionId;

  @Column(name = "transaction_type")
  private String transactionType;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "ledger_correlation_id")
  private String ledgerCorrelationId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "tenant_id")
  private String tenantId;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "occurred_at")
  private Instant occurredAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "received_at")
  private Instant receivedAt;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

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

  public String getTransactionRequestId() {
    return transactionRequestId;
  }

  public void setTransactionRequestId(String transactionRequestId) {
    this.transactionRequestId = transactionRequestId;
  }

  public String getLedgerTransactionId() {
    return ledgerTransactionId;
  }

  public void setLedgerTransactionId(String ledgerTransactionId) {
    this.ledgerTransactionId = ledgerTransactionId;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(String transactionType) {
    this.transactionType = transactionType;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public void setFailureCode(String failureCode) {
    this.failureCode = failureCode;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public String getLedgerCorrelationId() {
    return ledgerCorrelationId;
  }

  public void setLedgerCorrelationId(String ledgerCorrelationId) {
    this.ledgerCorrelationId = ledgerCorrelationId;
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

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }
}
