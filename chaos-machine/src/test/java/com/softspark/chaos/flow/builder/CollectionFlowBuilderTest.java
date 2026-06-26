package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.softspark.chaos.flow.FlowContextBuilder;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.CollectionCompletedEventData;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link CollectionFlowBuilder} against the corrected ledger contract. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionFlowBuilder")
class CollectionFlowBuilderTest {

  @Mock private TopicCatalog topicCatalog;

  private CollectionFlowBuilder builder;

  @BeforeEach
  void setUp() {
    lenient()
        .when(topicCatalog.topicFor(FlowType.COLLECTION_COMPLETED))
        .thenReturn("collection.completed");
    builder = new CollectionFlowBuilder(topicCatalog);
  }

  @Test
  void should_returnCollectionCompleted_when_typeQueried() {
    assertThat(builder.type()).isEqualTo(FlowType.COLLECTION_COMPLETED);
  }

  @Test
  void should_returnPaymentService_when_sourceQueried() {
    assertThat(builder.source()).isEqualTo("payment-service");
  }

  @Test
  void should_emitLedgerContractFields_when_feesSupplied() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .amount(new BigDecimal("1000.0000"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TXN-001",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PROV-REF-1",
                    "merchant_ref_id", "MERCH-REF-1"))
            .fees(List.of(new FeeInput("PLATFORM", new BigDecimal("10.0000"), "FEE-1", "VA-FEE")))
            .build();

    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-001")
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-001")
            .resolvedSlots(Map.of("source", "VA-FLOAT", "destination", "VA-MERCHANT"))
            .request(request)
            .build();

    var envelope = builder.build(request, ctx);
    assertThat(envelope.eventType()).isEqualTo("collection.completed");
    assertThat(envelope.metadata().idempotencyKey()).isEqualTo("collection-completed:EVT-001");

    CollectionCompletedEventData data = envelope.data();
    assertThat(data.transactionId()).isEqualTo("TXN-001");
    assertThat(data.sourceVaId()).isEqualTo("VA-FLOAT");
    assertThat(data.destinationVaId()).isEqualTo("VA-MERCHANT");
    assertThat(data.providerId()).isEqualTo("PROVIDER_GH");
    assertThat(data.providerReferenceId()).isEqualTo("PROV-REF-1");
    assertThat(data.netAmount()).isEqualByComparingTo("1000.0000");
    assertThat(data.currency()).isEqualTo("GHS");
    assertThat(data.merchantRefId()).isEqualTo("MERCH-REF-1");
    assertThat(data.commissionSplitId()).isNull();
    assertThat(data.fees()).hasSize(1);
    assertThat(data.fees().get(0).feeType()).isEqualTo("PLATFORM");
    assertThat(data.fees().get(0).amount()).isEqualByComparingTo("10.0000");
    assertThat(data.fees().get(0).feeCode()).isEqualTo("FEE-1");
    assertThat(data.fees().get(0).destinationVaId()).isEqualTo("VA-FEE");
  }

  @Test
  void should_computeGrossAsNetPlusFees_when_feesSupplied() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .amount(new BigDecimal("1000.0000"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TXN-002",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PROV-REF-2",
                    "merchant_ref_id", "MERCH-REF-2"))
            .fees(
                List.of(
                    new FeeInput("PLATFORM", new BigDecimal("10.0000"), "FEE-A", "VA-FEE-1"),
                    new FeeInput("PROVIDER", new BigDecimal("5.0000"), "FEE-B", "VA-FEE-2")))
            .build();

    var ctx = ctx("EVT-002", request);
    var data = builder.build(request, ctx).data();

    assertThat(data.netAmount()).isEqualByComparingTo("1000.0000");
    assertThat(data.grossAmount()).isEqualByComparingTo("1015.0000");
    assertThat(data.fees()).hasSize(2);
  }

  @Test
  void should_autogenerateFeeCode_when_feeCodeBlank() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .amount(new BigDecimal("1000.0000"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TXN-003",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PROV-REF-3",
                    "merchant_ref_id", "MERCH-REF-3"))
            .fees(List.of(new FeeInput("PLATFORM", new BigDecimal("10.0000"), null, "VA-FEE")))
            .build();

    var data = builder.build(request, ctx("EVT-003", request)).data();
    assertThat(data.fees().get(0).feeCode()).isNotBlank();
  }

  @Test
  void should_deriveSingleFeeFromGrossMinusNet_when_noFeesSupplied() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .grossAmount(new BigDecimal("110.00"))
            .netAmount(new BigDecimal("100.00"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "transaction_id", "TXN-004",
                    "provider_id", "PROVIDER_GH",
                    "provider_reference_id", "PROV-REF-4",
                    "merchant_ref_id", "MERCH-REF-4"))
            .build();

    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-004")
            .timestamp(Instant.now())
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-004")
            .resolvedSlots(Map.of("source", "VA-FLOAT", "destination", "VA-MERCH", "fee", "VA-FEE"))
            .request(request)
            .build();

    var data = builder.build(request, ctx).data();
    assertThat(data.fees()).hasSize(1);
    assertThat(data.fees().get(0).amount()).isEqualByComparingTo("10.00");
    assertThat(data.fees().get(0).destinationVaId()).isEqualTo("VA-FEE");
    assertThat(data.fees().get(0).feeType()).isEqualTo("PLATFORM");
  }

  @Test
  void should_returnDestinationSlot_when_partitionKeyQueried() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of())
            .build();
    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-005")
            .timestamp(Instant.now())
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-005")
            .resolvedSlots(Map.of("destination", "VA-MERCHANT-KEY"))
            .request(request)
            .build();

    assertThat(builder.partitionKey(ctx)).isEqualTo("VA-MERCHANT-KEY");
  }

  private static com.softspark.chaos.flow.FlowContext ctx(
      String eventId, com.softspark.chaos.flow.FlowRequest request) {
    return FlowContextBuilder.builder()
        .eventId(eventId)
        .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
        .source("payment-service")
        .tenantId("org_123")
        .correlationId("CORR")
        .resolvedSlots(Map.of("source", "VA-FLOAT", "destination", "VA-MERCHANT"))
        .request(request)
        .build();
  }
}
