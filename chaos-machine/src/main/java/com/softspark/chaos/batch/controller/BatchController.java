package com.softspark.chaos.batch.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.dto.BatchRowResponse;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tracked-run detail and live progress.
 *
 * <p>Backs the run-detail / progress page for <em>all</em> run kinds (N-Times-async,
 * lifecycle-random, batch-disbursement, and historical CSV runs). The CSV <em>ingest</em> path
 * ({@code POST /api/v0/batches}) and the run <em>list</em> ({@code GET /api/v0/batches}) were retired
 * in Phase 021 (ADR-031); the list moved to {@code GET /api/v0/runs}. Only the read-by-id endpoints
 * remain.
 */
@RestController
@RequestMapping("/api/v0/batches")
@Tag(name = "Batch", description = "Tracked-run detail and progress")
public class BatchController {

  private final BatchService batchService;

  /**
   * Constructs the controller.
   *
   * @param batchService the tracked-run read service
   */
  public BatchController(BatchService batchService) {
    this.batchService = batchService;
  }

  /**
   * Retrieves a single tracked run by id.
   *
   * @param id the run id
   * @return the run response
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get run by id")
  public ResponseEntity<BatchRunResponse> getBatch(@PathVariable String id) {
    return ResponseEntity.ok(batchService.getRunById(id));
  }

  /**
   * Returns the rows for a tracked run.
   *
   * @param id the run id
   * @param page zero-based page number
   * @param size page size
   * @return paginated list of run rows
   */
  @GetMapping("/{id}/rows")
  @Operation(summary = "Get run rows")
  public ResponseEntity<PageResponse<BatchRowResponse>> getRows(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(batchService.getRows(id, page, size));
  }
}
