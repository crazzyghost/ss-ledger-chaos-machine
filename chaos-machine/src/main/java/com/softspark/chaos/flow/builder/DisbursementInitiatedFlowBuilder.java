package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.DisbursementInitiatedEventData;
import com.softspark.chaos.flow.model.v1.DisbursementInitiatedEventData.AuthorisedPrincipal;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#DISBURSEMENT_INITIATED} events.
 *
 * <p>The first phase of a disbursement lifecycle. No slot resolution — the merchant's
 * {@code virtual_account_id} (the reservation account) comes directly from a {@code flowFields} VA
 * picker. {@code correlation_id} is the ledger transaction reference; {@code authorised_principal} is
 * assembled from the {@code authorised_user_id}/{@code authorised_key_fingerprint} fields; the
 * {@code corridor} is derived from the source/destination countries when left blank.
 */
@Component
public class DisbursementInitiatedFlowBuilder
    implements FlowBuilder<DisbursementInitiatedEventData> {

  private static final String DEFAULT_SUBTYPE = "DOMESTIC";
  private static final String DEFAULT_COUNTRY = "GH";
  private static final String DEFAULT_AUTH_USER = "chaos-operator";
  private static final String DEFAULT_AUTH_FINGERPRINT = "ab:cd:ef:00";
  private static final BigDecimal DEFAULT_FEE_AMOUNT = new BigDecimal("10");

  private final TopicCatalog topicCatalog;

  public DisbursementInitiatedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.DISBURSEMENT_INITIATED;
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
  public EventEnvelope<DisbursementInitiatedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    String sourceCountry = defaulted(f.getOptional("source_country"), DEFAULT_COUNTRY);
    String destinationCountry = defaulted(f.getOptional("destination_country"), DEFAULT_COUNTRY);
    String corridor = f.getOptional("corridor");
    if (corridor == null || corridor.isBlank()) {
      corridor = sourceCountry + "-" + destinationCountry;
    }

    var authorisedPrincipal =
        new AuthorisedPrincipal(
            defaulted(f.getOptional("authorised_user_id"), DEFAULT_AUTH_USER),
            defaulted(f.getOptional("authorised_key_fingerprint"), DEFAULT_AUTH_FINGERPRINT));

    BigDecimal feeAmount =
        f.getBigDecimal("fee_amount") != null ? f.getBigDecimal("fee_amount") : DEFAULT_FEE_AMOUNT;

    var data =
        new DisbursementInitiatedEventData(
            f.getRequired("transaction_id"),
            f.getRequired("merchant_id"),
            f.getRequired("virtual_account_id"),
            f.getRequired("merchant_ref_id"),
            f.getOptional("narration"),
            resolvePrincipal(request, f),
            feeAmount,
            request.currency() != null ? request.currency() : f.getRequired("currency"),
            defaulted(f.getOptional("disbursement_subtype"), DEFAULT_SUBTYPE),
            f.getRequired("credit_provider_id"),
            f.getRequired("credit_account_id"),
            sourceCountry,
            destinationCountry,
            corridor,
            f.getOptional("fx_quote_reference"),
            ctx.correlationId(),
            f.getTimestampOrNow("requested_at"),
            authorisedPrincipal);

    String idempotencyKey = "disbursement-initiated:" + ctx.eventId();
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
