package com.softspark.chaos.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.history.repository.PublishRecordRepository;
import com.softspark.chaos.run.dto.RunGroupRow;
import com.softspark.chaos.run.dto.RunStatusRollup;
import com.softspark.chaos.run.dto.RunSummaryResponse;
import com.softspark.chaos.run.dto.RunsQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RunSummaryService} — the tracked ∪ untracked merge, rollups, ordering,
 * grouping, no-double-counting, pagination, and filters.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RunSummaryService")
class RunSummaryServiceTest {

  private static final Instant T0 = Instant.parse("2026-06-29T10:00:00Z");

  @Mock private BatchRunRepository batchRunRepository;
  @Mock private PublishRecordRepository publishRecordRepository;

  private RunSummaryService service;

  @BeforeEach
  void setUp() {
    service = new RunSummaryService(batchRunRepository, publishRecordRepository, 100_000);
  }

  private BatchRun batchRun(
      String id,
      String flowType,
      RunKind kind,
      int total,
      int succeeded,
      int failed,
      int invalid,
      BatchRunStatus status,
      Instant createdAt,
      Instant completedAt,
      String externalBatchId) {
    var run = new BatchRun();
    run.setId(id);
    run.setFlowType(flowType);
    run.setKind(kind);
    run.setTotal(total);
    run.setSucceeded(succeeded);
    run.setFailed(failed);
    run.setInvalid(invalid);
    run.setStatus(status);
    run.setCreatedAt(createdAt);
    run.setCompletedAt(completedAt);
    run.setExternalBatchId(externalBatchId);
    return run;
  }

  private RunGroupRow row(
      String id, String correlationId, String eventType, String status, Instant createdAt) {
    return new RunGroupRow(id, correlationId, eventType, status, false, createdAt);
  }

  private RunsQuery allRuns() {
    return new RunsQuery(null, null, null, 0, 20);
  }

  @Test
  @DisplayName("should map a batch_run to one tracked run with counters/status from the run row")
  void should_mapBatchRunToTrackedRun_when_batchRunExists() {
    var run =
        batchRun(
            "run-1",
            "DISBURSEMENT_BATCH_RESERVATION_REQUEST",
            RunKind.BATCH_DISBURSEMENT,
            10,
            8,
            2,
            0,
            BatchRunStatus.COMPLETED_WITH_FAILURES,
            T0,
            T0.plusSeconds(30),
            "ext-batch-1");
    when(batchRunRepository.findAll()).thenReturn(List.of(run));
    when(publishRecordRepository.findUntrackedRunRows(any())).thenReturn(List.of());

    var page = service.listRuns(allRuns());

    assertThat(page.total()).isEqualTo(1);
    var summary = page.items().get(0);
    assertThat(summary.runKey()).isEqualTo("run-1");
    assertThat(summary.tracked()).isTrue();
    assertThat(summary.kind()).isEqualTo("BATCH_DISBURSEMENT");
    assertThat(summary.eventCount()).isEqualTo(10);
    assertThat(summary.publishedCount()).isEqualTo(8);
    assertThat(summary.failedCount()).isEqualTo(2);
    assertThat(summary.status()).isEqualTo(RunStatusRollup.HAS_FAILURES);
    assertThat(summary.externalBatchId()).isEqualTo("ext-batch-1");
    assertThat(summary.batchId()).isEqualTo("run-1");
    assertThat(summary.correlationId()).isNull();
    assertThat(summary.lastActivityAt()).isEqualTo(T0.plusSeconds(30));
    assertThat(summary.flowTypes()).containsExactly("DISBURSEMENT_BATCH_RESERVATION_REQUEST");
  }

