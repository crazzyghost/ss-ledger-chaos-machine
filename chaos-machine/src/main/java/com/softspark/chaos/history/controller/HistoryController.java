package com.softspark.chaos.history.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.history.dto.HistoryQuery;
import com.softspark.chaos.history.dto.PublishRecordResponse;
import com.softspark.chaos.history.service.HistoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying published event history.
 */
@RestController
@RequestMapping("/api/v0/history")
@Tag(name = "History", description = "Published event history")
public class HistoryController {

  private final HistoryQueryService historyQueryService;

  public HistoryController(HistoryQueryService historyQueryService) {
    this.historyQueryService = historyQueryService;
  }

  /**
   * Queries the publish history with optional filters.
   *
   * @param eventType optional filter by event type
   * @param sourceVaId optional filter by source VA id
   * @param destinationVaId optional filter by destination VA id
   * @param correlationId optional filter by correlation id
   * @param batchId optional filter by batch id
   * @param status optional filter by status
   * @param from optional start of the created-at range (ISO-8601)
   * @param to optional end of the created-at range (ISO-8601)
   * @param page zero-based page number (default 0)
   * @param size page size (default 20, max 100)
   * @return paginated list of publish records
   */
  @GetMapping
  @Operation(
      summary = "Query publish history",
      description = "Returns paginated publish records " + "with optional filters")
  public ResponseEntity<PageResponse<PublishRecordResponse>> query(
      @RequestParam(required = false) @Nullable String eventType,
      @RequestParam(required = false) @Nullable String sourceVaId,
      @RequestParam(required = false) @Nullable String destinationVaId,
      @RequestParam(required = false) @Nullable String correlationId,
      @RequestParam(required = false) @Nullable String batchId,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var query =
        new HistoryQuery(
            eventType,
            sourceVaId,
            destinationVaId,
            correlationId,
            batchId,
            status,
            from,
            to,
            page,
            size);
    return ResponseEntity.ok(historyQueryService.queryHistory(query));
  }

  /**
   * Retrieves a single publish record by id.
   *
   * @param id the history record ULID
   * @return the publish record response
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get publish record by id")
  public ResponseEntity<PublishRecordResponse> getById(@PathVariable String id) {
    return ResponseEntity.ok(historyQueryService.getRecord(id));
  }
}
