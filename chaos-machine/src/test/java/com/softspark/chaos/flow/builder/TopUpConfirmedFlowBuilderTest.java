package com.softspark.chaos.flow.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Unit tests for {@link TopUpConfirmedFlowBuilder} against the ledger wire contract. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TopUpConfirmedFlowBuilder")
class TopUpConfirmedFlowBuilderTest {

  @Mock private TopicCatalog topicCatalog;

  private TopUpConfirmedFlowBuilder builder;

  @BeforeEach
  void setUp() {
    lenient()
        .when(topicCatalog.topicFor(FlowType.TOPUP_CONFIRMED))
        .thenReturn("organization.topup.confirmed");
    builder = new TopUpConfirmedFlowBuilder(topicCatalog);
  }

  @Test
  void should_emitRoleBasedVaWireNames_when_serialized() throws Exception {
    var request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.TOPUP_CONFIRMED)
            .amount(new BigDecimal("500.00"))
            .currency("GHS")
            .slotOverrides(Map.of())
            .flowFields(
                Map.of(
                    "topup_request_id", "TUR-1",
                    "organization_id", "ORG-1",
                    "approved_by", "ops@acme.example"))
            .build();
    var ctx =
        FlowContextBuilder.builder()
            .eventId("EVT-1")
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .source("topup-service")
            .tenantId("org_123")
            .correlationId("CORR-1")
            .resolvedSlots(Map.of("source", "VA-ORG", "destination", "VA-FLOAT"))
            .request(request)
            .build();

    var data = builder.build(request, ctx).data();
    String json = new ObjectMapper().writeValueAsString(data);

    // VA ids are named by ledger role; the slot->wire mapping is reversed vs. collection:
    // source slot (organization, credited) -> organization_va_id;
    // destination slot (system, debited) -> system_va_id.
    assertThat(json)
        .contains("\"organization_va_id\":\"VA-ORG\"")
        .contains("\"system_va_id\":\"VA-FLOAT\"")
        .doesNotContain("source_va_id")
        .doesNotContain("destination_va_id");
  }
}