  @Test
  @DisplayName("should map RUNNING/COMPLETED/FAILED batch statuses to the right rollup")
  void should_mapStatusRollup_when_trackedRunHasStatus() {
    when(publishRecordRepository.findUntrackedRunRows(any())).thenReturn(List.of());
    when(batchRunRepository.findAll())
        .thenReturn(
            List.of(
                batchRun("a", "F", RunKind.N_TIMES, 5, 0, 0, 0, BatchRunStatus.RUNNING, T0, null, null),
                batchRun(
                    "b", "F", RunKind.N_TIMES, 5, 5, 0, 0, BatchRunStatus.COMPLETED, T0, T0, null),
                batchRun(
                    "c", "F", RunKind.N_TIMES, 5, 0, 5, 0, BatchRunStatus.FAILED, T0, T0, null)));

    var byKey =
        service.listRuns(allRuns()).items().stream()
            .collect(java.util.stream.Collectors.toMap(RunSummaryResponse::runKey, r -> r.status()));

    assertThat(byKey.get("a")).isEqualTo(RunStatusRollup.RUNNING);
    assertThat(byKey.get("b")).isEqualTo(RunStatusRollup.ALL_PUBLISHED);
    assertThat(byKey.get("c")).isEqualTo(RunStatusRollup.FAILED);
  }

  @Test
  @DisplayName("should group untracked publish records by correlation_id into one run each")
  void should_groupUntrackedByCorrelationId_when_batchIdNull() {
    when(batchRunRepository.findAll()).thenReturn(List.of());
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(
            List.of(
                row("e1", "corr-A", "collection.initiated", "PUBLISHED", T0.plusSeconds(5)),
                row("e2", "corr-A", "collection.completed", "FAILED", T0.plusSeconds(9)),
                row("e3", "corr-B", "disbursement.initiated", "PUBLISHED", T0.plusSeconds(2))));

    var page = service.listRuns(allRuns());

    assertThat(page.total()).isEqualTo(2);
    var corrA =
        page.items().stream().filter(r -> "corr-A".equals(r.runKey())).findFirst().orElseThrow();
    assertThat(corrA.tracked()).isFalse();
    assertThat(corrA.kind()).isEqualTo("MANUAL_SEQUENCE");
    assertThat(corrA.eventCount()).isEqualTo(2);
    assertThat(corrA.publishedCount()).isEqualTo(1);
    assertThat(corrA.failedCount()).isEqualTo(1);
    assertThat(corrA.status()).isEqualTo(RunStatusRollup.HAS_FAILURES);
    assertThat(corrA.flowTypes())
        .containsExactlyInAnyOrder("collection.initiated", "collection.completed");
    assertThat(corrA.firstActivityAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(corrA.lastActivityAt()).isEqualTo(T0.plusSeconds(9));
    assertThat(corrA.correlationId()).isEqualTo("corr-A");
    assertThat(corrA.batchId()).isNull();
  }

  @Test
  @DisplayName("should return a singleton SINGLE run for a one-event correlation group")
  void should_returnSingletonRun_when_correlationGroupHasOneRow() {
    when(batchRunRepository.findAll()).thenReturn(List.of());
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(List.of(row("e1", "corr-solo", "collection.completed", "PUBLISHED", T0)));

    var page = service.listRuns(allRuns());

    assertThat(page.total()).isEqualTo(1);
    var summary = page.items().get(0);
    assertThat(summary.kind()).isEqualTo("SINGLE");
    assertThat(summary.eventCount()).isEqualTo(1);
    assertThat(summary.runKey()).isEqualTo("corr-solo");
  }

  @Test
  @DisplayName("should emit a null-correlation row as a singleton keyed by its own id")
  void should_emitSingleton_when_correlationIdNull() {
    when(batchRunRepository.findAll()).thenReturn(List.of());
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(List.of(row("orphan-1", null, "some.event", "PUBLISHED", T0)));

    var summary = service.listRuns(allRuns()).items().get(0);

    assertThat(summary.runKey()).isEqualTo("orphan-1");
    assertThat(summary.kind()).isEqualTo("SINGLE");
    assertThat(summary.correlationId()).isNull();
  }

  @Test
  @DisplayName("should not double-count: tracked runs + untracked groups sum to the total")
  void should_notDoubleCount_when_mixedTrackedAndUntracked() {
    when(batchRunRepository.findAll())
        .thenReturn(
            List.of(
                batchRun(
                    "run-1", "F", RunKind.N_TIMES, 3, 3, 0, 0, BatchRunStatus.COMPLETED, T0, T0,
                    null)));
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(
            List.of(
                row("e1", "corr-A", "x", "PUBLISHED", T0.plusSeconds(1)),
                row("e2", "corr-A", "x", "PUBLISHED", T0.plusSeconds(2)),
                row("e3", "corr-B", "y", "PUBLISHED", T0.plusSeconds(3))));

    var page = service.listRuns(allRuns());

    assertThat(page.total()).isEqualTo(3); // 1 tracked + 2 untracked groups
    assertThat(page.items().stream().filter(RunSummaryResponse::tracked).count()).isEqualTo(1);
  }

  @Test
  @DisplayName("should order runs by last activity descending")
  void should_orderByLastActivityDesc_when_multipleRuns() {
    when(batchRunRepository.findAll())
        .thenReturn(
            List.of(
                batchRun(
                    "old", "F", RunKind.N_TIMES, 1, 1, 0, 0, BatchRunStatus.COMPLETED, T0,
                    T0.plusSeconds(10), null)));
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(
            List.of(
                row("e1", "corr-new", "x", "PUBLISHED", T0.plusSeconds(100)),
                row("e2", "corr-mid", "y", "PUBLISHED", T0.plusSeconds(50))));

    var keys = service.listRuns(allRuns()).items().stream().map(RunSummaryResponse::runKey).toList();

    assertThat(keys).containsExactly("corr-new", "corr-mid", "old");
  }

  @Test
  @DisplayName("should paginate the merged feed and report the authoritative total")
  void should_paginate_when_moreRunsThanPageSize() {
    when(batchRunRepository.findAll()).thenReturn(List.of());
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(
            List.of(
                row("e1", "c1", "x", "PUBLISHED", T0.plusSeconds(1)),
                row("e2", "c2", "x", "PUBLISHED", T0.plusSeconds(2)),
                row("e3", "c3", "x", "PUBLISHED", T0.plusSeconds(3))));

    var firstPage = service.listRuns(new RunsQuery(null, null, null, 0, 2));
    assertThat(firstPage.total()).isEqualTo(3);
    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.items().get(0).runKey()).isEqualTo("c3"); // newest first

    var secondPage = service.listRuns(new RunsQuery(null, null, null, 1, 2));
    assertThat(secondPage.total()).isEqualTo(3);
    assertThat(secondPage.items()).hasSize(1);
    assertThat(secondPage.items().get(0).runKey()).isEqualTo("c1");
  }

