package com.softspark.chaos.batch.dto;

import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.model.BatchRow;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a single {@link BatchRow}.
 *
 * @param id the row ULID
 * @param batchId the owning batch run id
 * @param rowNumber the one-based row number in the CSV
 * @param status the row processing status
 * @param eventId the published event id; null until successfully published
 * @param error the error message; null on success
 * @param createdAt the row creation timestamp
 */
@RecordBuilder
public record BatchRowResponse(
    String id,
    String batchId,
    int rowNumber,
    BatchRowStatus status,
    @Nullable String eventId,
    @Nullable String error,
    Instant createdAt) {

  /**
   * Maps a {@link BatchRow} entity to a {@link BatchRowResponse}.
   *
   * @param row the entity to map
   * @return the response DTO
   */
  public static BatchRowResponse from(BatchRow row) {
    return new BatchRowResponse(
        row.getId(),
        row.getBatchId(),
        row.getRowNumber(),
        row.getStatus(),
        row.getEventId(),
        row.getError(),
        row.getCreatedAt());
  }
}
