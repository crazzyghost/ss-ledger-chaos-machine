package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.batch.service.BatchRunner;
import com.softspark.chaos.batch.service.PacingPlan;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.chaos.ChaosLimits;
import com.softspark.chaos.flow.chaos.ChaosOptions;
import com.softspark.chaos.flow.chaos.ExecutionMode;
import com.softspark.chaos.flow.chaos.NTimesExpander;
import com.softspark.chaos.flow.chaos.NTimesOptions;
import com.softspark.chaos.flow.chaos.Pacing;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link NTimesRunService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("NTimesRunService")
class NTimesRunServiceTest {

  private static final ChaosLimits LIMITS = new ChaosLimits(10, 100, 1000, 30000L, 100, 25, 60000L);

  @Mock private BatchRunRepository batchRunRepository;
  @Mock private BatchRowRepository batchRowRepository;
  @Mock private BatchRunner batchRunner;

  private NTimesRunService service;

  @BeforeEach
  void setUp() {
    var expander = new NTimesExpander(new FlowCatalogProvider(new TopicCatalog()), LIMITS);
    service = new NTimesRunService(expander, batchRunRepository, batchRowRepository, batchRunner);
  }

  private FlowRequest base(NTimesOptions options) {
    Map<String, Object> flowFields = new LinkedHashMap<>();
    flowFields.put("topup_request_id", "REQ-1");
    flowFields.put("organization_id", "org-1");
    flowFields.put("currency", "GHS");
    return FlowRequestBuilder.builder()
        .flowType(FlowType.TOPUP_CONFIRMED)
        .slotOverrides(Map.of("source", "va-1", "destination", "va-2"))
        .chaos(new ChaosOptions(null, null, null, null, null, null, options))
        .flowFields(flowFields)
        .build();
  }

  @Test
  @DisplayName("persists a N_TIMES run with total=count, pacing/mode, and N rows, then submits")
  void startsRun() {
    var options = new NTimesOptions(7, Pacing.BURST, ExecutionMode.ASYNC, null, null, null);

    var response = service.startRun(base(options));

    ArgumentCaptor<BatchRun> runCaptor = ArgumentCaptor.forClass(BatchRun.class);
    verify(batchRunRepository).save(runCaptor.capture());
    BatchRun run = runCaptor.getValue();
    assertThat(run.getKind()).isEqualTo(RunKind.N_TIMES);
    assertThat(run.getTotal()).isEqualTo(7);
    assertThat(run.getPacing()).isEqualTo("BURST");
    assertThat(run.getMode()).isEqualTo("ASYNC");
    assertThat(run.getFilename()).isNull();

    verify(batchRowRepository, times(7)).save(any());

    ArgumentCaptor<List<BatchRunner.RowWithRequest>> rowsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(batchRunner)
        .executeNTimes(
            eq(run.getId()), rowsCaptor.capture(), any(PacingPlan.class), eq("BURST"), eq(7));
    assertThat(rowsCaptor.getValue()).hasSize(7);

    assertThat(response.kind()).isEqualTo(RunKind.N_TIMES);
    assertThat(response.total()).isEqualTo(7);
    assertThat(response.pacing()).isEqualTo("BURST");
    assertThat(response.mode()).isEqualTo("ASYNC");
  }

  @Test
  @DisplayName("rows carry distinct request ids and a shared correlation id")
  void rowsAreDistinct() {
    var options = new NTimesOptions(5, Pacing.LINEAR, ExecutionMode.ASYNC, 100L, null, null);

    service.startRun(base(options));

    ArgumentCaptor<List<BatchRunner.RowWithRequest>> rowsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(batchRunner)
        .executeNTimes(anyString(), rowsCaptor.capture(), any(), anyString(), anyInt());

    var rows = rowsCaptor.getValue();
    var ids = rows.stream().map(r -> r.request().flowFields().get("topup_request_id")).toList();
    var correlationIds = rows.stream().map(r -> r.request().correlationId()).distinct().toList();
    assertThat(ids).doesNotHaveDuplicates();
    assertThat(correlationIds).hasSize(1);
  }
}
