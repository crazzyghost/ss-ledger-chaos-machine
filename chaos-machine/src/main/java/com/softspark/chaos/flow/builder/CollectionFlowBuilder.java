package com.softspark.chaos.flow.builder;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.CollectionCompletedEventData;
import com.softspark.chaos.flow.model.v1.TransactionFeeLine;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#COLLECTION_COMPLETED} events.
 *
 * <p>Emits the authoritative ledger {@code collection.completed} contract:
 * {@code transaction_id} (the ledger idempotency key / transaction reference), the
 * {@code source}/{@code destination} slot VAs, provider ids, a {@code fees[]} list, and a computed
 * {@code gross_amount = net_amount + Σ fee.amount}.
 *
 * <p>Slots: {@code source} (system PLATFORM_FLOAT — debited, wire {@code system_va_id}),
 * {@code destination} (organization VA — credited, wire {@code organization_va_id}). Fee VAs travel
 * per-row inside {@code fees[]}; when no fees are supplied the legacy gross−net single-fee fallback
 * credits the {@code fee} slot for CSV/batch compatibility.
 */
@Component
public class CollectionFlowBuilder implements FlowBuilder<CollectionCompletedEventData> {

  /** The ledger {@code TransactionFeeType} applied to collection fees by default. */
  private static final String DEFAULT_FEE_TYPE = "PLATFORM";

  private final TopicCatalog topicCatalog;

  public CollectionFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.COLLECTION_COMPLETED;
  }

  @Override
  public java.util.Optional<String> transactionRequestIdField() {
    return java.util.Optional.of("transaction_id");
  }

  @Override
  public String source() {
    return "payment-service";
  }

  @Override
  public EventEnvelope<CollectionCompletedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    BigDecimal net = resolveNet(request, f);
    List<TransactionFeeLine> fees = FeeLines.from(request.fees(), DEFAULT_FEE_TYPE);

    BigDecimal gross;
    if (!fees.isEmpty()) {
      gross = net.add(FeeLines.sum(fees));
    } else {
      // Legacy gross−net single-fee fallback (CSV/batch): derive one fee from gross − net.
      gross =
          request.grossAmount() != null
              ? request.grossAmount()
              : f.getBigDecimal("gross_amount") != null ? f.getBigDecimal("gross_amount") : net;
      BigDecimal feeAmount = gross.subtract(net);
      if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
        String feeVaId = ctx.resolvedSlots().getOrDefault("fee", "");
        fees =
            List.of(
                new TransactionFeeLine(DEFAULT_FEE_TYPE, feeAmount, Ids.generateULID(), feeVaId));
      }
    }

    var data =
        new CollectionCompletedEventData(
            f.getRequired("transaction_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            f.getRequired("provider_id"),
            f.getRequired("provider_reference_id"),
            gross,
            net,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            fees,
            f.getOptional("commission_split_id"),
            f.getTimestampOrNow("completed_at"),
            f.getRequired("merchant_ref_id"));

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

  /**
   * Resolves the net (merchant-credited) amount. The collection form labels its {@code amount} field
   * "Net Amount", so the top-level request {@code amount} is the net; {@code netAmount} and a
   * {@code net_amount} flow field are honoured for CSV/batch compatibility.
   */
  private static BigDecimal resolveNet(FlowRequest request, FlowFields f) {
    if (request.netAmount() != null) {
      return request.netAmount();
    }
    if (request.amount() != null) {
      return request.amount();
    }
    BigDecimal fromFields = f.getBigDecimal("net_amount");
    return fromFields != null ? fromFields : BigDecimal.ZERO;
  }
}
