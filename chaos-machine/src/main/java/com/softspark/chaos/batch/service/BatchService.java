package com.softspark.chaos.batch.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.dto.BatchRowResponse;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.exception.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side queries for tracked runs ({@code batch_run} / {@code batch_row}).
 *
 * <p>Backs the run-detail / live-progress endpoints ({@code GET /api/v0/batches/{id}} and
 * {@code /{id}/rows}) for <em>all</em> run kinds (N-Times-async, lifecycle-random,
 * batch-disbursement, and historical CSV runs). The CSV <em>ingest</em> path — file parsing and run
 * creation — was retired in Phase 021 (ADR-031), so this service no longer imports any CSV type or
 * the async executor; it is a pure reader. The run list moved to {@code GET /api/v0/runs}.
 */
@Service
public class BatchService {

  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;

  /**
   * Constructs the service.
   *
   * @param batchRunRepository the tracked-run repository
   * @param batchRowRepository the run-row repository
   */
  public BatchService(
      BatchRunRepository batchRunRepository, BatchRowRepository batchRowRepository) {
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
  }

  /**
   * Retrieves a tracked run by id.
   *
   * @param id the run id
   * @return the run response
   * @throws NotFoundException if not found
   */
  @Transactional(readOnly = true)
  public BatchRunResponse getRunById(String id) {
    return batchRunRepository
        .findById(id)
        .map(BatchRunResponse::from)
        .orElseThrow(() -> new NotFoundException("Batch run not found: " + id));
  }

  /**
   * Returns a paginated list of rows for a run.
   *
   * @param batchId the run id
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of rows
   * @throws NotFoundException if the run does not exist
   */
  @Transactional(readOnly = true)
  public PageResponse<BatchRowResponse> getRows(String batchId, int page, int size) {
    if (!batchRunRepository.existsById(batchId)) {
      throw new NotFoundException("Batch run not found: " + batchId);
    }
    var pageable = PageRequest.of(page, size);
    var result = batchRowRepository.findByBatchIdOrderByRowNumber(batchId, pageable);
    var items = result.getContent().stream().map(BatchRowResponse::from).toList();
    return new PageResponse<>(
        items, result.getNumber(), result.getSize(), result.getTotalElements());
  }
}
