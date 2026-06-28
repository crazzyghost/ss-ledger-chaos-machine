package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.BadRequestException;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link NTimesSyncRunner}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("NTimesSyncRunner")
class NTimesSyncRunnerTest {

  private static final ChaosLimits LIMITS = new ChaosLimits(10, 100, 1000, 30000L, 100, 25, 60000L, 100);

  @Mock private FlowEngine flowEngine;
  private NTimesSyncRunner runner;

  @BeforeEach
  void setUp() {
    var expander = new NTimesExpander(new FlowCatalogProvider(new TopicCatalog()), LIMITS);
    runner = new NTimesSyncRunner(expander, flowEngine, LIMITS);
  }

  private FlowRequest base(NTimesOptions options) {
    Map<String, Object> flowFields = new LinkedHashMap<>();
    flowFields.put("topup_request_id", "REQ-1");
    flowFields.put("organization_id", "org-1");
    flowFields.put("currency", "GHS");
    return FlowRequestBuilder.builder()
        .flowType(FlowType.TOPUP_CONFIRMED)
        .correlationId("corr-1")
        .slotOverrides(Map.of("source", "va-1", "destination", "va-2"))
        .chaos(new ChaosOptions(null, null, null, null, null, null, options))
        .flowFields(flowFields)
        .build();
  }

  private void stubPublished() {
    var counter = new AtomicInteger();
    when(flowEngine.execute(any(), nullable(String.class)))
        .thenAnswer(
            inv -> {
              int n = counter.incrementAndGet();
              return new FlowResult(
                  "evt-" + n, "topic", 0, n, "PUBLISHED", "hist-" + n, null, "req-" + n);
            });
  }

  @Test
  @DisplayName("executes count times with NTIMES:<pacing>:i/N labels")
  void executesWithLabels() {
    stubPublished();
    var options = new NTimesOptions(3, Pacing.BURST, ExecutionMode.SYNC, null, null, null);

    var result = runner.run(base(options));

    ArgumentCaptor<String> labels = ArgumentCaptor.forClass(String.class);
    verify(flowEngine, org.mockito.Mockito.times(3)).execute(any(), labels.capture());
    assertThat(labels.getAllValues())
        .containsExactly("NTIMES:BURST:1/3", "NTIMES:BURST:2/3", "NTIMES:BURST:3/3");

    assertThat(result.count()).isEqualTo(3);
    assertThat(result.succeeded()).isEqualTo(3);
    assertThat(result.failed()).isZero();
    assertThat(result.correlationId()).isEqualTo("corr-1");
    assertThat(result.eventIds()).hasSize(3);
    assertThat(result.historyIds()).hasSize(3);
    assertThat(result.transactionRequestIds()).containsExactly("req-1", "req-2", "req-3");
  }

  @Test
  @DisplayName("counts a failed iteration without aborting the run")
  void countsFailuresBestEffort() {
    var counter = new AtomicInteger();
    when(flowEngine.execute(any(), nullable(String.class)))
        .thenAnswer(
            inv -> {
              int n = counter.incrementAndGet();
              String status = n == 2 ? "FAILED" : "PUBLISHED";
              return new FlowResult(
                  "evt-" + n, "topic", 0, n, status, "hist-" + n, null, "req-" + n);
            });
    var options = new NTimesOptions(3, Pacing.BURST, ExecutionMode.SYNC, null, null, null);

    var result = runner.run(base(options));

    assertThat(result.succeeded()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.succeeded() + result.failed()).isEqualTo(result.count());
  }

  @Test
  @DisplayName("rejects SYNC count over maxNTimesSync, pointing at ASYNC")
  void rejectsOverSyncCount() {
    var options = new NTimesOptions(26, Pacing.BURST, ExecutionMode.SYNC, null, null, null);
    assertThatThrownBy(() -> runner.run(base(options)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("ASYNC");
  }

  @Test
  @DisplayName("rejects a SYNC run whose projected duration exceeds maxSyncDurationMs")
  void rejectsLongProjectedDuration() {
    // 25 iterations × 30000ms gap = 750000ms > 60000ms limit
    var options = new NTimesOptions(25, Pacing.LINEAR, ExecutionMode.SYNC, 30000L, null, null);
    assertThatThrownBy(() -> runner.run(base(options)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("ASYNC");
  }

  @Test
  @DisplayName("produces distinct request ids across iterations")
  void distinctRequestIds() {
    stubPublished();
    var options = new NTimesOptions(4, Pacing.BURST, ExecutionMode.SYNC, null, null, null);

    runner.run(base(options));

    ArgumentCaptor<FlowRequest> requests = ArgumentCaptor.forClass(FlowRequest.class);
    verify(flowEngine, org.mockito.Mockito.times(4))
        .execute(requests.capture(), nullable(String.class));
    List<Object> ids =
        requests.getAllValues().stream().map(r -> r.flowFields().get("topup_request_id")).toList();
    assertThat(ids).doesNotHaveDuplicates();
  }
}
