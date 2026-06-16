package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.SettlementCompletedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#SETTLEMENT_COMPLETED} events.
 *
 * <p>Slots: {@code source} (client VA — debited), {@code destination} (system SETTLEMENT_ACCOUNT —
 * credited).
 */
@Component
public class SettlementCompletedFlowBuilder implements FlowBuilder<SettlementCompletedEventData> {

  private final TopicCatalog topicCatalog;

  public SettlementCompletedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.SETTLEMENT_COMPLETED;
  }

  @Override
  public String source() {
    return "settlement-service";
  }

  @Override
  public EventEnvelope<SettlementCompletedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    BigDecimal amount =
        request.amount() != null
            ? request.amount()
            : f.getBigDecimal("amount") != null ? f.getBigDecimal("amount") : BigDecimal.ZERO;

    var data =
        new SettlementCompletedEventData(
            f.getRequired("settlement_request_id"),
            f.getRequired("source_organization_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            amount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getOptional("completion_reference"),
            f.getRequired("completed_by"),
            f.getTimestampOrNow("completed_at"));

    String idempotencyKey = "settlement-completed:" + ctx.eventId();
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
        ctx.request().flowFields().getOrDefault("source_organization_id", ctx.eventId()).toString();
    return orgId;
  }
}
