package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowContextBuilder;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the authoritative wire field set emitted by the Phase 014 lifecycle builders by serializing
 * each event's data with the same snake_case Jackson config used on the wire.
 */
@DisplayName("Lifecycle builders — wire contract")
class LifecycleBuildersContractTest {

  private final TopicCatalog topics = new TopicCatalog();
  private final ObjectMapper mapper = new ObjectMapper();

  private FlowContext ctx(FlowType type, FlowRequest request, Map<String, String> slots) {
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
  void should_emitDisbursementInitiatedContract_when_built() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_INITIATED)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TX-1",
                    "merchant_id", "ORG-1",
                    "virtual_account_id", "VA-ORG",
                    "merchant_ref_id", "MR-1",
                    "principal_amount", "5000",
                    "fee_amount", "50",
                    "credit_provider_id", "PROVIDER_GH",
                    "credit_account_id", "0240000000"))
            .build();
    var data =
        new DisbursementInitiatedFlowBuilder(topics)
            .build(request, ctx(FlowType.DISBURSEMENT_INITIATED, request, Map.of()))
            .data();
    String json = mapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"transaction_id\":\"TX-1\"")
        .contains("\"merchant_id\":\"ORG-1\"")
        .contains("\"virtual_account_id\":\"VA-ORG\"")
        .contains("\"principal_amount\":5000")
        .contains("\"fee_amount\":50")
        .contains("\"disbursement_subtype\":\"DOMESTIC\"")
        .contains("\"corridor\":\"GH-GH\"")
        .contains("\"correlation_id\":\"CORR-XYZ\"")
        .contains("\"authorised_principal\":{")
        .contains("\"user_id\":\"chaos-operator\"")
        .contains("\"key_fingerprint\":\"ab:cd:ef:00\"")
        // initiated carries NO reservation_id
        .doesNotContain("reservation_id");
  }

  @Test
  void should_emitDisbursementCompletedContract_when_built() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_COMPLETED)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TX-1",
                    "reservation_id", "RES-1",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PR-1",
                    "merchant_ref_id", "MR-1",
                    "principal_amount", "5000"))
            .fees(List.of(new FeeInput("PLATFORM", new BigDecimal("50"), "DISB-PLAT-01", "VA-FEE")))
            .build();
    var data =
        new DisbursementFlowBuilder(topics)
            .build(
                request,
                ctx(
                    FlowType.DISBURSEMENT_COMPLETED,
                    request,
                    Map.of("source", "VA-ORG", "destination", "VA-SETTLE")))
            .data();
    String json = mapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"transaction_id\":\"TX-1\"")
        .contains("\"source_va_id\":\"VA-ORG\"")
        .contains("\"destination_va_id\":\"VA-SETTLE\"")
        .contains("\"reservation_id\":\"RES-1\"")
        .contains("\"principal_amount\":5000")
        .contains("\"fee_code\":\"DISB-PLAT-01\"")
        .contains("\"fee_type\":\"PLATFORM\"")
        .contains("\"merchant_ref_id\":\"MR-1\"");
  }

  @Test
  void should_emitDisbursementFailedContract_when_built() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_FAILED)
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TX-1",
                    "virtual_account_id", "VA-ORG",
                    "reservation_id", "RES-1",
                    "provider_id", "PROVIDER_GH",
                    "merchant_ref_id", "MR-1",
                    "principal_amount", "5000",
                    "failure_reason", "Bank rejected",
                    "failure_code", "RECIPIENT_INVALID"))
            .build();
    var data =
        new DisbursementFailedFlowBuilder(topics)
            .build(request, ctx(FlowType.DISBURSEMENT_FAILED, request, Map.of()))
            .data();
    String json = mapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"transaction_id\":\"TX-1\"")
        .contains("\"virtual_account_id\":\"VA-ORG\"")
        .contains("\"reservation_id\":\"RES-1\"")
        .contains("\"failure_reason\":\"Bank rejected\"")
        .contains("\"failure_code\":\"RECIPIENT_INVALID\"")
        .contains("\"principal_amount\":5000");
  }

  @Test
  void should_emitSettlementVaIdDestination_when_settlementCompletedBuilt() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.SETTLEMENT_COMPLETED)
            .amount(new BigDecimal("1500"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "settlement_request_id", "SR-1",
                    "source_organization_id", "ORG-1",
                    "completion_reference", "COMP-1",
                    "completed_by", "ops@acme.example"))
            .build();
    var data =
        new SettlementCompletedFlowBuilder(topics)
            .build(
                request,
                ctx(
                    FlowType.SETTLEMENT_COMPLETED,
                    request,
                    Map.of("source", "VA-ORG", "destination", "VA-SETTLE")))
            .data();
    String json = mapper.writeValueAsString(data);

    // The confirmed destination field name is settlement_va_id (NOT destination_va_id).
    assertThat(json)
        .contains("\"settlement_va_id\":\"VA-SETTLE\"")
        .contains("\"source_va_id\":\"VA-ORG\"")
        .contains("\"source_organization_id\":\"ORG-1\"")
        .contains("\"completion_reference\":\"COMP-1\"")
        .doesNotContain("destination_va_id");
  }

  @Test
  void should_emitOptionalDestination_when_settlementFailedBuilt() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.SETTLEMENT_FAILED)
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "settlement_request_id", "SR-1",
                    "organization_id", "ORG-1",
                    "virtual_account_id", "VA-ORG",
                    "destination_va_id", "VA-SETTLE",
                    "failure_reason_code", "BANK_REJECTED",
                    "failure_note", "Bank rejected",
                    "marked_by", "ops@acme.example"))
            .build();
    var data =
        new SettlementFailedFlowBuilder(topics)
            .build(request, ctx(FlowType.SETTLEMENT_FAILED, request, Map.of()))
            .data();
    String json = mapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"destination_va_id\":\"VA-SETTLE\"")
        .contains("\"failure_reason_code\":\"BANK_REJECTED\"")
        .contains("\"virtual_account_id\":\"VA-ORG\"");
  }
}
