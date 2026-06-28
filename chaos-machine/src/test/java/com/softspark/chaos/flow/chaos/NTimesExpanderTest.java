package com.softspark.chaos.flow.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Unit tests for {@link NTimesExpander}. */
@DisplayName("NTimesExpander")
class NTimesExpanderTest {

  private static final ChaosLimits LIMITS = new ChaosLimits(10, 100, 1000, 30000L, 250, 25, 60000L, 100);

  private FlowCatalogProvider catalogProvider;
  private NTimesExpander expander;

  @BeforeEach
  void setUp() {
    catalogProvider = new FlowCatalogProvider(new TopicCatalog());
    expander = new NTimesExpander(catalogProvider, LIMITS);
  }

  private static NTimesOptions burst(int count) {
    return new NTimesOptions(count, Pacing.BURST, ExecutionMode.SYNC, null, null, null);
  }

  private FlowRequest topUpBase(NTimesOptions options, String correlationId) {
    Map<String, Object> flowFields = new LinkedHashMap<>();
    flowFields.put("topup_request_id", "REQ-ORIGINAL");
    flowFields.put("organization_id", "org-123");
    flowFields.put("currency", "GHS");
    flowFields.put("amount", "1000.0000");
    flowFields.put("source_payment_reference", "PAYREF-ORIGINAL");
    flowFields.put("approved_by", "ops@acme.example");
    return FlowRequestBuilder.builder()
        .flowType(FlowType.TOPUP_CONFIRMED)
        .correlationId(correlationId)
        .tenantId("tenant-x")
        .channel("momo")
        .amount(new BigDecimal("1000.0000"))
        .currency("GHS")
        .slotOverrides(Map.of("source", "va-src", "destination", "va-dst"))
        .chaos(new ChaosOptions(null, null, null, null, null, null, options))
        .flowFields(flowFields)
        .build();
  }

  @Nested
  @DisplayName("expand — distinctness & invariants")
  class Expand {

    @Test
    @DisplayName("returns exactly count requests")
    void returnsExactlyCount() {
      var requests = expander.expand(topUpBase(burst(5), null));
      assertThat(requests).hasSize(5);
    }

    @Test
    @DisplayName("re-rolls every autogen UUID_V4 field to a distinct value per iteration")
    void rerollsAutogenFields() {
      var requests = expander.expand(topUpBase(burst(4), null));

      var requestIds = requests.stream().map(r -> r.flowFields().get("topup_request_id")).toList();
      var paymentRefs =
          requests.stream().map(r -> r.flowFields().get("source_payment_reference")).toList();

      assertThat(requestIds).doesNotHaveDuplicates();
      assertThat(paymentRefs).doesNotHaveDuplicates();
      // the original seed values must be replaced
      assertThat(requestIds).doesNotContain("REQ-ORIGINAL");
      assertThat(paymentRefs).doesNotContain("PAYREF-ORIGINAL");
    }

    @Test
    @DisplayName("shares one generated correlation id when base has none")
    void sharesGeneratedCorrelationId() {
      var requests = expander.expand(topUpBase(burst(3), null));
      var correlationIds = requests.stream().map(FlowRequest::correlationId).distinct().toList();
      assertThat(correlationIds).hasSize(1);
      assertThat(correlationIds.get(0)).isNotBlank();
    }

    @Test
    @DisplayName("preserves the caller's correlation id override across all iterations")
    void preservesCorrelationOverride() {
      var requests = expander.expand(topUpBase(burst(3), "corr-fixed"));
      assertThat(requests).allSatisfy(r -> assertThat(r.correlationId()).isEqualTo("corr-fixed"));
    }

    @Test
    @DisplayName("holds slots, amount, currency, channel and org-id constant")
    void holdsBusinessFieldsConstant() {
      var requests = expander.expand(topUpBase(burst(3), null));
      assertThat(requests)
          .allSatisfy(
              r -> {
                assertThat(r.slotOverrides()).containsEntry("source", "va-src");
                assertThat(r.slotOverrides()).containsEntry("destination", "va-dst");
                assertThat(r.amount()).isEqualByComparingTo("1000.0000");
                assertThat(r.currency()).isEqualTo("GHS");
                assertThat(r.channel()).isEqualTo("momo");
                assertThat(r.flowFields()).containsEntry("organization_id", "org-123");
                assertThat(r.flowFields()).containsEntry("currency", "GHS");
              });
    }

    @Test
    @DisplayName("strips chaos so each iteration runs the normal single-publish path")
    void stripsChaos() {
      var requests = expander.expand(topUpBase(burst(2), null));
      assertThat(requests).allSatisfy(r -> assertThat(r.chaos()).isNull());
    }

