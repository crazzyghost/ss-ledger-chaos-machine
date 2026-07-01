package com.softspark.chaos.run.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.history.repository.PublishRecordRepository;
import com.softspark.chaos.run.dto.RunGroupRow;
import com.softspark.chaos.run.dto.RunStatusRollup;
import com.softspark.chaos.run.dto.RunSummaryResponse;
import com.softspark.chaos.run.dto.RunSummaryResponseBuilder;
import com.softspark.chaos.run.dto.RunsQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the run-grouped feed ({@code GET /api/v0/runs}) by merging two sources into one
 * time-ordered, paginated list of runs (ADR-031):
 *
 * <ol>
 *   <li><strong>tracked runs</strong> — every {@code batch_run} row, mapped one-to-one (authoritative
 *       counters/status from the run row);
 *   <li><strong>untracked runs</strong> — {@code publish_record} rows with {@code batch_id IS NULL},
 *       grouped in memory by {@code correlation_id} (a {@code null}-correlation row forms its own
 *       singleton run keyed by its id).
 * </ol>
 *
 * <p>Because tracked-run events carry a {@code batch_id} (stamped via {@code recordBatch}) they are
 * excluded from the untracked projection, so a run is never double-counted. The single authoritative
 * total is therefore {@code (tracked count) + (distinct untracked group count)}.
 *
 * <p>The merge is performed in memory (ADR-031 approach 2): the untracked side is fetched up to a
 * bounded scan window ({@code chaos.runs.scan-limit}) and the service logs a warning if the cap is
 * hit. This is well within a single-operator harness's data volumes; switch to a DB-windowed fetch
 * if the untracked history grows unbounded.
 */
@Service
public class RunSummaryService {

  private static final Logger log = LoggerFactory.getLogger(RunSummaryService.class);
  private static final String STATUS_FAILED = "FAILED";

  private final BatchRunRepository batchRunRepository;
  private final PublishRecordRepository publishRecordRepository;
  private final int scanLimit;

  /**
   * Constructs the service.
   *
   * @param batchRunRepository the tracked-run source
   * @param publishRecordRepository the untracked-event source
   * @param scanLimit the maximum number of untracked rows scanned per request (bounded merge window)
   */
  public RunSummaryService(
      BatchRunRepository batchRunRepository,
      PublishRecordRepository publishRecordRepository,
      @Value("${chaos.runs.scan-limit:100000}") int scanLimit) {
    this.batchRunRepository = batchRunRepository;
    this.publishRecordRepository = publishRecordRepository;
    this.scanLimit = scanLimit;
  }

  /**
   * Lists runs newest-first (by last activity), applying the optional filters and chaos pagination.
   *
   * @param query the filters + paging (already page-floored / size-clamped)
   * @return one page of run summaries with the authoritative total across both sources
   */
  @Transactional(readOnly = true)
  public PageResponse<RunSummaryResponse> listRuns(RunsQuery query) {
    List<RunSummaryResponse> runs = new ArrayList<>();

    // 1. Tracked runs — one row per batch_run.
    for (BatchRun run : batchRunRepository.findAll()) {
      runs.add(fromBatchRun(run));
    }

    // 2. Untracked runs — group batch_id-null publish records by correlation_id (bounded scan).
    List<RunGroupRow> untrackedRows =
        publishRecordRepository.findUntrackedRunRows(PageRequest.of(0, scanLimit));
    if (untrackedRows.size() >= scanLimit) {
      log.warn(
          "Run feed untracked scan hit the {} row cap; older untracked events may be omitted from "
              + "grouping. Raise chaos.runs.scan-limit or switch to a DB-windowed fetch.",
          scanLimit);
    }
    Map<String, List<RunGroupRow>> groups = new LinkedHashMap<>();
    for (RunGroupRow row : untrackedRows) {
      String key = row.correlationId() != null ? row.correlationId() : row.id();
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }
    for (Map.Entry<String, List<RunGroupRow>> group : groups.entrySet()) {
      runs.add(fromUntrackedGroup(group.getKey(), group.getValue()));
    }

    // 3. Filter, order by last activity DESC (id tie-break for determinism), then page-slice.
    List<RunSummaryResponse> filtered =
        runs.stream()
            .filter(run -> matches(run, query))
            .sorted(
                Comparator.comparing(RunSummaryResponse::lastActivityAt)
                    .reversed()
                    .thenComparing(RunSummaryResponse::runKey))
            .toList();

    int total = filtered.size();
    int fromIndex = query.page() * query.size();
    List<RunSummaryResponse> items =
        fromIndex >= total
            ? List.of()
            : new ArrayList<>(filtered.subList(fromIndex, Math.min(fromIndex + query.size(), total)));
    return new PageResponse<>(items, query.page(), query.size(), total);
  }

