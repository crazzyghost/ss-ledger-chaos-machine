package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.softspark.chaos.flow.FlowContextBuilder;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CollectionFlowBuilder}.
 */
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
  @DisplayName("type() returns COLLECTION_COMPLETED")
  void typeReturnsCollectionCompleted() {
    assertThat(builder.type()).isEqualTo(FlowType.COLLECTION_COMPLETED);
  }

  @Test
  @DisplayName("source() returns payment-service")
  void sourceReturnsPaymentService() {
    assertThat(builder.source()).isEqualTo("payment-service");
  }

  @Test
  @DisplayName("builds correct envelope with idempotency key collection-completed:<eventId>")
  void buildsCorrectEnvelope() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .grossAmount(new BigDecimal("110.00"))
            .netAmount(new BigDecimal("100.00"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "collection_request_id", "COL-001",
                    "merchant_reference", "MERCH-REF",
                    "provider_collection_id", "PROV-001"))
            .build();

    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-001")
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-001")
            .resolvedSlots(
                Map.of("source", "VA-FLOAT", "destination", "VA-MERCHANT", "fee", "VA-FEE"))
            .request(request)
            .build();

    var envelope = builder.build(request, ctx);

    assertThat(envelope.eventId()).isEqualTo("EVT-001");
    assertThat(envelope.eventType()).isEqualTo("collection.completed");
    assertThat(envelope.source()).isEqualTo("payment-service");
    assertThat(envelope.version()).isEqualTo("1.0");
    assertThat(envelope.metadata().idempotencyKey()).isEqualTo("collection-completed:EVT-001");
    assertThat(envelope.metadata().correlationId()).isEqualTo("CORR-001");
    assertThat(envelope.metadata().tenantId()).isEqualTo("org_123");

    var data = (com.softspark.chaos.flow.model.v1.CollectionCompletedEventData) envelope.data();
    assertThat(data.collectionRequestId()).isEqualTo("COL-001");
    assertThat(data.sourceVaId()).isEqualTo("VA-FLOAT");
    assertThat(data.destinationVaId()).isEqualTo("VA-MERCHANT");
    assertThat(data.grossAmount()).isEqualByComparingTo("110.00");
    assertThat(data.netAmount()).isEqualByComparingTo("100.00");
    assertThat(data.currency()).isEqualTo("GHS");
    assertThat(data.fees()).hasSize(1);
    assertThat(data.fees().get(0).amount()).isEqualByComparingTo("10.00");
    assertThat(data.fees().get(0).destinationVaId()).isEqualTo("VA-FEE");
  }

  @Test
  @DisplayName("partitionKey returns destination slot VA id")
  void partitionKeyReturnsDestinationSlot() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .slotOverrides(Map.of())
            .flowFields(Map.of())
            .build();

    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-002")
            .timestamp(Instant.now())
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-002")
            .resolvedSlots(Map.of("destination", "VA-MERCHANT-KEY"))
            .request(request)
            .build();

    assertThat(builder.partitionKey(ctx)).isEqualTo("VA-MERCHANT-KEY");
  }

  @Test
  @DisplayName("no fee entry when grossAmount equals netAmount")
  void noFeeEntryWhenZeroFee() {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.COLLECTION_COMPLETED)
            .grossAmount(new BigDecimal("100.00"))
            .netAmount(new BigDecimal("100.00"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "collection_request_id", "COL-002",
                    "merchant_reference", "MR-2",
                    "provider_collection_id", "P-2"))
            .build();

    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-003")
            .timestamp(Instant.now())
            .source("payment-service")
            .tenantId("org_123")
            .correlationId("CORR-003")
            .resolvedSlots(Map.of("source", "VA-FLOAT", "destination", "VA-MERCH"))
            .request(request)
            .build();

    var envelope = builder.build(request, ctx);
    var data = (com.softspark.chaos.flow.model.v1.CollectionCompletedEventData) envelope.data();

    assertThat(data.fees()).isEmpty();
  }
}
