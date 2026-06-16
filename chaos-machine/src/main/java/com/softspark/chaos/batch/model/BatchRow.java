package com.softspark.chaos.batch.model;

import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * JPA entity representing a single row in a CSV batch run.
 */
@Entity
@Table(name = "batch_row")
public class BatchRow {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "batch_id")
  private String batchId;

  @Column(name = "row_number")
  private int rowNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private BatchRowStatus status;

  @Column(name = "event_id")
  @Nullable
  private String eventId;

  @Column(name = "error", columnDefinition = "TEXT")
  @Nullable
  private String error;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "created_at")
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public int getRowNumber() {
    return rowNumber;
  }

  public void setRowNumber(int rowNumber) {
    this.rowNumber = rowNumber;
  }

  public BatchRowStatus getStatus() {
    return status;
  }

  public void setStatus(BatchRowStatus status) {
    this.status = status;
  }

  @Nullable
  public String getEventId() {
    return eventId;
  }

  public void setEventId(@Nullable String eventId) {
    this.eventId = eventId;
  }

  @Nullable
  public String getError() {
    return error;
  }

  public void setError(@Nullable String error) {
    this.error = error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
