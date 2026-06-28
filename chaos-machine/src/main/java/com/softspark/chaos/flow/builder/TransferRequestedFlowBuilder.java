package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.TransferRequestedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#TRANSFER_REQUESTED} events.
 *
 * <p>Slots: {@code source} (client VA of source org), {@code destination} (client VA of destination
 * org).
 */
@Component
public class TransferRequestedFlowBuilder implements FlowBuilder<TransferRequestedEventData> {

  private final TopicCatalog topicCatalog;

  public TransferRequestedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.TRANSFER_REQUESTED;
  }

  @Override
  public java.util.Optional<String> transactionRequestIdField() {
    return java.util.Optional.of("transfer_request_id");
  }

  @Override
  public String source() {
    return "transfer-service";
  }

  @Override
  public EventEnvelope<TransferRequestedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    BigDecimal amount =
        request.amount() != null
            ? request.amount()
            : f.getBigDecimal("amount") != null ? f.getBigDecimal("amount") : BigDecimal.ZERO;

    var data =
        new TransferRequestedEventData(
            f.getRequired("transfer_request_id"),
            f.getRequired("source_organization_id"),
            f.getRequired("destination_organization_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            amount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getRequired("narrative"),
            f.getRequired("initiated_by"),
            f.getTimestampOrNow("initiated_at"));

    String idempotencyKey = "transfer-requested:" + ctx.eventId();
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
