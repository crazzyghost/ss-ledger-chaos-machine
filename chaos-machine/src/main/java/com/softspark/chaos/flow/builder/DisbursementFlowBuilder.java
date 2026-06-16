package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementCompletedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_COMPLETED} events.
 *
 * <p>Slots: {@code source} (merchant client VA — debited), {@code destination} (system
 * PLATFORM_FLOAT — credited), {@code fee} (system PROVIDER_FEE — receives the provider fee).
 */
@Component
public class DisbursementFlowBuilder implements FlowBuilder<DisbursementCompletedEventData> {

  private final TopicCatalog topicCatalog;

  public DisbursementFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_COMPLETED;
  }

  @Override
  public String source() {
    return "disbursement-service";
  }

  @Override
  public EventEnvelope<DisbursementCompletedEventData> build(FlowRequest request, FlowContext ctx) {
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
    String feeType = f.getOptional("fee_type") != null ? f.getOptional("fee_type") : "PROVIDER_FEE";

    List<DisbursementCompletedEventData.FeeEntry> fees =
        feeAmount.compareTo(BigDecimal.ZERO) > 0
            ? List.of(new DisbursementCompletedEventData.FeeEntry(feeType, feeAmount, feeVaId))
            : List.of();

    var data =
        new DisbursementCompletedEventData(
            f.getRequired("disbursement_request_id"),
            f.getRequired("organization_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            grossAmount,
            netAmount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getRequired("recipient_account_number"),
            f.getRequired("recipient_bank"),
            f.getRequired("merchant_reference"),
            f.getRequired("provider_disbursement_id"),
            fees,
            f.getRequired("approved_by"),
            f.getTimestampOrNow("completed_at"));

    String idempotencyKey = "disbursement-completed:" + ctx.eventId();
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
