package com.softspark.chaos.base;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;

/**
 * Base entity with audit timestamps.
 *
 * <p>Provides {@code createdAt} and {@code updatedAt} fields that are automatically set on persist
 * and update operations. All domain entities should extend this class.
 *
 * <p>Timestamps are stored as ISO-8601 text via {@link InstantStringConverter} to ensure
 * compatibility with the SQLite community dialect, which does not natively support JDBC timestamp
 * parsing from epoch-millis strings.
 */
@MappedSuperclass
public abstract class AuditableEntity {

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Convert(converter = InstantStringConverter.class)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditableEntity that)) return false;
    return Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createdAt, updatedAt);
  }
}
