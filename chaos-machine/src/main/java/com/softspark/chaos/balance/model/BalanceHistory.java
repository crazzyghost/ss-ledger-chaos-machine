package com.softspark.chaos.balance.model;

import com.softspark.chaos.base.BigDecimalStringConverter;
import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.base.LocalDateTimeStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * JPA entity projecting a single {@code ledger.balance.updated} event — one per affected account
 * (ADR-027). Immutable once written; balances are stored as exact decimal strings.
 */
@Entity
@Table(name = "balance_history")
public class BalanceHistory {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "event_id")
  private String eventId;

  @Column(name = "account_id")
  private String accountId;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "available_balance")
  private BigDecimal availableBalance;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "pending_balance")
  private BigDecimal pendingBalance;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "reserved_balance")
  private BigDecimal reservedBalance;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "total_balance")
  private BigDecimal totalBalance;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "total_debits")
  private BigDecimal totalDebits;

  @Convert(converter = BigDecimalStringConverter.class)
  @Column(name = "total_credits")
  private BigDecimal totalCredits;

  @Column(name = "last_entry_sequence")
  private long lastEntrySequence;

  @Convert(converter = LocalDateTimeStringConverter.class)
  @Column(name = "balance_as_of")
  private LocalDateTime balanceAsOf;

  @Column(name = "currency")
  private String currency;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "ledger_correlation_id")
  private String ledgerCorrelationId;

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

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public void setAvailableBalance(BigDecimal availableBalance) {
    this.availableBalance = availableBalance;
  }

  public BigDecimal getPendingBalance() {
    return pendingBalance;
  }

  public void setPendingBalance(BigDecimal pendingBalance) {
    this.pendingBalance = pendingBalance;
  }

  public BigDecimal getReservedBalance() {
    return reservedBalance;
  }

  public void setReservedBalance(BigDecimal reservedBalance) {
    this.reservedBalance = reservedBalance;
  }

  public BigDecimal getTotalBalance() {
    return totalBalance;
  }

  public void setTotalBalance(BigDecimal totalBalance) {
    this.totalBalance = totalBalance;
  }

  public BigDecimal getTotalDebits() {
    return totalDebits;
  }

  public void setTotalDebits(BigDecimal totalDebits) {
    this.totalDebits = totalDebits;
  }

  public BigDecimal getTotalCredits() {
    return totalCredits;
  }

  public void setTotalCredits(BigDecimal totalCredits) {
    this.totalCredits = totalCredits;
  }

  public long getLastEntrySequence() {
    return lastEntrySequence;
  }

  public void setLastEntrySequence(long lastEntrySequence) {
    this.lastEntrySequence = lastEntrySequence;
  }

  public LocalDateTime getBalanceAsOf() {
    return balanceAsOf;
  }

  public void setBalanceAsOf(LocalDateTime balanceAsOf) {
    this.balanceAsOf = balanceAsOf;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getLedgerCorrelationId() {
    return ledgerCorrelationId;
  }

  public void setLedgerCorrelationId(String ledgerCorrelationId) {
    this.ledgerCorrelationId = ledgerCorrelationId;
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
