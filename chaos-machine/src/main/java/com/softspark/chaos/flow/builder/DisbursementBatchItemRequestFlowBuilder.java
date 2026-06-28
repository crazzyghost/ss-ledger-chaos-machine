package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementBatchItemRequestEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_BATCH_ITEM_REQUEST} events
 * ({@code operation = BATCH_ITEM_REQUEST}).
 *
 * <p>The per-item request phase, inert at the ledger (recorded for idempotency, no side-effects). All
 * batch identity ({@code batch_id}/{@code batch_correlation_id}), the source VA, currency, and
 * subtype are carried over from the reservation via {@code flowFields}. The structured idempotency key
 * is {@code disbursement-batch-initiated:{batch_id}:{item_id}}; the partition key is the batch source
 * VA so every event of the batch stays ordered on one partition.
 */
@Component
public class DisbursementBatchItemRequestFlowBuilder
    implements FlowBuilder<DisbursementBatchItemRequestEventData> {

  /** The operation discriminator for the item request on the shared initiated topic. */
  static final String OPERATION = "BATCH_ITEM_REQUEST";

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";

  private final TopicCatalog topicCatalog;

  public DisbursementBatchItemRequestFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST;
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
  public EventEnvelope<DisbursementBatchItemRequestEventData> build(
      FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    String sourceCountry = f.getOptional("source_country");
    String destinationCountry = f.getOptional("destination_country");
    String corridor = f.getOptional("corridor");
    if ((corridor == null || corridor.isBlank())
        && sourceCountry != null
        && !sourceCountry.isBlank()
        && destinationCountry != null
        && !destinationCountry.isBlank()) {
      corridor = sourceCountry + "-" + destinationCountry;
    }

    String batchId = f.getRequired("batch_id");
    String itemId = f.getRequired("item_id");
    var data =
        new DisbursementBatchItemRequestEventData(
            OPERATION,
            batchId,
            f.getRequired("batch_correlation_id"),
            itemId,
            BatchFields.itemSequence(f),
            f.getRequired("merchant_item_ref"),
            f.getRequired("merchant_id"),
            f.getRequired("virtual_account_id"),
            BatchFields.amount(f, "principal_amount"),
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            f.getRequired("credit_provider_id"),
            f.getRequired("credit_account_id"),
            BatchFields.defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            sourceCountry,
            destinationCountry,
            corridor,
            f.getOptional("fx_quote_reference"),
            BatchFields.amount(f, "item_fee"),
            ctx.correlationId(),
            f.getTimestampOrNow("requested_at"));

    String idempotencyKey = "disbursement-batch-initiated:" + batchId + ":" + itemId;
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
    var fields = ctx.request().flowFields();
    Object vaId = fields.get("virtual_account_id");
    if (vaId != null && !vaId.toString().isBlank()) {
      return vaId.toString();
    }
    return fields.getOrDefault("batch_id", ctx.eventId()).toString();
  }
}
