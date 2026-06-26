package com.softspark.chaos.flow;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.model.BatchRow;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.batch.service.BatchRunner;
import com.softspark.chaos.batch.service.PacingPlan;
import com.softspark.chaos.flow.chaos.ExecutionMode;
import com.softspark.chaos.flow.chaos.NTimesExpander;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes a {@code mode = ASYNC} N-Times request as a tracked run and hands it to the
 * {@link BatchRunner} for background execution.
 *
 * <p>An N-Times run is structurally "a batch of N synthetic rows of one flow, each a distinct
 * transaction." This service reuses the Phase 003 {@code batch_run}/{@code batch_row} tables behind
 * a {@link RunKind#N_TIMES} discriminator: it persists the run + N {@code PENDING} rows, then
 * submits the pre-expanded distinct requests to the pacing-aware runner. The request returns in
 * O(1) with a run handle; progress is polled through the existing run endpoints.
 */
@Service
public class NTimesRunService {

  private static final Logger log = LoggerFactory.getLogger(NTimesRunService.class);

  private final NTimesExpander expander;
  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;
  private final BatchRunner batchRunner;

  public NTimesRunService(
      NTimesExpander expander,
      BatchRunRepository batchRunRepository,
      BatchRowRepository batchRowRepository,
      BatchRunner batchRunner) {
    this.expander = expander;
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
    this.batchRunner = batchRunner;
  }

  /**
   * Validates, expands, and starts a tracked N-Times run.
   *
   * @param base the originating request (with a non-null {@code chaos.nTimes()})
   * @return the created run handle (status = RUNNING)
   */
  @Transactional
  public BatchRunResponse startRun(FlowRequest base) {
    NTimesOptions options = base.chaos().nTimes();
    expander.validate(options);
    List<FlowRequest> requests = expander.expand(base);

    var run = new BatchRun();
    run.setId(Ids.generate());
    run.setFlowType(base.flowType().name());
    run.setFilename(null);
    run.setKind(RunKind.N_TIMES);
    run.setPacing(options.pacing().name());
    run.setMode((options.mode() != null ? options.mode() : ExecutionMode.ASYNC).name());
    run.setTotal(requests.size());
    run.setStatus(BatchRunStatus.RUNNING);
    run.setCreatedAt(Instant.now());
    batchRunRepository.save(run);

    List<BatchRunner.RowWithRequest> rows = new ArrayList<>(requests.size());
    for (int i = 0; i < requests.size(); i++) {
      var row = new BatchRow();
      row.setId(Ids.generate());
      row.setBatchId(run.getId());
      row.setRowNumber(i);
      row.setStatus(BatchRowStatus.PENDING);
      row.setCreatedAt(Instant.now());
      batchRowRepository.save(row);
      rows.add(new BatchRunner.RowWithRequest(row, requests.get(i)));
    }

    log.info(
        "Starting N-Times run {} for flow {} ({} iterations, pacing={})",
        run.getId(),
        base.flowType(),
        requests.size(),
        options.pacing());

    batchRunner.executeNTimes(
        run.getId(),
        rows,
        PacingPlan.forOptions(options),
        options.pacing().name(),
        requests.size());

    return BatchRunResponse.from(run);
  }
}
