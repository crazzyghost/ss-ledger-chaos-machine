package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.CollectionCompletedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#COLLECTION_COMPLETED} events.
 *
 * <p>Slots: {@code source} (system PLATFORM_FLOAT — debited), {@code destination} (merchant client
 * VA — credited), {@code fee} (system PLATFORM_FEE — receives the platform fee).
 */
@Component
public class CollectionFlowBuilder implements FlowBuilder<CollectionCompletedEventData> {

  private final TopicCatalog topicCatalog;

  public CollectionFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.COLLECTION_COMPLETED;
  }

  @Override
  public String source() {
    return "payment-service";
  }

  @Override
  public EventEnvelope<CollectionCompletedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    BigDecimal grossAmount =
        request.grossAmount() != null
            ? request.grossAmount()
            : f.getBigDecimal("gross_amount") != null
                ? f.getBigDecimal("gross_amount")
                : BigDecimal.ZERO;
    BigDecimal netAmount =
        request.netAmount() != null
            ? request.netAmount()
            : f.getBigDecimal("net_amount") != null ? f.getBigDecimal("net_amount") : grossAmount;

    BigDecimal feeAmount = grossAmount.subtract(netAmount);
    String feeVaId = ctx.resolvedSlots().getOrDefault("fee", "");
    String feeType = f.getOptional("fee_type") != null ? f.getOptional("fee_type") : "PLATFORM_FEE";

    List<CollectionCompletedEventData.FeeEntry> fees =
        feeAmount.compareTo(BigDecimal.ZERO) > 0
            ? List.of(new CollectionCompletedEventData.FeeEntry(feeType, feeAmount, feeVaId))
            : List.of();

    var data =
        new CollectionCompletedEventData(
            f.getRequired("collection_request_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            grossAmount,
            netAmount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getRequired("merchant_reference"),
            f.getRequired("provider_collection_id"),
            fees);

    String idempotencyKey = "collection-completed:" + ctx.eventId();
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
    return ctx.resolvedSlots().getOrDefault("destination", ctx.eventId());
  }
}
