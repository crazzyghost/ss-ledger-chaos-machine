package com.softspark.chaos.run.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.run.dto.RunSummaryResponse;
import com.softspark.chaos.run.dto.RunsQuery;
import com.softspark.chaos.run.service.RunSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only run-grouped feed that powers the Scenario Runner's <em>Run History</em> tab (ADR-031).
 *
 * <p>Each row is a <em>run</em> — a tracked {@code batch_run} or an untracked {@code correlation_id}
 * group of {@code publish_record}s — ordered newest-first. Drilling into a run reuses the existing
 * {@code GET /api/v0/history} with {@code batchId} (tracked) or {@code correlationId} (untracked);
 * this endpoint adds no child route. Authentication is enforced globally (the bearer token), like the
 * rest of {@code /api/v0}.
 */
@RestController
@RequestMapping("/api/v0/runs")
@Tag(name = "Runs", description = "Run-grouped published-event feed")
public class RunController {

  private final RunSummaryService runSummaryService;

  /**
   * Constructs the controller.
   *
   * @param runSummaryService the run-summary query service
   */
  public RunController(RunSummaryService runSummaryService) {
    this.runSummaryService = runSummaryService;
  }

  /**
   * Lists runs newest-first with optional filters and chaos pagination.
   *
   * @param from optional inclusive lower bound on last activity (ISO-8601 instant)
   * @param to optional inclusive upper bound on last activity (ISO-8601 instant)
   * @param kind optional run-kind filter (case-insensitive)
   * @param page zero-based page number (default 0)
   * @param size page size (default 20, clamped to a max of 100)
   * @return a page of run summaries
   */
  @GetMapping
  @Operation(
      summary = "List runs",
      description =
          "Run-grouped feed over batch_run (tracked) and correlation-grouped publish_record "
              + "(untracked), ordered by last activity descending")
  public ResponseEntity<PageResponse<RunSummaryResponse>> listRuns(
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(required = false) @Nullable String kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(runSummaryService.listRuns(new RunsQuery(from, to, kind, page, size)));
  }
}
