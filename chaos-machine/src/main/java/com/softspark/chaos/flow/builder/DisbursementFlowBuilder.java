package com.softspark.chaos.flow.builder;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementCompletedEventData;
import com.softspark.chaos.flow.model.v1.TransactionFeeLine;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_COMPLETED} events.
 *
 * <p>The success phase of a disbursement lifecycle. Emits the authoritative ledger contract:
 * the carried {@code transaction_id}, the {@code source}/{@code destination} slot VAs, the
 * (ledger-ignored) {@code reservation_id}, {@code principal_amount}, a {@code fees[]} list, and the
 * cross-border fields (left null for {@code DOMESTIC}). {@code provider_reference_id} is the
 * transaction reference.
 *
 * <p>Slots: {@code source} (organization VA — debited), {@code destination} (system
 * SETTLEMENT_ACCOUNT — credited). Fee VAs travel per-row inside {@code fees[]}.
 */
@Component
public class DisbursementFlowBuilder implements FlowBuilder<DisbursementCompletedEventData> {

  /** The ledger {@code TransactionFeeType} applied to disbursement fees by default. */
  private static final String DEFAULT_FEE_TYPE = "PLATFORM";

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";

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
    return "payment-service";
  }

  @Override
  public EventEnvelope<DisbursementCompletedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    BigDecimal principal = resolvePrincipal(request, f);
    List<TransactionFeeLine> fees = FeeLines.from(request.fees(), DEFAULT_FEE_TYPE);
    if (fees.isEmpty()) {
      // Legacy gross−net single-fee fallback (CSV/batch): derive one fee from gross − net.
      BigDecimal gross = request.grossAmount();
      BigDecimal net = request.netAmount();
      if (gross != null && net != null && gross.subtract(net).compareTo(BigDecimal.ZERO) > 0) {
        String feeVaId = ctx.resolvedSlots().getOrDefault("fee", "");
        fees =
            List.of(
                new TransactionFeeLine(
                    DEFAULT_FEE_TYPE, gross.subtract(net), Ids.generateULID(), feeVaId));
      }
    }

    String subtype = f.getOptional("disbursement_subtype");
    var data =
        new DisbursementCompletedEventData(
            f.getRequired("transaction_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            f.getRequired("reservation_id"),
            subtype != null && !subtype.isBlank() ? subtype : DEFAULT_SUBTYPE,
            f.getRequired("provider_id"),
            f.getRequired("provider_reference_id"),
            principal,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            fees,
            f.getOptional("recipient_reference"),
            f.getOptional("destination_country"),
            f.getOptional("corridor"),
            f.getBigDecimal("applied_fx_rate"),
            f.getTimestampOrNow("completed_at"),
            f.getRequired("merchant_ref_id"));

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
    String sourceVa = ctx.resolvedSlots().get("source");
    if (sourceVa != null && !sourceVa.isBlank()) {
      return sourceVa;
    }
    return ctx.request().flowFields().getOrDefault("transaction_id", ctx.eventId()).toString();
  }

  /** Resolves the principal amount: top-level {@code amount} first, then the carried flow field. */
  private static BigDecimal resolvePrincipal(FlowRequest request, FlowFields f) {
    if (request.amount() != null) {
      return request.amount();
    }
    BigDecimal fromFields = f.getBigDecimal("principal_amount");
    return fromFields != null ? fromFields : BigDecimal.ZERO;
  }
}
