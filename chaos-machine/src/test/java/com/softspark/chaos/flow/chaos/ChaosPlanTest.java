package com.softspark.chaos.flow.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowContextBuilder;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChaosPlan}.
 */
@DisplayName("ChaosPlan")
class ChaosPlanTest {

  private ChaosPlan chaosPlan;
  private EventEnvelope<Object> baseEnvelope;
  private FlowContext ctx;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    ChaosLimits limits = new ChaosLimits(10, 100, 1000, 30000L, 100, 25, 60000L);
    chaosPlan = new ChaosPlan(limits, mapper);

    var meta = new EventMetadata("corr-1", "idem-key:EVT-001", "tenant-1");
    baseEnvelope =
        new EventEnvelope<>(
            "EVT-001",
            "collection.completed",
            Instant.now(),
            "payment-service",
            "1.0",
            Map.of("amount", 100),
            meta);

    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of())
            .build();
    ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-001")
            .timestamp(Instant.now())
            .source("payment-service")
            .tenantId("tenant-1")
            .correlationId("corr-1")
            .resolvedSlots(Map.of())
            .request(request)
            .build();
  }

  @Nested
  @DisplayName("null options")
  class NullOptions {

    @Test
    @DisplayName("returns single normal PreparedSend when options is null")
    void returnsNormalSendWhenOptionsNull() {
      var sends = chaosPlan.expand(baseEnvelope, ctx, null);
      assertThat(sends).hasSize(1);
      assertThat(sends.get(0).chaosLabel()).isNull();
      assertThat(sends.get(0).delay()).isEqualTo(Duration.ZERO);
      assertThat(sends.get(0).rawOverride()).isNull();
    }
  }

  @Nested
  @DisplayName("Duplicate strategy")
  class DuplicateStrategy {

    @Test
    @DisplayName("produces n copies with same envelope and DUPLICATE label")
    void producesNCopies() {
      var options =
          new ChaosOptions(
              new ChaosOptions.DuplicateOptions(3), null, null, null, null, null, null);
      var sends = chaosPlan.expand(baseEnvelope, ctx, options);

      assertThat(sends).hasSize(3);
      sends.forEach(
          s -> {
            assertThat(s.envelope().eventId()).isEqualTo("EVT-001");
            assertThat(s.chaosLabel()).isEqualTo("DUPLICATE:3");
            assertThat(s.delay()).isEqualTo(Duration.ZERO);
          });
    }

    @Test
    @DisplayName("throws BadRequestException when count exceeds maxDuplicates")
    void throwsWhenExceedsLimit() {
      var options =
          new ChaosOptions(
              new ChaosOptions.DuplicateOptions(99), null, null, null, null, null, null);
      assertThatThrownBy(() -> chaosPlan.expand(baseEnvelope, ctx, options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }
  }

  @Nested
  @DisplayName("Malformed strategy")
  class MalformedStrategy {

    @Test
    @DisplayName("produces rawOverride with MALFORMED label")
    void producesRawOverride() {
      var mutations = List.of("negativeAmount");
      var options =
          new ChaosOptions(
              null, null, new ChaosOptions.MalformedOptions(mutations), null, null, null, null);
      var sends = chaosPlan.expand(baseEnvelope, ctx, options);

      assertThat(sends).hasSize(1);
      assertThat(sends.get(0).rawOverride()).isNotNull();
      assertThat(sends.get(0).chaosLabel()).isEqualTo("MALFORMED:negativeAmount");
    }
  }

  @Nested
  @DisplayName("Burst strategy")
  class BurstStrategy {

    @Test
    @DisplayName("produces count sends with unique event ids and BURST label")
    void producesCountSendsWithUniqueIds() {
      var options =
          new ChaosOptions(
              null, null, null, null, new ChaosOptions.BurstOptions(5, 100), null, null);
      var sends = chaosPlan.expand(baseEnvelope, ctx, options);

      assertThat(sends).hasSize(5);
      var eventIds = sends.stream().map(s -> s.envelope().eventId()).toList();
      assertThat(eventIds).doesNotHaveDuplicates();
      sends.forEach(s -> assertThat(s.chaosLabel()).isEqualTo("BURST:5"));
    }

    @Test
    @DisplayName("throws BadRequestException when count exceeds maxBurst")
    void throwsWhenCountExceedsLimit() {
      var options =
          new ChaosOptions(
              null, null, null, null, new ChaosOptions.BurstOptions(999, 100), null, null);
      assertThatThrownBy(() -> chaosPlan.expand(baseEnvelope, ctx, options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("throws BadRequestException when rate exceeds maxRatePerSecond")
    void throwsWhenRateExceedsLimit() {
      var options =
          new ChaosOptions(
              null, null, null, null, new ChaosOptions.BurstOptions(5, 9999), null, null);
      assertThatThrownBy(() -> chaosPlan.expand(baseEnvelope, ctx, options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }
  }

  @Nested
  @DisplayName("Delay strategy")
  class DelayStrategy {

    @Test
    @DisplayName("produces single send with non-zero delay and DELAY label")
    void producesSingleSendWithDelay() {
      var options =
          new ChaosOptions(
              null, null, null, null, null, new ChaosOptions.DelayOptions(500L, 0L), null);
      var sends = chaosPlan.expand(baseEnvelope, ctx, options);

      assertThat(sends).hasSize(1);
      assertThat(sends.get(0).delay()).isGreaterThanOrEqualTo(Duration.ofMillis(500));
      assertThat(sends.get(0).chaosLabel()).startsWith("DELAY:");
    }

    @Test
    @DisplayName("throws BadRequestException when delay exceeds maxDelayMs")
    void throwsWhenDelayExceedsLimit() {
      var options =
          new ChaosOptions(
              null, null, null, null, null, new ChaosOptions.DelayOptions(40000L, 0L), null);
      assertThatThrownBy(() -> chaosPlan.expand(baseEnvelope, ctx, options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }
  }

  @Nested
  @DisplayName("Unbalanced strategy")
  class UnbalancedStrategy {

    @Test
    @DisplayName("supported flow returns UNBALANCED label")
    void supportedFlowReturnsLabel() {
      var options =
          new ChaosOptions(
              null,
              null,
              null,
              new ChaosOptions.UnbalancedOptions(new BigDecimal("5.00")),
              null,
              null,
              null);
      var sends = chaosPlan.expand(baseEnvelope, ctx, options);

      assertThat(sends).hasSize(1);
      assertThat(sends.get(0).chaosLabel()).startsWith("UNBALANCED:delta=");
    }

    @Test
    @DisplayName("unsupported flow returns UNBALANCED:unsupported label")
    void unsupportedFlowReturnsUnsupportedLabel() {
      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.TOPUP_CONFIRMED)
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();
      var unsupportedCtx =
          FlowContextBuilder.builder()
              .eventId("EVT-002")
              .timestamp(Instant.now())
              .source("topup-service")
              .tenantId("tenant-1")
              .correlationId("corr-2")
              .resolvedSlots(Map.of())
              .request(request)
              .build();

      var options =
          new ChaosOptions(
              null,
              null,
              null,
              new ChaosOptions.UnbalancedOptions(new BigDecimal("5.00")),
              null,
              null,
              null);
      var sends = chaosPlan.expand(baseEnvelope, unsupportedCtx, options);

      assertThat(sends).hasSize(1);
      assertThat(sends.get(0).chaosLabel()).isEqualTo("UNBALANCED:unsupported");
    }
  }
}
