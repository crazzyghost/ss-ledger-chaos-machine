package com.softspark.chaos.consistencycheck.model;

import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.base.LocalDateTimeStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * JPA entity for the reconciliation_mismatch projection table.
 *
 * <p>Each row represents a {@code ledger.reconciliation.mismatch} event consumed from Kafka. The
 * table is append-only and serves as a toast-notification key for the chaos UI. The {@code
 * check_id} column is UNIQUE; replayed events are deduplicated by SQLite's {@code INSERT OR IGNORE}.
 */
@Entity
@Table(name = "reconciliation_mismatch")
public class ReconciliationMismatch {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "check_id", unique = true, nullable = false)
  private String checkId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "initiator_type", nullable = false)
  private String initiatorType;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "as_of", nullable = false)
  private Instant asOf;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "initiated_at", nullable = false)
  private Instant initiatedAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  @Column(name = "discrepancy_count", nullable = false)
  private int discrepancyCount;

  @Convert(converter = LocalDateTimeStringConverter.class)
  @Column(name = "consumed_at", nullable = false)
  private LocalDateTime consumedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCheckId() {
    return checkId;
  }

  public void setCheckId(String checkId) {
    this.checkId = checkId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getInitiatorType() {
    return initiatorType;
  }

  public void setInitiatorType(String initiatorType) {
    this.initiatorType = initiatorType;
  }

  public Instant getAsOf() {
    return asOf;
  }

  public void setAsOf(Instant asOf) {
    this.asOf = asOf;
  }

  public Instant getInitiatedAt() {
    return initiatedAt;
  }

  public void setInitiatedAt(Instant initiatedAt) {
    this.initiatedAt = initiatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public int getDiscrepancyCount() {
    return discrepancyCount;
  }

  public void setDiscrepancyCount(int discrepancyCount) {
    this.discrepancyCount = discrepancyCount;
  }

  public LocalDateTime getConsumedAt() {
    return consumedAt;
  }

  public void setConsumedAt(LocalDateTime consumedAt) {
    this.consumedAt = consumedAt;
  }
}
