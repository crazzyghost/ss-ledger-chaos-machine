package com.softspark.chaos.batch.model;

import com.softspark.chaos.base.InstantStringConverter;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.enumeration.RunKind;
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
 * JPA entity representing a CSV batch publishing run.
 *
 * <p>Does not extend {@link com.softspark.chaos.base.AuditableEntity} — timestamps are managed
 * manually and there is no {@code updated_at} column.
 */
@Entity
@Table(name = "batch_run")
public class BatchRun {

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "flow_type")
  private String flowType;

  @Column(name = "filename")
  @Nullable
  private String filename;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind")
  private RunKind kind = RunKind.CSV;

  /** Pacing name for N-Times runs ({@code BURST}/{@code LINEAR}/{@code RANDOM}); null for CSV. */
  @Column(name = "pacing")
  @Nullable
  private String pacing;

  /** Execution mode name for N-Times runs ({@code SYNC}/{@code ASYNC}); null for CSV. */
  @Column(name = "mode")
  @Nullable
  private String mode;

  @Column(name = "total")
  private int total;

  @Column(name = "succeeded")
  private int succeeded;

  @Column(name = "failed")
  private int failed;

  @Column(name = "invalid")
  private int invalid;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private BatchRunStatus status;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "created_at")
  private Instant createdAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "completed_at")
  private Instant completedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFlowType() {
    return flowType;
  }

  public void setFlowType(String flowType) {
    this.flowType = flowType;
  }

  @Nullable
  public String getFilename() {
    return filename;
  }

  public void setFilename(@Nullable String filename) {
    this.filename = filename;
  }

  public RunKind getKind() {
    return kind;
  }

  public void setKind(RunKind kind) {
    this.kind = kind;
  }

  @Nullable
  public String getPacing() {
    return pacing;
  }

  public void setPacing(@Nullable String pacing) {
    this.pacing = pacing;
  }

  @Nullable
  public String getMode() {
    return mode;
  }

  public void setMode(@Nullable String mode) {
    this.mode = mode;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getSucceeded() {
    return succeeded;
  }

  public void setSucceeded(int succeeded) {
    this.succeeded = succeeded;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }

  public int getInvalid() {
    return invalid;
  }

  public void setInvalid(int invalid) {
    this.invalid = invalid;
  }

  public BatchRunStatus getStatus() {
    return status;
  }

  public void setStatus(BatchRunStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
