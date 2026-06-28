package com.softspark.chaos.reservation.model;

import com.softspark.chaos.base.BigDecimalStringConverter;
import com.softspark.chaos.base.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stateful JPA entity tracking a single reservation across its lifecycle (ADR-028), keyed by the
 * natural {@code reservation_id}. Unlike the other projections this row is <em>mutable</em>: status
 * advances monotonically as {@code created}/{@code released} events arrive.
 */
@Entity
@Table(name = "reservation")
public class Reservation {

  @Id
  @Column(name = "reservation_id")
  private String reservationId;

  @Column(name = "account_id")
  private String accountId;

  @Column(name = "transaction_id")
  private String transactionId;

  @Column(name = "reservation_type")
  private String reservationType;

  @Column(name = "disbursement_batch_id")
  private String disbursementBatchId;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "status")
  private String status;

  @Column(name = "created_event_id")
  private String createdEventId;

  @Column(name = "last_event_id")
  private String lastEventId;

  @Column(name = "release_event_count")
  private int releaseEventCount;

  @Column(name = "tenant_id")
  private String tenantId;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "created_at")
  private Instant createdAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "terminal_at")
  private Instant terminalAt;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

  public String getReservationId() {
    return reservationId;
  }

  public void setReservationId(String reservationId) {
    this.reservationId = reservationId;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getReservationType() {
    return reservationType;
  }

  public void setReservationType(String reservationType) {
    this.reservationType = reservationType;
  }

  public String getDisbursementBatchId() {
    return disbursementBatchId;
  }

  public void setDisbursementBatchId(String disbursementBatchId) {
    this.disbursementBatchId = disbursementBatchId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCreatedEventId() {
    return createdEventId;
  }

  public void setCreatedEventId(String createdEventId) {
    this.createdEventId = createdEventId;
  }

  public String getLastEventId() {
    return lastEventId;
  }

  public void setLastEventId(String lastEventId) {
    this.lastEventId = lastEventId;
  }

  public int getReleaseEventCount() {
    return releaseEventCount;
  }

  public void setReleaseEventCount(int releaseEventCount) {
    this.releaseEventCount = releaseEventCount;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getTerminalAt() {
    return terminalAt;
  }

  public void setTerminalAt(Instant terminalAt) {
    this.terminalAt = terminalAt;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }
}