  private boolean matches(RunSummaryResponse run, RunsQuery query) {
    if (query.from() != null && run.lastActivityAt().isBefore(query.from())) {
      return false;
    }
    if (query.to() != null && run.lastActivityAt().isAfter(query.to())) {
      return false;
    }
    return query.kind() == null || query.kind().isBlank() || query.kind().equalsIgnoreCase(run.kind());
  }

  private RunSummaryResponse fromBatchRun(BatchRun run) {
    RunKind kind = run.getKind() != null ? run.getKind() : RunKind.CSV;
    Instant last = run.getCompletedAt() != null ? run.getCompletedAt() : run.getCreatedAt();
    return RunSummaryResponseBuilder.builder()
        .runKey(run.getId())
        .tracked(true)
        .kind(kind.name())
        .flowTypes(run.getFlowType() != null ? List.of(run.getFlowType()) : List.of())
        .eventCount(run.getTotal())
        .status(rollupOf(run))
        .publishedCount(run.getSucceeded())
        .failedCount(run.getFailed() + run.getInvalid())
        .intentionalFailure(false)
        .firstActivityAt(run.getCreatedAt())
        .lastActivityAt(last)
        .externalBatchId(run.getExternalBatchId())
        .correlationId(null)
        .batchId(run.getId())
        .build();
  }

  private static RunStatusRollup rollupOf(BatchRun run) {
    return switch (run.getStatus()) {
      case RUNNING -> RunStatusRollup.RUNNING;
      case COMPLETED -> RunStatusRollup.ALL_PUBLISHED;
      case COMPLETED_WITH_FAILURES -> RunStatusRollup.HAS_FAILURES;
      case FAILED -> RunStatusRollup.FAILED;
    };
  }

  private RunSummaryResponse fromUntrackedGroup(String key, List<RunGroupRow> rows) {
    int count = rows.size();
    List<String> flowTypes =
        rows.stream().map(RunGroupRow::eventType).filter(Objects::nonNull).distinct().toList();
    long failed = rows.stream().filter(row -> STATUS_FAILED.equals(row.status())).count();
    boolean anyIntentional = rows.stream().anyMatch(RunGroupRow::intentionalFailure);
    Instant first =
        rows.stream().map(RunGroupRow::createdAt).min(Comparator.naturalOrder()).orElse(null);
    Instant last =
        rows.stream().map(RunGroupRow::createdAt).max(Comparator.naturalOrder()).orElse(null);
    boolean hasCorrelation = rows.get(0).correlationId() != null;
    return RunSummaryResponseBuilder.builder()
        .runKey(key)
        .tracked(false)
        .kind(count == 1 ? "SINGLE" : "MANUAL_SEQUENCE")
        .flowTypes(flowTypes)
        .eventCount(count)
        .status(failed > 0 ? RunStatusRollup.HAS_FAILURES : RunStatusRollup.ALL_PUBLISHED)
        .publishedCount(count - (int) failed)
        .failedCount((int) failed)
        .intentionalFailure(anyIntentional)
        .firstActivityAt(first)
        .lastActivityAt(last)
        .externalBatchId(null)
        .correlationId(hasCorrelation ? key : null)
        .batchId(null)
        .build();
  }
}
