package com.softspark.chaos.batch.dto;

import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.model.BatchRun;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a {@link BatchRun}.
 *
 * @param id the batch run ULID
 * @param flowType the flow type processed in this batch
 * @param filename the original CSV filename; null if uploaded without a name
 * @param total total rows in the batch
 * @param succeeded successfully published rows
 * @param failed failed publish rows
 * @param invalid invalid / parse-error rows
 * @param status the batch run status
 * @param createdAt the batch creation timestamp
 * @param completedAt the batch completion timestamp; null while still running
 */
@RecordBuilder
public record BatchRunResponse(
    String id,
    String flowType,
    @Nullable String filename,
    int total,
    int succeeded,
    int failed,
    int invalid,
    BatchRunStatus status,
    Instant createdAt,
    @Nullable Instant completedAt) {

  /**
   * Maps a {@link BatchRun} entity to a {@link BatchRunResponse}.
   *
   * @param run the entity to map
   * @return the response DTO
   */
  public static BatchRunResponse from(BatchRun run) {
    return new BatchRunResponse(
        run.getId(),
        run.getFlowType(),
        run.getFilename(),
        run.getTotal(),
        run.getSucceeded(),
        run.getFailed(),
        run.getInvalid(),
        run.getStatus(),
        run.getCreatedAt(),
        run.getCompletedAt());
  }
}
