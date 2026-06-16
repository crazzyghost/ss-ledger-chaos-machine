package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.SettlementFailedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#SETTLEMENT_FAILED} events.
 *
 * <p>No slot resolution needed — all identifiers come directly from flow fields.
 */
@Component
public class SettlementFailedFlowBuilder implements FlowBuilder<SettlementFailedEventData> {

  private final TopicCatalog topicCatalog;

  public SettlementFailedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.SETTLEMENT_FAILED;
  }

  @Override
  public String source() {
    return "settlement-service";
  }

  @Override
  public EventEnvelope<SettlementFailedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    var data =
        new SettlementFailedEventData(
            f.getRequired("settlement_request_id"),
            f.getRequired("organization_id"),
            f.getRequired("virtual_account_id"),
            f.getRequired("failure_reason_code"),
            f.getRequired("failure_note"),
            f.getRequired("marked_by"),
            f.getTimestampOrNow("marked_at"));

    String idempotencyKey = "settlement-failed:" + ctx.eventId();
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