    @Test
    @DisplayName("falls back to *_request_id convention when no descriptor marks autogen")
    void fallbackByRequestIdSuffix() {
      // ORGANIZATION_VA_UPDATED is a hidden flow with minimal descriptors (no autogen rules), so
      // the expander falls back to re-rolling *_request_id-suffixed flow fields.
      Map<String, Object> flowFields = new LinkedHashMap<>();
      flowFields.put("some_request_id", "REQ-ORIGINAL");
      flowFields.put("status", "ACTIVE");
      var base =
          FlowRequestBuilder.builder()
              .flowType(FlowType.ORGANIZATION_VA_UPDATED)
              .slotOverrides(Map.of())
              .chaos(new ChaosOptions(null, null, null, null, null, null, burst(3)))
              .flowFields(flowFields)
              .build();

      var requests = expander.expand(base);
      var ids = requests.stream().map(r -> r.flowFields().get("some_request_id")).toList();
      assertThat(ids).doesNotHaveDuplicates();
      assertThat(ids).doesNotContain("REQ-ORIGINAL");
      // a non-request-id field is left untouched
      assertThat(requests)
          .allSatisfy(r -> assertThat(r.flowFields()).containsEntry("status", "ACTIVE"));
    }

    @Test
    @DisplayName("re-rolls ULID reference fields too, not just the UUID id (collection dedup fix)")
    void rerollsUlidReferenceFields() {
      // Collection's transaction_id is UUID-autogen and provider_reference_id/merchant_ref_id are
      // ULID-autogen. If only the UUID id were re-rolled, the ledger would dedupe the journal entries
      // on the (constant) provider reference. Every autogen reference must vary per iteration.
      var base =
          FlowRequestBuilder.builder()
              .flowType(FlowType.COLLECTION_COMPLETED)
              .slotOverrides(Map.of())
              .chaos(new ChaosOptions(null, null, null, null, null, null, burst(4)))
              .flowFields(new LinkedHashMap<>())
              .build();

      var requests = expander.expand(base);
      assertThat(requests).hasSize(4);

      var txIds = requests.stream().map(r -> r.flowFields().get("transaction_id")).toList();
      var providerRefs =
          requests.stream().map(r -> r.flowFields().get("provider_reference_id")).toList();
      var merchantRefs =
          requests.stream().map(r -> r.flowFields().get("merchant_ref_id")).toList();

      assertThat(txIds).doesNotHaveDuplicates().allSatisfy(v -> assertThat(v).isNotNull());
      assertThat(providerRefs).doesNotHaveDuplicates().allSatisfy(v -> assertThat(v).isNotNull());
      assertThat(merchantRefs).doesNotHaveDuplicates().allSatisfy(v -> assertThat(v).isNotNull());
    }
  }

  @Nested
  @DisplayName("delayFor — pacing")
  class DelayFor {

