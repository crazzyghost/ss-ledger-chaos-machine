package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.TreasuryTransferCompletedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#TREASURY_TRANSFER_COMPLETED} events.
 *
 * <p>Slots: {@code source} (any system VA), {@code destination} (any system VA).
 */
@Component
public class TreasuryTransferFlowBuilder
    implements FlowBuilder<TreasuryTransferCompletedEventData> {

  private final TopicCatalog topicCatalog;

  public TreasuryTransferFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.TREASURY_TRANSFER_COMPLETED;
  }

  @Override
  public String source() {
    return "treasury-service";
  }

  @Override
  public EventEnvelope<TreasuryTransferCompletedEventData> build(
      FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    BigDecimal amount =
        request.amount() != null
            ? request.amount()
            : f.getBigDecimal("amount") != null ? f.getBigDecimal("amount") : BigDecimal.ZERO;

    var data =
        new TreasuryTransferCompletedEventData(
            f.getRequired("transfer_request_id"),
            f.getRequired("source_channel"),
            f.getRequired("destination_channel"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            amount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getOptional("completion_reference"),
            f.getRequired("completed_by"),
            f.getTimestampOrNow("completed_at"));

    String idempotencyKey = "treasury-transfer-completed:" + ctx.eventId();
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
    return ctx.resolvedSlots().getOrDefault("source", ctx.eventId());
  }
}
