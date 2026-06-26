package com.softspark.chaos.flow;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.chaos.ChaosLimits;
import com.softspark.chaos.flow.chaos.NTimesExpander;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import com.softspark.chaos.flow.chaos.Pacing;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a {@code mode = SYNC} N-Times request in-line on the caller's (virtual) request thread:
 * publishes the flow {@code count} times sequentially, applying the pacing delay before each
 * iteration after the first, and returns an aggregate {@link NTimesSyncResult}.
 *
 * <p>Each iteration goes through the normal single-publish path via
 * {@link FlowEngine#execute(FlowRequest, String)} (the expander has stripped {@code chaos}), with
 * a {@code "NTIMES:<pacing>:i/N"} label stamped on its publish-history record. A publish failure is
 * recorded and counted but does not abort the remaining iterations (best-effort, mirroring batch
 * row semantics).
 */
@Component
public class NTimesSyncRunner {

  private static final Logger log = LoggerFactory.getLogger(NTimesSyncRunner.class);

  private final NTimesExpander expander;
  private final FlowEngine flowEngine;
  private final ChaosLimits limits;

  public NTimesSyncRunner(NTimesExpander expander, FlowEngine flowEngine, ChaosLimits limits) {
    this.expander = expander;
    this.flowEngine = flowEngine;
    this.limits = limits;
  }

  /**
   * Executes the SYNC N-Times run described by {@code base.chaos().nTimes()}.
   *
   * @param base the originating request (with a non-null {@code chaos.nTimes()})
   * @return the aggregate run result
   * @throws BadRequestException if the request is malformed or exceeds the SYNC caps
   */
  public NTimesSyncResult run(FlowRequest base) {
    NTimesOptions options = base.chaos().nTimes();
    expander.validate(options);
    guardSync(options);

    List<FlowRequest> requests = expander.expand(base);
    String correlationId = requests.isEmpty() ? null : requests.get(0).correlationId();

    int count = options.count();
    List<String> eventIds = new ArrayList<>(count);
    List<String> historyIds = new ArrayList<>(count);
    int succeeded = 0;
    int failed = 0;

    for (int i = 0; i < requests.size(); i++) {
      if (!applyDelay(expander.delayFor(options, i))) {
        break;
      }
      String label = "NTIMES:" + options.pacing() + ":" + (i + 1) + "/" + count;
      FlowResult result = flowEngine.execute(requests.get(i), label);
      eventIds.add(result.eventId());
      historyIds.add(result.historyId());
      if ("PUBLISHED".equals(result.status())) {
        succeeded++;
      } else {
        failed++;
      }
    }

    return new NTimesSyncResult(
        base.flowType(), count, succeeded, failed, correlationId, eventIds, historyIds);
  }

  /**
   * Rejects SYNC runs that are too large or would run too long, steering the caller to ASYNC.
   */
  private void guardSync(NTimesOptions options) {
    if (options.count() > limits.maxNTimesSync()) {
      throw new BadRequestException(
          "Synchronous N-Times count "
              + options.count()
              + " exceeds limit of "
              + limits.maxNTimesSync()
              + "; use mode=ASYNC for larger runs");
    }
    long effectiveMaxGap = effectiveMaxGap(options);
    long projected = (long) options.count() * effectiveMaxGap;
    if (projected > limits.maxSyncDurationMs()) {
      throw new BadRequestException(
          "Projected synchronous run time "
              + projected
              + "ms exceeds limit of "
              + limits.maxSyncDurationMs()
              + "ms; use mode=ASYNC for long-paced runs");
    }
  }

  private long effectiveMaxGap(NTimesOptions options) {
    Pacing pacing = options.pacing();
    return switch (pacing) {
      case BURST -> 0L;
      case LINEAR -> options.fixedDelayMs() != null ? options.fixedDelayMs() : 0L;
      case RANDOM -> options.maxDelayMs() != null ? options.maxDelayMs() : 0L;
    };
  }

  /**
   * Sleeps for the given inter-event gap. Returns {@code false} if interrupted (the run should stop
   * cleanly), restoring the interrupt flag.
   */
  private boolean applyDelay(Duration delay) {
    if (delay.isZero() || delay.isNegative()) {
      return true;
    }
    try {
      Thread.sleep(delay);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Synchronous N-Times run interrupted during pacing delay");
      return false;
    }
  }
}
