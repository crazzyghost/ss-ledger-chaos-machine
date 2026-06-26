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
import com.softspark.chaos.flow.dto.FlowLifecycle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes a RANDOM lifecycle request as a tracked run and hands it to the {@link BatchRunner}
 * for background execution.
 *
 * <p>A RANDOM lifecycle run is "fire N distinct {@link FlowLifecycle}s, each deciding SUCCEED/FAIL at
 * random". This service reuses the Phase 003/013 {@code batch_run}/{@code batch_row} tables behind a
 * {@link RunKind#LIFECYCLE} discriminator: it persists the run + N {@code PENDING} rows, derives a
 * per-run outcome seed, and submits the units to the pacing-aware runner. The request returns in O(1)
 * with a run handle; progress is polled through the existing run endpoints. {@code count} and pacing
 * reuse the N-Times caps via {@link NTimesExpander#validate(NTimesOptions)}.
 */
@Service
public class LifecycleRunService {

  private static final Logger log = LoggerFactory.getLogger(LifecycleRunService.class);

  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;
  private final BatchRunner batchRunner;
  private final LifecycleRunner lifecycleRunner;
  private final OutcomeDecider outcomeDecider;
  private final NTimesExpander expander;

  public LifecycleRunService(
      BatchRunRepository batchRunRepository,
      BatchRowRepository batchRowRepository,
      BatchRunner batchRunner,
      LifecycleRunner lifecycleRunner,
      OutcomeDecider outcomeDecider,
      NTimesExpander expander) {
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
    this.batchRunner = batchRunner;
    this.lifecycleRunner = lifecycleRunner;
    this.outcomeDecider = outcomeDecider;
    this.expander = expander;
  }

  /**
   * Validates and starts a tracked RANDOM lifecycle run of {@code count} distinct lifecycles.
   *
   * @param baseInitiated the operator-supplied initiated intent (shared across the run)
   * @param lifecycle the lifecycle grouping for the requested transaction type
   * @param options the count/pacing options (validated against the N-Times caps; mode forced ASYNC)
   * @return the created run handle (status = RUNNING)
   */
  @Transactional
  public BatchRunResponse startRun(
      FlowRequest baseInitiated, FlowLifecycle lifecycle, NTimesOptions options) {
    expander.validate(options);
    int count = options.count();

    var run = new BatchRun();
    run.setId(Ids.generate());
    run.setFlowType(lifecycle.initiated().name());
    run.setFilename(null);
    run.setKind(RunKind.LIFECYCLE);
    run.setPacing(options.pacing().name());
    run.setMode(ExecutionMode.ASYNC.name());
    run.setTotal(count);
    run.setStatus(BatchRunStatus.RUNNING);
    run.setCreatedAt(Instant.now());
    batchRunRepository.save(run);

    long seed = outcomeDecider.seedFor(run.getId());
    List<BatchRunner.LifecycleUnit> units = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      var row = new BatchRow();
      row.setId(Ids.generate());
      row.setBatchId(run.getId());
      row.setRowNumber(i);
      row.setStatus(BatchRowStatus.PENDING);
      row.setCreatedAt(Instant.now());
      batchRowRepository.save(row);
      units.add(new BatchRunner.LifecycleUnit(row, baseInitiated, lifecycle, seed, i));
    }

    log.info(
        "Starting RANDOM lifecycle run {} for {} ({} lifecycles, pacing={})",
        run.getId(),
        lifecycle.label(),
        count,
        options.pacing());

    batchRunner.executeLifecycle(
        run.getId(), units, PacingPlan.forOptions(options), count, lifecycleRunner);

    return BatchRunResponse.from(run);
  }
}