  @Test
  @DisplayName("should filter by kind (case-insensitive)")
  void should_filterByKind_when_kindProvided() {
    when(batchRunRepository.findAll())
        .thenReturn(
            List.of(
                batchRun(
                    "run-1", "F", RunKind.N_TIMES, 1, 1, 0, 0, BatchRunStatus.COMPLETED, T0, T0,
                    null)));
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(List.of(row("e1", "c1", "x", "PUBLISHED", T0.plusSeconds(1))));

    var page = service.listRuns(new RunsQuery(null, null, "n_times", 0, 20));

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).runKey()).isEqualTo("run-1");
  }

  @Test
  @DisplayName("should filter by from/to on last activity")
  void should_filterByTimeRange_when_fromToProvided() {
    when(batchRunRepository.findAll()).thenReturn(List.of());
    when(publishRecordRepository.findUntrackedRunRows(any()))
        .thenReturn(
            List.of(
                row("e1", "early", "x", "PUBLISHED", T0.plusSeconds(10)),
                row("e2", "late", "x", "PUBLISHED", T0.plusSeconds(1000))));

    var page =
        service.listRuns(
            new RunsQuery(T0.plusSeconds(500), T0.plusSeconds(2000), null, 0, 20));

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).runKey()).isEqualTo("late");
  }
}
