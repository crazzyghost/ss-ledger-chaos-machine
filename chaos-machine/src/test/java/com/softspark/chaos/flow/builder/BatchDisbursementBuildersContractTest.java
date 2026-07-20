package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowContextBuilder;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the authoritative wire field set, topic, {@code operation} discriminator, structured
 * idempotency key, and {@code total_amount} invariant emitted by the Phase 016 batch-disbursement
 * builders.
 */
@DisplayName("Batch disbursement builders — wire contract")
class BatchDisbursementBuildersContractTest {

  private final TopicCatalog topics = new TopicCatalog();
  private final ObjectMapper mapper = new ObjectMapper();

  private FlowContext ctx(FlowRequest request, Map<String, String> slots) {
    return FlowContextBuilder.builder()
        .eventId("EVT")
        .timestamp(Instant.parse("2026-06-26T00:00:00Z"))
        .source("svc")
        .tenantId("org_123")
        .correlationId("CORR-XYZ")
        .resolvedSlots(slots)
        .request(request)
        .build();
  }

  @Test
  void should_emitReservationContract_with_computedTotalAndStructuredKey() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "batch_id", "BATCH-1",
                    "batch_correlation_id", "BCORR-1",
                    "merchant_id", "ORG-1",
                    "merchant_batch_ref", "BATCH-REF-1",
                    "total_principal_amount", "1000.0000",
                    "total_fees", "10",
                    "item_count", "4"))
            .build();
    var envelope =
        new DisbursementBatchReservationRequestFlowBuilder(topics)
            .build(request, ctx(request, Map.of("source", "VA-ORG", "destination", "VA-FLOAT")));
    String json = mapper.writeValueAsString(envelope.data());

    assertThat(envelope.eventType()).isEqualTo("disbursement.batch.initiated");
    assertThat(envelope.metadata().idempotencyKey())
        .isEqualTo("disbursement-batch-initiated:BATCH-1");
    assertThat(json)
        .contains("\"operation\":\"BATCH_RESERVATION_REQUEST\"")
        .contains("\"batch_id\":\"BATCH-1\"")
        .contains("\"batch_correlation_id\":\"BCORR-1\"")
        .contains("\"source_va_id\":\"VA-ORG\"")
        .contains("\"destination_va_id\":\"VA-FLOAT\"")
        .contains("\"total_principal_amount\":1000.0000")
        .contains("\"total_fees\":10")
        .contains("\"total_amount\":1010.0000")
        .contains("\"item_count\":4")
        .contains("\"disbursement_subtype\":\"DOMESTIC\"")
        .contains("\"merchant_batch_ref\":\"BATCH-REF-1\"")
        .contains("\"correlation_id\":\"CORR-XYZ\"")
        .contains("\"authorised_principal\":{")
        .contains("\"user_id\":\"chaos-operator\"")
        .contains("\"key_fingerprint\":\"ab:cd:ef:00\"");
  }

  @Test
  void should_keepTotalAmountAsPrincipalPlusFees_even_whenUnbalancedTotalSupplied() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "batch_id", "BATCH-1",
                    "batch_correlation_id", "BCORR-1",
                    "merchant_id", "ORG-1",
                    "merchant_batch_ref", "REF",
                    "total_principal_amount", "200",
                    "total_fees", "5",
                    "item_count", "2"))
            .build();
    var data =
        new DisbursementBatchReservationRequestFlowBuilder(topics)
            .build(request, ctx(request, Map.of("source", "VA-ORG", "destination", "VA-FLOAT")))
            .data();

    assertThat(data.totalAmount()).isEqualByComparingTo(new BigDecimal("205"));
  }

  @Test
  void should_emitItemRequestContract_with_operationAndStructuredKey() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.ofEntries(
                    Map.entry("batch_id", "BATCH-1"),
                    Map.entry("batch_correlation_id", "BCORR-1"),
                    Map.entry("item_id", "ITEM-1"),
                    Map.entry("item_sequence", "1"),
                    Map.entry("merchant_item_ref", "ITEM-REF-1"),
                    Map.entry("merchant_id", "ORG-1"),
                    Map.entry("virtual_account_id", "VA-ORG"),
                    Map.entry("principal_amount", "250.0000"),
                    Map.entry("item_fee", "2.5000"),
                    Map.entry("credit_provider_id", "PROVIDER_GH"),
                    Map.entry("credit_account_id", "0240000000")))
            .build();
    var envelope =
        new DisbursementBatchItemRequestFlowBuilder(topics).build(request, ctx(request, Map.of()));
    String json = mapper.writeValueAsString(envelope.data());

    assertThat(envelope.eventType()).isEqualTo("disbursement.batch.initiated");
    assertThat(envelope.metadata().idempotencyKey())
        .isEqualTo("disbursement-batch-initiated:BATCH-1:ITEM-1");
    assertThat(json)
        .contains("\"operation\":\"BATCH_ITEM_REQUEST\"")
        .contains("\"item_id\":\"ITEM-1\"")
        .contains("\"item_sequence\":1")
        .contains("\"virtual_account_id\":\"VA-ORG\"")
        .contains("\"principal_amount\":250.0000")
        .contains("\"item_fee\":2.5000")
        .contains("\"credit_provider_id\":\"PROVIDER_GH\"");
  }

  @Test
  void should_emitItemCompletedContract_with_feesAndStructuredKey() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "batch_id", "BATCH-1",
                    "item_id", "ITEM-1",
                    "item_sequence", "2",
                    "virtual_account_id", "VA-ORG",
                    "reservation_id", "RES-1",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PR-1",
                    "merchant_item_ref", "ITEM-REF-1",
                    "principal_amount", "250.0000"))
            .fees(
                List.of(new FeeInput("PLATFORM", new BigDecimal("2.5"), "BATCH-PLAT-01", "VA-FEE")))
            .build();
    EventEnvelope<?> envelope =
        new DisbursementBatchItemCompletedFlowBuilder(topics)
            .build(request, ctx(request, Map.of("source", "VA-ORG", "destination", "VA-FLOAT")));
    String json = mapper.writeValueAsString(envelope.data());

    assertThat(envelope.eventType()).isEqualTo("disbursement.batch.item.completed");
    assertThat(envelope.metadata().idempotencyKey())
        .isEqualTo("disbursement-batch-item-completed:BATCH-1:ITEM-1");
    assertThat(json)
        .contains("\"batch_id\":\"BATCH-1\"")
        .contains("\"item_id\":\"ITEM-1\"")
        .contains("\"virtual_account_id\":\"VA-ORG\"")
        .contains("\"reservation_id\":\"RES-1\"")
        .contains("\"fee_type\":\"PLATFORM\"")
        .contains("\"fee_code\":\"BATCH-PLAT-01\"")
        .contains("\"merchant_item_ref\":\"ITEM-REF-1\"")
        // completed never carries a failure code
        .doesNotContain("failure_code");
  }

  @Test
  void should_emitItemFailedContract_with_failureFieldsAndStructuredKey() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_ITEM_FAILED)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "batch_id", "BATCH-1",
                    "item_id", "ITEM-2",
                    "item_sequence", "3",
                    "virtual_account_id", "VA-ORG",
                    "reservation_id", "RES-1",
                    "provider_id", "PROVIDER_GH",
                    "merchant_item_ref", "ITEM-REF-2",
                    "principal_amount", "250.0000",
                    "failure_reason", "Recipient invalid",
                    "failure_code", "RECIPIENT_INVALID"))
            .build();
    EventEnvelope<?> envelope =
        new DisbursementBatchItemFailedFlowBuilder(topics).build(request, ctx(request, Map.of()));
    String json = mapper.writeValueAsString(envelope.data());

    assertThat(envelope.eventType()).isEqualTo("disbursement.batch.item.failed");
    assertThat(envelope.metadata().idempotencyKey())
        .isEqualTo("disbursement-batch-item-failed:BATCH-1:ITEM-2");
    assertThat(json)
        .contains("\"item_id\":\"ITEM-2\"")
        .contains("\"virtual_account_id\":\"VA-ORG\"")
        .contains("\"reservation_id\":\"RES-1\"")
        .contains("\"failure_reason\":\"Recipient invalid\"")
        .contains("\"failure_code\":\"RECIPIENT_INVALID\"");
  }
}
