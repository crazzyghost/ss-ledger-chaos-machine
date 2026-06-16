package com.softspark.chaos.batch.service;

import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.model.BatchRow;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.flow.FlowEngine;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.chaos.ChaosOptions;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a batch run asynchronously using a bounded virtual-thread pool.
 *
 * <p>A {@link Semaphore} enforces a configurable parallelism ceiling. An optional token-bucket
 * delay between submissions provides a simple rate-limiting mechanism when a maximum rate per
 * second is specified.
 */
@Component
public class BatchRunner {

  private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

  private final FlowEngine flowEngine;
  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;
  private final int maxWorkers;

  public BatchRunner(
      FlowEngine flowEngine,
      BatchRunRepository batchRunRepository,
      BatchRowRepository batchRowRepository,
      @Value("${chaos.batch.workers:20}") int maxWorkers) {
    this.flowEngine = flowEngine;
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
    this.maxWorkers = maxWorkers;
  }

  /**
   * Pairs a {@link BatchRow} with its parsed {@link FlowRequest} for in-memory processing.
   *
   * @param row the batch row entity
   * @param request the parsed flow request
   */
  public record RowWithRequest(BatchRow row, FlowRequest request) {}

  /**
   * Executes the given batch rows asynchronously and updates the batch run on completion.
   *
   * @param batchRunId the batch run id to update
   * @param rowsWithRequests the rows and their associated flow requests
   * @param maxRatePerSecond optional rate cap in events per second; null = unlimited
   * @param chaos optional chaos options applied to each published event
   */
  public void execute(
      String batchRunId,
      List<RowWithRequest> rowsWithRequests,
      @Nullable Integer maxRatePerSecond,
      @Nullable ChaosOptions chaos) {

    Thread.ofVirtual()
        .name("batch-runner-" + batchRunId)
        .start(() -> runBatch(batchRunId, rowsWithRequests, maxRatePerSecond, chaos));
  }

  private void runBatch(
      String batchRunId,
      List<RowWithRequest> rowsWithRequests,
      @Nullable Integer maxRatePerSecond,
      @Nullable ChaosOptions chaos) {

    var semaphore = new Semaphore(maxWorkers);
    long delayBetweenMs =
        (maxRatePerSecond != null && maxRatePerSecond > 0) ? 1000L / maxRatePerSecond : 0L;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (RowWithRequest rwr : rowsWithRequests) {
        if (rwr.row().getStatus() != BatchRowStatus.PENDING) {
          continue;
        }

        applyRateDelay(delayBetweenMs);

        try {
          semaphore.acquire();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Batch runner interrupted for batch {}", batchRunId);
          break;
        }

        FlowRequest effectiveRequest =
            chaos != null
                ? FlowRequestBuilder.builder()
                    .flowType(rwr.request().flowType())
                    .correlationId(rwr.request().correlationId())
                    .tenantId(rwr.request().tenantId())
                    .channel(rwr.request().channel())
                    .amount(rwr.request().amount())
                    .grossAmount(rwr.request().grossAmount())
                    .netAmount(rwr.request().netAmount())
                    .currency(rwr.request().currency())
                    .slotOverrides(rwr.request().slotOverrides())
                    .chaos(chaos)
                    .flowFields(rwr.request().flowFields())
                    .build()
                : rwr.request();

        executor.submit(
            () -> {
              try {
                processRow(rwr.row(), effectiveRequest);
              } finally {
                semaphore.release();
              }
            });
      }
    }

    finalizeRun(batchRunId);
  }

  @Transactional
  void processRow(BatchRow row, FlowRequest request) {
    try {
      var result = flowEngine.execute(request);
      row.setStatus(BatchRowStatus.PUBLISHED);
      row.setEventId(result.eventId());
      batchRowRepository.save(row);
    } catch (Exception e) {
      log.error("Batch row {} failed: {}", row.getId(), e.getMessage(), e);
      row.setStatus(BatchRowStatus.FAILED);
      row.setError(e.getMessage());
      batchRowRepository.save(row);
    }
  }

  @Transactional
  void finalizeRun(String batchRunId) {
    BatchRun run = batchRunRepository.findById(batchRunId).orElse(null);
    if (run == null) {
      log.error("Cannot finalize batch run {}: not found", batchRunId);
      return;
    }

    long succeeded =
        batchRowRepository.countByBatchIdAndStatus(batchRunId, BatchRowStatus.PUBLISHED);
    long failed = batchRowRepository.countByBatchIdAndStatus(batchRunId, BatchRowStatus.FAILED);
    long invalid = batchRowRepository.countByBatchIdAndStatus(batchRunId, BatchRowStatus.INVALID);

    run.setSucceeded((int) succeeded);
    run.setFailed((int) failed);
    run.setInvalid((int) invalid);
    run.setCompletedAt(Instant.now());

    if (failed > 0 || invalid > 0) {
      run.setStatus(succeeded > 0 ? BatchRunStatus.COMPLETED_WITH_FAILURES : BatchRunStatus.FAILED);
    } else {
      run.setStatus(BatchRunStatus.COMPLETED);
    }

    batchRunRepository.save(run);
    log.info(
        "Batch run {} finalized: succeeded={}, failed={}, invalid={}",
        batchRunId,
        succeeded,
        failed,
        invalid);
  }

  private void applyRateDelay(long delayMs) {
    if (delayMs > 0) {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
