package com.softspark.chaos.run.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * One row of the run-grouped feed ({@code GET /api/v0/runs}) — a summary sufficient to render a Run
 * History accordion header without fetching the run's children (ADR-031).
 *
 * <p>A run is either a <em>tracked</em> run (a {@code batch_run} row: N-Times-async,
 * lifecycle-random, batch-disbursement, or a historical CSV run; {@code tracked = true},
 * {@code batchId} set, drilled down via {@code GET /api/v0/history?batchId=}) or an
 * <em>untracked</em> run (a {@code correlation_id} group of {@code publish_record}s with
 * {@code batch_id IS NULL}: single publish, N-Times-SYNC, interactive wizard; {@code tracked = false},
 * {@code correlationId} set, drilled down via {@code GET /api/v0/history?correlationId=}).
 *
 * @param runKey the {@code batch_run} id (tracked) or the {@code correlation_id} (untracked)
 * @param tracked whether this run is backed by a {@code batch_run} row
 * @param kind the {@link com.softspark.chaos.batch.enumeration.RunKind} name (tracked) or a derived
 *     label {@code "SINGLE"}/{@code "MANUAL_SEQUENCE"} (untracked)
 * @param flowTypes the distinct event/flow types observed in the run
 * @param eventCount the run's event count ({@code batch_run.total} tracked; group size untracked)
 * @param status the coarse publish-status rollup
 * @param publishedCount how many events published successfully
 * @param failedCount how many events failed to publish (incl. invalid rows for tracked runs)
 * @param intentionalFailure whether any event in the run was an intentional/chaos failure
 * @param firstActivityAt the earliest activity timestamp in the run
 * @param lastActivityAt the latest activity timestamp in the run (the feed orders by this, DESC)
 * @param externalBatchId the ledger batch id for a tracked batch-disbursement run; else {@code null}
 * @param correlationId the untracked drill-down key; {@code null} for tracked runs
 * @param batchId the tracked drill-down key; {@code null} for untracked runs
 */
@RecordBuilder
public record RunSummaryResponse(
    String runKey,
    boolean tracked,
    String kind,
    List<String> flowTypes,
    int eventCount,
    RunStatusRollup status,
    int publishedCount,
    int failedCount,
    boolean intentionalFailure,
    Instant firstActivityAt,
    Instant lastActivityAt,
    @Nullable String externalBatchId,
    @Nullable String correlationId,
    @Nullable String batchId) {

  /** Null-guards the flow-type list to an empty list. */
  public RunSummaryResponse {
    if (flowTypes == null) {
      flowTypes = List.of();
    }
  }
}
