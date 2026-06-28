package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementBatchItemFailedEventData;
import com.softspark.chaos.flow.model.v1.TransactionFeeLine;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_BATCH_ITEM_FAILED} events.
 *
 * <p>The failure phase of one batch item: the ledger partially releases the item's gross back to
 * {@code AVAILABLE} (no journal) and increments {@code failed_count}. Same carry-over and VA
 * resolution as the completed builder, plus {@code failure_reason}/{@code failure_code}. The
 * structured idempotency key is {@code disbursement-batch-item-failed:{batch_id}:{item_id}}.
 */
@Component
public class DisbursementBatchItemFailedFlowBuilder
    implements FlowBuilder<DisbursementBatchItemFailedEventData> {

  /** The ledger {@code TransactionFeeType} applied to batch-item fees by default. */
  private static final String DEFAULT_FEE_TYPE = "PLATFORM";

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";
  private static final String DEFAULT_FAILURE_REASON = "Batch item disbursement failed";
  private static final String DEFAULT_FAILURE_CODE = "RECIPIENT_INVALID";

  private final TopicCatalog topicCatalog;

  public DisbursementBatchItemFailedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_BATCH_ITEM_FAILED;
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
  public EventEnvelope<DisbursementBatchItemFailedEventData> build(
      FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());
    List<TransactionFeeLine> fees = FeeLines.from(request.fees(), DEFAULT_FEE_TYPE);

    String batchId = f.getRequired("batch_id");
    String itemId = f.getRequired("item_id");
    var data =
        new DisbursementBatchItemFailedEventData(
            batchId,
            itemId,
            BatchFields.itemSequence(f),
            BatchVa.resolve(f, ctx),
            f.getRequired("reservation_id"),
            BatchFields.defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            f.getRequired("provider_id"),
            f.getOptional("provider_reference_id"),
            BatchFields.amount(f, "principal_amount"),
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            fees,
            BatchFields.defaulted(f.getOptional("failure_reason"), DEFAULT_FAILURE_REASON),
            BatchFields.defaulted(f.getOptional("failure_code"), DEFAULT_FAILURE_CODE),
            f.getTimestampOrNow("failed_at"),
            f.getRequired("merchant_item_ref"));

    String idempotencyKey = "disbursement-batch-item-failed:" + batchId + ":" + itemId;
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
