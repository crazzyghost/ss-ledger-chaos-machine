package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementBatchItemCompletedEventData;
import com.softspark.chaos.flow.model.v1.TransactionFeeLine;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_BATCH_ITEM_COMPLETED} events.
 *
 * <p>The success phase of one batch item: the ledger captures the item's gross ({@code
 * principal_amount} + Σ{@code fees}) from the shared BATCH reservation and posts the per-item journal.
 * The {@code virtual_account_id} (the batch source ORGANIZATION VA) is carried over via
 * {@code flowFields}, falling back to the resolved {@code source} slot. Fee VAs travel per-row inside
 * {@code fees[]}. The structured idempotency key is
 * {@code disbursement-batch-item-completed:{batch_id}:{item_id}}.
 */
@Component
public class DisbursementBatchItemCompletedFlowBuilder
    implements FlowBuilder<DisbursementBatchItemCompletedEventData> {

  /** The ledger {@code TransactionFeeType} applied to batch-item fees by default. */
  private static final String DEFAULT_FEE_TYPE = "PLATFORM";

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";

  private final TopicCatalog topicCatalog;

  public DisbursementBatchItemCompletedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED;
  }

  @Override
  public java.util.Optional<String> transactionRequestIdField() {
    return java.util.Optional.of("item_id");
  }

  @Override
  public String source() {
    return "payment-service";
  }

  @Override
  public EventEnvelope<DisbursementBatchItemCompletedEventData> build(
      FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    List<TransactionFeeLine> fees = FeeLines.from(request.fees(), DEFAULT_FEE_TYPE);

    String batchId = f.getRequired("batch_id");
    String itemId = f.getRequired("item_id");
    var data =
        new DisbursementBatchItemCompletedEventData(
            batchId,
            itemId,
            BatchFields.itemSequence(f),
            BatchVa.resolve(f, ctx),
            f.getRequired("reservation_id"),
            BatchFields.defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            f.getRequired("provider_id"),
            f.getRequired("provider_reference_id"),
            BatchFields.amount(f, "principal_amount"),
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            fees,
            f.getOptional("recipient_reference"),
            f.getOptional("destination_country"),
            f.getOptional("corridor"),
            f.getBigDecimal("applied_fx_rate"),
            f.getTimestampOrNow("completed_at"),
            f.getRequired("merchant_item_ref"));

    String idempotencyKey = "disbursement-batch-item-completed:" + batchId + ":" + itemId;
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
    return BatchVa.partitionKey(ctx);
  }
}
