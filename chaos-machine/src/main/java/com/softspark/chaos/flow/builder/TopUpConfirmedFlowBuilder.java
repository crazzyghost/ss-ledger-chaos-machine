package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.TopUpConfirmedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#TOPUP_CONFIRMED} events.
 *
 * <p>Slots: {@code source} (client VA — debited), {@code destination} (system PLATFORM_FLOAT —
 * credited).
 */
@Component
public class TopUpConfirmedFlowBuilder implements FlowBuilder<TopUpConfirmedEventData> {

  private final TopicCatalog topicCatalog;

  public TopUpConfirmedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.TOPUP_CONFIRMED;
  }

  @Override
  public String source() {
    return "topup-service";
  }

  @Override
  public EventEnvelope<TopUpConfirmedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    BigDecimal amount =
        request.amount() != null
            ? request.amount()
            : f.getBigDecimal("amount") != null ? f.getBigDecimal("amount") : BigDecimal.ZERO;

    var data =
        new TopUpConfirmedEventData(
            f.getRequired("topup_request_id"),
            f.getRequired("organization_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            amount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getOptional("source_payment_reference"),
            f.getRequired("approved_by"),
            f.getTimestampOrNow("approved_at"));

    String idempotencyKey = "topup-confirmed:" + ctx.eventId();
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
