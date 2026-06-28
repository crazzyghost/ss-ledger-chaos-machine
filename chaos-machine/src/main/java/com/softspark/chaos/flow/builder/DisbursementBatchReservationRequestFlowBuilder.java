package com.softspark.chaos.flow.builder;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementBatchReservationRequestEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_BATCH_RESERVATION_REQUEST}
 * events ({@code operation = BATCH_RESERVATION_REQUEST}).
 *
 * <p>The first phase of a batch-disbursement fan-out: the ledger mints one BATCH reservation for
 * {@code total_amount} against the source ORGANIZATION VA. {@code total_amount} is always computed as
 * {@code total_principal_amount + total_fees} (the exact invariant the ledger enforces). The
 * structured idempotency key is {@code disbursement-batch-initiated:{batch_id}}; the partition key is
 * the source VA so the whole batch lands on one partition (the reservation must be consumed before
 * any item terminal).
 *
 * <p>Slots: {@code source} (organization VA — reservation held), {@code destination}
 * (platform-float SYSTEM VA — receives item principal credits).
 */
@Component
public class DisbursementBatchReservationRequestFlowBuilder
    implements FlowBuilder<DisbursementBatchReservationRequestEventData> {

  /** The operation discriminator for the reservation request on the shared initiated topic. */
  static final String OPERATION = "BATCH_RESERVATION_REQUEST";

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";
  private static final String DEFAULT_AUTH_USER = "chaos-operator";
  private static final String DEFAULT_AUTH_FINGERPRINT = "ab:cd:ef:00";
  private static final BigDecimal DEFAULT_PRINCIPAL = new BigDecimal("1000.0000");
  private static final BigDecimal DEFAULT_FEES = new BigDecimal("10");

  private final TopicCatalog topicCatalog;

  public DisbursementBatchReservationRequestFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST;
  }

  @Override
  public java.util.Optional<String> transactionRequestIdField() {
    return java.util.Optional.of("batch_id");
  }

  @Override
  public String source() {
    return "payment-service";
  }

  @Override
  public EventEnvelope<DisbursementBatchReservationRequestEventData> build(
      FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    BigDecimal totalPrincipal = resolvePrincipal(request, f);
    BigDecimal totalFees = f.getBigDecimal("total_fees") != null ? f.getBigDecimal("total_fees") : DEFAULT_FEES;
    BigDecimal totalAmount = totalPrincipal.add(totalFees);

    Map<String, Object> authorisedPrincipal = new LinkedHashMap<>();
    authorisedPrincipal.put(
        "user_id", defaulted(f.getOptional("authorised_user_id"), DEFAULT_AUTH_USER));
    authorisedPrincipal.put(
        "key_fingerprint",
        defaulted(f.getOptional("authorised_key_fingerprint"), DEFAULT_AUTH_FINGERPRINT));

    String batchId = f.getRequired("batch_id");
    var data =
        new DisbursementBatchReservationRequestEventData(
            OPERATION,
            batchId,
            f.getRequired("batch_correlation_id"),
            f.getRequired("merchant_id"),
            ctx.resolvedSlots().getOrDefault("source", ""),
            ctx.resolvedSlots().getOrDefault("destination", ""),
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            totalPrincipal,
            totalFees,
            totalAmount,
            parseItemCount(f.getRequired("item_count")),
            defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            f.getOptional("callback_url"),
            f.getRequired("merchant_batch_ref"),
            ctx.correlationId(),
            f.getTimestampOrNow("requested_at"),
            authorisedPrincipal);

    String idempotencyKey = "disbursement-batch-initiated:" + batchId;
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
    return ctx.request().flowFields().getOrDefault("batch_id", ctx.eventId()).toString();
  }

  /** Resolves the total principal: top-level {@code amount} first, then {@code total_principal_amount}. */
  private static BigDecimal resolvePrincipal(FlowRequest request, FlowFields f) {
    if (request.amount() != null) {
      return request.amount();
    }
    BigDecimal fromFields = f.getBigDecimal("total_principal_amount");
    return fromFields != null ? fromFields : DEFAULT_PRINCIPAL;
  }

  private static int parseItemCount(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid item_count: " + value);
    }
  }

  private static String defaulted(String value, String fallback) {
    return value != null && !value.isBlank() ? value : fallback;
  }
}
