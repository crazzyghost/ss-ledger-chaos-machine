package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowBuilderRegistry;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-flow {@code transactionRequestIdField()} labels match the field the ledger files
 * under {@code transactionRequestId} (ADR-025) and that the registry resolves the concrete value.
 */
@DisplayName("transactionRequestIdField mapping")
class TransactionRequestIdFieldTest {

  private final TopicCatalog topics = new TopicCatalog();

  @Test
  @DisplayName("each transaction-bearing builder labels the documented ledger request-id field")
  void perBuilderMapping() {
    assertThat(new CollectionFlowBuilder(topics).transactionRequestIdField())
        .contains("transaction_id");
    assertThat(new DisbursementInitiatedFlowBuilder(topics).transactionRequestIdField())
        .contains("transaction_id");
    assertThat(new DisbursementFlowBuilder(topics).transactionRequestIdField())
        .contains("transaction_id");
    assertThat(new DisbursementFailedFlowBuilder(topics).transactionRequestIdField())
        .contains("transaction_id");
    assertThat(new SettlementInitiatedFlowBuilder(topics).transactionRequestIdField())
        .contains("settlement_request_id");
    assertThat(new SettlementCompletedFlowBuilder(topics).transactionRequestIdField())
        .contains("settlement_request_id");
    assertThat(new SettlementFailedFlowBuilder(topics).transactionRequestIdField())
        .contains("settlement_request_id");
    assertThat(new TopUpConfirmedFlowBuilder(topics).transactionRequestIdField())
        .contains("topup_request_id");
    assertThat(new TransferRequestedFlowBuilder(topics).transactionRequestIdField())
        .contains("transfer_request_id");
    assertThat(new TreasuryPrefundFlowBuilder(topics).transactionRequestIdField())
        .contains("prefund_request_id");
    assertThat(new TreasurySweepFlowBuilder(topics).transactionRequestIdField())
        .contains("sweep_request_id");
    assertThat(new TreasuryTransferFlowBuilder(topics).transactionRequestIdField())
        .contains("transfer_request_id");
    assertThat(
            new DisbursementBatchReservationRequestFlowBuilder(topics).transactionRequestIdField())
        .contains("batch_id");
    assertThat(new DisbursementBatchItemRequestFlowBuilder(topics).transactionRequestIdField())
        .contains("item_id");
    assertThat(new DisbursementBatchItemCompletedFlowBuilder(topics).transactionRequestIdField())
        .contains("item_id");
    assertThat(new DisbursementBatchItemFailedFlowBuilder(topics).transactionRequestIdField())
        .contains("item_id");
  }

  @Test
  @DisplayName("non-transactional flows default to empty")
  void defaultEmpty() {
    FlowBuilder<Object> nonTransactional =
        new FlowBuilder<>() {
          @Override
          public FlowType type() {
            return FlowType.ORGANIZATION_ONBOARDED;
          }

          @Override
          public String source() {
            return "x";
          }

          @Override
          public EventEnvelope<Object> build(FlowRequest request, FlowContext ctx) {
            return null;
          }

          @Override
          public String partitionKey(FlowContext ctx) {
            return "k";
          }
        };
    assertThat(nonTransactional.transactionRequestIdField()).isEmpty();
  }

  @Test
  @DisplayName("registry resolves the value from flowFields, trimmed; empty when blank or absent")
  void registryResolution() {
    var registry = new FlowBuilderRegistry(List.of(new CollectionFlowBuilder(topics)));

    var present =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of("transaction_id", "  TXN-9  "))
            .build();
    assertThat(registry.transactionRequestIdValue(present)).contains("TXN-9");

    var blank =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of("transaction_id", "   "))
            .build();
    assertThat(registry.transactionRequestIdValue(blank)).isEmpty();

    var absent =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of())
            .build();
    assertThat(registry.transactionRequestIdValue(absent)).isEmpty();
  }
}
