package com.softspark.chaos.history.dto;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Query parameters for the publish history endpoint.
 *
 * @param eventType optional filter by event type
 * @param sourceVaId optional filter by source VA id
 * @param destinationVaId optional filter by destination VA id
 * @param correlationId optional filter by correlation id
 * @param batchId optional filter by batch run id
 * @param status optional filter by status ({@code "PUBLISHED"} or {@code "FAILED"})
 * @param from optional start of the created-at range
 * @param to optional end of the created-at range
 * @param page zero-based page number (default 0)
 * @param size page size (default 20, max 100)
 */
public record HistoryQuery(
    @Nullable String eventType,
    @Nullable String sourceVaId,
    @Nullable String destinationVaId,
    @Nullable String correlationId,
    @Nullable String batchId,
    @Nullable String status,
    @Nullable Instant from,
    @Nullable Instant to,
    int page,
    int size) {

  public HistoryQuery {
    if (page < 0) {
      page = 0;
    }
    size = Math.min(Math.max(size, 1), 100);
  }
}