    @Test
    @DisplayName("BURST is always zero")
    void burstZero() {
      var options = burst(5);
      assertThat(expander.delayFor(options, 0)).isEqualTo(Duration.ZERO);
      assertThat(expander.delayFor(options, 3)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("LINEAR returns the fixed gap, with no gap before the first iteration")
    void linearFixed() {
      var options = new NTimesOptions(5, Pacing.LINEAR, ExecutionMode.SYNC, 250L, null, null);
      assertThat(expander.delayFor(options, 0)).isEqualTo(Duration.ZERO);
      assertThat(expander.delayFor(options, 1)).isEqualTo(Duration.ofMillis(250));
      assertThat(expander.delayFor(options, 4)).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    @DisplayName("RANDOM returns a value within [min, max]")
    void randomInRange() {
      var options = new NTimesOptions(20, Pacing.RANDOM, ExecutionMode.SYNC, null, 100L, 300L);
      for (int i = 1; i < 20; i++) {
        Duration d = expander.delayFor(options, i);
        assertThat(d.toMillis()).isBetween(100L, 300L);
      }
      assertThat(expander.delayFor(options, 0)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("RANDOM with min == max is degenerate to that value")
    void randomDegenerate() {
      var options = new NTimesOptions(5, Pacing.RANDOM, ExecutionMode.SYNC, null, 200L, 200L);
      assertThat(expander.delayFor(options, 2)).isEqualTo(Duration.ofMillis(200));
    }
  }

  @Nested
  @DisplayName("validate — rejection matrix")
  class Validate {

    @Test
    @DisplayName("rejects count < 1")
    void rejectsZeroCount() {
      assertThatThrownBy(() -> expander.validate(burst(0)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("at least 1");
    }

    @Test
    @DisplayName("rejects count > maxNTimes")
    void rejectsOverCount() {
      assertThatThrownBy(() -> expander.validate(burst(251)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("rejects null pacing")
    void rejectsNullPacing() {
      var options = new NTimesOptions(3, null, ExecutionMode.SYNC, null, null, null);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("pacing is required");
    }

    @Test
    @DisplayName("rejects LINEAR without fixedDelayMs")
    void rejectsLinearMissingDelay() {
      var options = new NTimesOptions(3, Pacing.LINEAR, ExecutionMode.SYNC, null, null, null);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("fixedDelayMs");
    }

    @Test
    @DisplayName("rejects LINEAR fixedDelayMs over maxDelayMs")
    void rejectsLinearOverMax() {
      var options = new NTimesOptions(3, Pacing.LINEAR, ExecutionMode.SYNC, 40000L, null, null);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("rejects RANDOM without min/max")
    void rejectsRandomMissing() {
      var options = new NTimesOptions(3, Pacing.RANDOM, ExecutionMode.SYNC, null, null, null);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("minDelayMs and maxDelayMs");
    }

    @Test
    @DisplayName("rejects RANDOM with min > max")
    void rejectsRandomInverted() {
      var options = new NTimesOptions(3, Pacing.RANDOM, ExecutionMode.SYNC, null, 500L, 100L);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("must not exceed");
    }

    @Test
    @DisplayName("rejects RANDOM with negative min")
    void rejectsRandomNegative() {
      var options = new NTimesOptions(3, Pacing.RANDOM, ExecutionMode.SYNC, null, -1L, 100L);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("rejects RANDOM with max over maxDelayMs")
    void rejectsRandomOverMax() {
      var options = new NTimesOptions(3, Pacing.RANDOM, ExecutionMode.SYNC, null, 0L, 40000L);
      assertThatThrownBy(() -> expander.validate(options))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("accepts a valid BURST/LINEAR/RANDOM configuration")
    void acceptsValid() {
      expander.validate(burst(50));
      expander.validate(
          new NTimesOptions(10, Pacing.LINEAR, ExecutionMode.ASYNC, 1000L, null, null));
      expander.validate(new NTimesOptions(10, Pacing.RANDOM, ExecutionMode.ASYNC, null, 0L, 1000L));
    }
  }

  @Nested
  @DisplayName("catalog — runner-visible flows")
  class CatalogCoverage {

    static Stream<FlowCatalogEntry> runnerVisibleFlows() {
      // Batch-disbursement (batchGroup) is driven by its own runner/wizard, not the N-Times
      // expander, and has no single re-rollable transaction id — exclude it from this coverage.
      return new FlowCatalogProvider(new TopicCatalog())
          .catalog().stream()
          .filter(FlowCatalogEntry::runnerVisible)
          .filter(e -> e.batchGroup() == null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runnerVisibleFlows")
    @DisplayName("each runner-visible flow has exactly one re-rollable business id field")
    void exactlyOneBusinessIdField(FlowCatalogEntry entry) {
      // The canonical business/transaction id is a single autogen UUID_V4 field named either
      // *_request_id (topup/transfer/treasury/settlement) or transaction_id (collection,
      // disbursement). Other autogen fields (e.g. correlation_id) are tracing, not the business id.
      List<String> requestIdAutogenFields =
          entry.fields().stream()
              .filter(f -> f.autogen() == AutogenRule.UUID_V4)
              .map(FlowFieldDescriptor::name)
              .filter(name -> name.endsWith("_request_id") || name.equals("transaction_id"))
              .toList();

      assertThat(requestIdAutogenFields)
          .as("business id autogen field for %s", entry.flowType())
          .hasSize(1);

      String businessIdField = requestIdAutogenFields.get(0);
      var base =
          FlowRequestBuilder.builder()
              .flowType(entry.flowType())
              .slotOverrides(Map.of())
              .chaos(
                  new ChaosOptions(
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      new NTimesOptions(3, Pacing.BURST, ExecutionMode.SYNC, null, null, null)))
              .flowFields(new LinkedHashMap<>())
              .build();

      var requests = new NTimesExpander(catalogProvider, LIMITS).expand(base);
      var values = requests.stream().map(r -> r.flowFields().get(businessIdField)).toList();
      assertThat(values).doesNotHaveDuplicates();
      assertThat(values).allSatisfy(v -> assertThat(v).isNotNull());
    }
  }
}
