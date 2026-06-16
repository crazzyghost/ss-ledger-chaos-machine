package com.softspark.chaos.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.softspark.chaos.flow.chaos.ChaosPlan;
import com.softspark.chaos.flow.chaos.PreparedSend;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.resolver.SlotResolver;
import com.softspark.chaos.history.service.HistoryWriter;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.EventPublishException;
import com.softspark.chaos.kafka.TopicCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link FlowEngine}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlowEngine")
class FlowEngineTest {

  @Mock private FlowBuilderRegistry registry;
  @Mock private SlotResolver slotResolver;
  @Mock private ChaosPlan chaosPlan;
  @Mock private ChaosEventPublisher publisher;
  @Mock private HistoryWriter historyWriter;
  @Mock private TopicCatalog topicCatalog;

  private FlowEngine flowEngine;

  @BeforeEach
  void setUp() {
    flowEngine =
        new FlowEngine(
            registry,
            slotResolver,
            chaosPlan,
            publisher,
            historyWriter,
            topicCatalog,
            "test-tenant");
  }

  private EventEnvelope<Object> sampleEnvelope() {
    var meta = new EventMetadata("corr-1", "idem-1", "test-tenant");
    return new EventEnvelope<>(
        "evt-123",
        "collection.completed",
        Instant.now(),
        "payment-service",
        "1.0",
        Map.of("key", "value"),
        meta);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private FlowBuilder setupMockBuilder(FlowType type) {
    FlowBuilder builder = mock(FlowBuilder.class);
    when(builder.type()).thenReturn(type);
    when(builder.source()).thenReturn("payment-service");
    when(builder.build(any(), any())).thenReturn(sampleEnvelope());
    when(builder.partitionKey(any())).thenReturn("partition-key");
    return builder;
  }

  @Nested
  @DisplayName("execute — happy path")
  class HappyPath {

    @Test
    @DisplayName("returns PUBLISHED result when publisher succeeds")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsPublishedResult() {
      FlowBuilder builder = setupMockBuilder(FlowType.COLLECTION_COMPLETED);
      doReturn(builder).when(registry).get(FlowType.COLLECTION_COMPLETED);
      when(slotResolver.resolveAll(any(), any())).thenReturn(Map.of());
      when(topicCatalog.topicFor(FlowType.COLLECTION_COMPLETED)).thenReturn("collection.completed");

      var send = new PreparedSend(sampleEnvelope(), null, Duration.ZERO, null);
      when(chaosPlan.expand(any(), any(), any())).thenReturn(List.of(send));
      lenient()
          .when(publisher.publish(anyString(), anyString(), any()))
          .thenReturn(new ChaosEventPublisher.PublishResult(42L, 0));
      lenient()
          .when(
              historyWriter.record(
                  any(), anyString(), anyString(), any(), any(), any(), anyBoolean()))
          .thenReturn("history-001");

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.COLLECTION_COMPLETED)
              .slotOverrides(Map.of())
              .flowFields(
                  Map.of(
                      "collection_request_id",
                      "REQ-1",
                      "merchant_reference",
                      "MR-1",
                      "provider_collection_id",
                      "P-1"))
              .build();

      var result = flowEngine.execute(request);

      assertThat(result.status()).isEqualTo("PUBLISHED");
      assertThat(result.offset()).isEqualTo(42L);
      assertThat(result.partition()).isZero();
      assertThat(result.historyId()).isEqualTo("history-001");
      assertThat(result.error()).isNull();
    }
  }

  @Nested
  @DisplayName("execute — failure path")
  class FailurePath {

    @Test
    @DisplayName("returns FAILED result when publisher throws EventPublishException")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsFailedResult() {
      FlowBuilder builder = setupMockBuilder(FlowType.COLLECTION_COMPLETED);
      doReturn(builder).when(registry).get(FlowType.COLLECTION_COMPLETED);
      when(slotResolver.resolveAll(any(), any())).thenReturn(Map.of());
      when(topicCatalog.topicFor(FlowType.COLLECTION_COMPLETED)).thenReturn("collection.completed");

      var send = new PreparedSend(sampleEnvelope(), null, Duration.ZERO, null);
      when(chaosPlan.expand(any(), any(), any())).thenReturn(List.of(send));
      lenient()
          .when(publisher.publish(anyString(), anyString(), any()))
          .thenThrow(new EventPublishException("Kafka down", new RuntimeException()));
      lenient()
          .when(historyWriter.recordFailure(any(), anyString(), anyString(), any()))
          .thenReturn("history-fail-001");

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.COLLECTION_COMPLETED)
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();

      var result = flowEngine.execute(request);

      assertThat(result.status()).isEqualTo("FAILED");
      assertThat(result.offset()).isEqualTo(-1L);
      assertThat(result.partition()).isEqualTo(-1);
      assertThat(result.historyId()).isEqualTo("history-fail-001");
      assertThat(result.error()).contains("Kafka down");
    }
  }
}
