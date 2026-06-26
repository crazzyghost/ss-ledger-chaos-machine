package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementFailedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_FAILED} events.
 *
 * <p>The failure phase of a disbursement lifecycle. No slot resolution — the merchant's
 * {@code virtual_account_id} (whose reservation is released) comes directly from a {@code flowFields}
 * VA picker. Carries the same {@code transaction_id} as the initiated phase; the inbound
 * {@code reservation_id} is required on the wire but ledger-ignored.
 */
@Component
public class DisbursementFailedFlowBuilder implements FlowBuilder<DisbursementFailedEventData> {

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";
  private static final String DEFAULT_FAILURE_REASON = "Disbursement failed";

  private final TopicCatalog topicCatalog;

  public DisbursementFailedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_FAILED;
  }

  @Override
  public String source() {
    return "payment-service";
  }

  @Override
  public EventEnvelope<DisbursementFailedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    var data =
        new DisbursementFailedEventData(
            f.getRequired("transaction_id"),
            f.getRequired("virtual_account_id"),
            f.getRequired("reservation_id"),
            defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            f.getRequired("provider_id"),
            f.getOptional("provider_reference_id"),
            resolvePrincipal(request, f),
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            defaulted(f.getOptional("failure_reason"), DEFAULT_FAILURE_REASON),
            f.getOptional("failure_code"),
            f.getTimestampOrNow("failed_at"),
            f.getRequired("merchant_ref_id"));

    String idempotencyKey = "disbursement-failed:" + ctx.eventId();
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
    return fields.getOrDefault("transaction_id", ctx.eventId()).toString();
  }

  /** Resolves the principal amount: top-level {@code amount} first, then a {@code principal_amount} field. */
  private static BigDecimal resolvePrincipal(FlowRequest request, FlowFields f) {
    if (request.amount() != null) {
      return request.amount();
    }
    BigDecimal fromFields = f.getBigDecimal("principal_amount");
    return fromFields != null ? fromFields : BigDecimal.ZERO;
  }

  private static String defaulted(String value, String fallback) {
    return value != null && !value.isBlank() ? value : fallback;
  }
}
