package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.SettlementInitiatedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#SETTLEMENT_INITIATED} events.
 *
 * <p>No slot resolution needed — {@code virtualAccountId} comes directly from flow fields.
 */
@Component
public class SettlementInitiatedFlowBuilder implements FlowBuilder<SettlementInitiatedEventData> {

  private final TopicCatalog topicCatalog;

  public SettlementInitiatedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.SETTLEMENT_INITIATED;
  }

  @Override
  public String source() {
    return "settlement-service";
  }

  @Override
  public EventEnvelope<SettlementInitiatedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    BigDecimal amount =
        request.amount() != null
            ? request.amount()
            : f.getBigDecimal("amount") != null ? f.getBigDecimal("amount") : BigDecimal.ZERO;

    var data =
        new SettlementInitiatedEventData(
            f.getRequired("settlement_request_id"),
            f.getRequired("virtual_account_id"),
            f.getRequired("organization_id"),
            amount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getRequired("destination_bank_account"),
            f.getRequired("destination_bank"),
            f.getRequired("approved_by"),
            f.getTimestampOrNow("approved_at"));

    String idempotencyKey = "settlement-initiated:" + ctx.eventId();
    var metadata = new EventMetadata(ctx.correlationId(), idempotencyKey, ctx.tenantId());

    return new EventEnvelope<>(
        ctx.eventId(),
        topicCatalog.topicFor(type()),
        ctx.timestamp(),
        source(),
        "1.0",
        data,
        metadata);
  }

  @Override
  public String partitionKey(FlowContext ctx) {
    String orgId =
        ctx.request().flowFields().getOrDefault("organization_id", ctx.eventId()).toString();
    return orgId;
  }
}
