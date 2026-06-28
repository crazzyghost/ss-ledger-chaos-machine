package com.softspark.chaos.flow;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.flow.chaos.ChaosOptions;
import com.softspark.chaos.flow.dto.FeeInput;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.ledgerproxy.BatchReservationLookup;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the publishing for one unattended batch disbursement: publish the reservation, resolve
 * the ledger {@code reservation_id} (poll, else autogen placeholder), then per item publish
 * {@code item.request} followed by {@code item.completed|item.failed}. The whole batch's split,
 * outcome decisions, and template are captured in a resume-safe {@link Plan} assembled by
 * {@link BatchDisbursementRunService}; the per-item ids ({@code item_id}, {@code merchant_item_ref},
 * {@code provider_reference_id}) are minted at publish time.
 *
 * <p>Pure publishing (no DB): {@link com.softspark.chaos.batch.service.BatchRunner} drives the
 * threading/pacing, persists row status, and finalizes the run. All events of a batch share one
 * {@code correlation_id} and {@code batch_correlation_id} so history groups the batch.
 */
@Component
public class BatchDisbursementRunner {

  private static final Logger log = LoggerFactory.getLogger(BatchDisbursementRunner.class);

  private static final String PUBLISHED = "PUBLISHED";
  private static final String FEE_TYPE = "PLATFORM";

  private final FlowEngine flowEngine;
  private final BatchReservationLookup reservationLookup;

  public BatchDisbursementRunner(
      FlowEngine flowEngine, BatchReservationLookup reservationLookup) {
    this.flowEngine = flowEngine;
    this.reservationLookup = reservationLookup;
  }

  /** One item's pre-split principal/fee and its decided outcome. */
  public record Item(BigDecimal principal, BigDecimal fee, boolean pass) {}

  /**
   * The fully-resolved plan for one batch disbursement (assembled by the run service, resume-safe).
   *
   * @param token the caller token used for the reservation lookup (empty for the service token)
   * @param tenantId the tenant id
   * @param correlationId the shared correlation id for every event of the batch
   * @param batchId the minted batch id
   * @param batchCorrelationId the minted batch correlation id
   * @param merchantId the merchant/organization id
   * @param sourceVaId the source ORGANIZATION VA
   * @param destinationVaId the platform-float SYSTEM VA
   * @param currency the ISO-4217 currency
   * @param totalPrincipal the total principal
   * @param totalFees the total fees
   * @param itemCount the number of items N
   * @param disbursementSubtype {@code DOMESTIC}/{@code CROSS_BORDER}
   * @param merchantBatchRef the merchant batch reference
   * @param callbackUrl optional callback URL
   * @param authorisedUserId the authorising user id
   * @param authorisedKeyFingerprint the authorising key fingerprint
   * @param creditProviderId the per-item credit provider id
   * @param creditAccountId the per-item credit account id
   * @param providerId the per-item payment provider id
   * @param sourceCountry optional per-item source country
   * @param destinationCountry optional per-item destination country
   * @param feeVaId optional SYSTEM fee VA for the item fee lines
   * @param failureReason the failure reason for failed items
   * @param failureCode the failure code for failed items
   * @param chaos optional per-item-event chaos
   * @param items the per-item split + outcome (size == itemCount)
   */
  public record Plan(
      String token,
      @Nullable String tenantId,
      String correlationId,
      String batchId,
      String batchCorrelationId,
      String merchantId,
      String sourceVaId,
      String destinationVaId,
      String currency,
      BigDecimal totalPrincipal,
      BigDecimal totalFees,
      int itemCount,
      String disbursementSubtype,
      String merchantBatchRef,
      @Nullable String callbackUrl,
      String authorisedUserId,
      String authorisedKeyFingerprint,
      String creditProviderId,
      String creditAccountId,
      String providerId,
      @Nullable String sourceCountry,
      @Nullable String destinationCountry,
      @Nullable String feeVaId,
      String failureReason,
      String failureCode,
      @Nullable ChaosOptions chaos,
      List<Item> items) {}

  /**
   * The outcome of publishing one item (request + terminal).
   *
   * @param success whether both the request and the terminal published
   * @param requestEventId the item.request event id (recorded as the row's event id)
   * @param terminalEventId the item terminal event id, or {@code null} if not reached
   * @param passed whether the item's decided outcome was completed (vs failed)
   */
  public record ItemResult(
      boolean success, String requestEventId, @Nullable String terminalEventId, boolean passed) {}

  /**
   * Publishes the batch reservation and resolves its {@code reservation_id}.
   *
   * <p>Never throws: a publish failure or a reservation that never lands falls back to an autogen
   * placeholder (the ledger keys off {@code batch_id} and ignores the inbound {@code reservation_id}).
   *
   * @param plan the batch plan
   * @return the resolved (or placeholder) {@code reservation_id}; never null
   */
  public String publishReservation(Plan plan) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("batch_id", plan.batchId());
    fields.put("batch_correlation_id", plan.batchCorrelationId());
    fields.put("merchant_id", plan.merchantId());
    fields.put("total_principal_amount", plan.totalPrincipal());
    fields.put("total_fees", plan.totalFees());
    fields.put("item_count", plan.itemCount());
    fields.put("disbursement_subtype", plan.disbursementSubtype());
    fields.put("merchant_batch_ref", plan.merchantBatchRef());
    if (plan.callbackUrl() != null) {
      fields.put("callback_url", plan.callbackUrl());
    }
    fields.put("authorised_user_id", plan.authorisedUserId());
    fields.put("authorised_key_fingerprint", plan.authorisedKeyFingerprint());

    FlowRequest request =
        FlowRequestBuilder.builder()
            .flowType(FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST)
            .correlationId(plan.correlationId())
            .tenantId(plan.tenantId())
            .currency(plan.currency())
            .slotOverrides(slots(plan))
            .chaos(null)
            .flowFields(fields)
            .build();

    FlowResult result =
        flowEngine.execute(request, label(plan, 0, plan.itemCount(), "reservation"));
    if (!PUBLISHED.equals(result.status())) {
      log.warn("Batch {} reservation publish failed: {}", plan.batchId(), result.error());
    }

    Optional<String> resolved = reservationLookup.find(plan.token(), plan.batchId());
    if (resolved.isPresent()) {
      return resolved.get();
    }
    String placeholder = Ids.generateUUID();
    log.info(
        "Batch {}: reservation_id not resolved before timeout, using placeholder {}",
        plan.batchId(),
        placeholder);
    return placeholder;
  }

  /**
   * Publishes one item: {@code item.request} then {@code item.completed|item.failed}.
   *
   * @param plan the batch plan
   * @param index the zero-based item index
   * @param reservationId the resolved reservation id (carried into the terminal event)
   * @return the item result (success iff both publishes succeeded)
   */
  public ItemResult runItem(Plan plan, int index, String reservationId) {
    Item item = plan.items().get(index);
    int sequence = index + 1;
    String itemId = Ids.generateUUID();
    String merchantItemRef = Ids.generateULID();
    String corridor = corridor(plan.sourceCountry(), plan.destinationCountry());

    // 1. item.request (inert at the ledger)
    Map<String, Object> requestFields = new LinkedHashMap<>();
    requestFields.put("batch_id", plan.batchId());
    requestFields.put("batch_correlation_id", plan.batchCorrelationId());
    requestFields.put("item_id", itemId);
    requestFields.put("item_sequence", sequence);
    requestFields.put("merchant_item_ref", merchantItemRef);
    requestFields.put("merchant_id", plan.merchantId());
    requestFields.put("virtual_account_id", plan.sourceVaId());
    requestFields.put("principal_amount", item.principal());
    requestFields.put("item_fee", item.fee());
    requestFields.put("credit_provider_id", plan.creditProviderId());
    requestFields.put("credit_account_id", plan.creditAccountId());
    requestFields.put("disbursement_subtype", plan.disbursementSubtype());
    putIfPresent(requestFields, "source_country", plan.sourceCountry());
    putIfPresent(requestFields, "destination_country", plan.destinationCountry());
    putIfPresent(requestFields, "corridor", corridor);

    FlowResult requestResult =
        flowEngine.execute(
            build(FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST, plan, requestFields, List.of()),
            label(plan, sequence, plan.itemCount(), "request"));
    if (!PUBLISHED.equals(requestResult.status())) {
      return new ItemResult(false, requestResult.eventId(), null, item.pass());
    }

    // 2. terminal: item.completed (capture) or item.failed (release)
    List<FeeInput> fees =
        item.fee() != null && item.fee().signum() > 0
            ? List.of(new FeeInput(FEE_TYPE, item.fee(), null, plan.feeVaId()))
            : List.of();

    Map<String, Object> terminalFields = new LinkedHashMap<>();
    terminalFields.put("batch_id", plan.batchId());
    terminalFields.put("item_id", itemId);
    terminalFields.put("item_sequence", sequence);
    terminalFields.put("virtual_account_id", plan.sourceVaId());
    terminalFields.put("reservation_id", reservationId);
    terminalFields.put("disbursement_subtype", plan.disbursementSubtype());
    terminalFields.put("provider_id", plan.providerId());
    terminalFields.put("provider_reference_id", Ids.generateULID());
    terminalFields.put("principal_amount", item.principal());
    terminalFields.put("merchant_item_ref", merchantItemRef);
    putIfPresent(terminalFields, "destination_country", plan.destinationCountry());
    putIfPresent(terminalFields, "corridor", corridor);

    FlowType terminalType;
    String phase;
    if (item.pass()) {
      terminalType = FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED;
      phase = "completed";
    } else {
      terminalType = FlowType.DISBURSEMENT_BATCH_ITEM_FAILED;
      phase = "failed";
      terminalFields.put("failure_reason", plan.failureReason());
      terminalFields.put("failure_code", plan.failureCode());
    }

    FlowResult terminalResult =
        flowEngine.execute(
            build(terminalType, plan, terminalFields, fees),
            label(plan, sequence, plan.itemCount(), phase));

    boolean success = PUBLISHED.equals(terminalResult.status());
    if (!success) {
      log.warn(
          "Batch {} item {}/{} {} publish failed: {}",
          plan.batchId(),
          sequence,
          plan.itemCount(),
          phase,
          terminalResult.error());
    }
    return new ItemResult(success, requestResult.eventId(), terminalResult.eventId(), item.pass());
  }

  private FlowRequest build(
      FlowType type, Plan plan, Map<String, Object> fields, List<FeeInput> fees) {
    return FlowRequestBuilder.builder()
        .flowType(type)
        .correlationId(plan.correlationId())
        .tenantId(plan.tenantId())
        .currency(plan.currency())
        .slotOverrides(slots(plan))
        .chaos(plan.chaos())
        .flowFields(fields)
        .fees(fees)
        .build();
  }

  private static Map<String, String> slots(Plan plan) {
    return Map.of("source", plan.sourceVaId(), "destination", plan.destinationVaId());
  }

  private static void putIfPresent(Map<String, Object> fields, String key, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      fields.put(key, value);
    }
  }

  @Nullable
  private static String corridor(@Nullable String source, @Nullable String destination) {
    if (source == null || source.isBlank() || destination == null || destination.isBlank()) {
      return null;
    }
    return source + "-" + destination;
  }

  private static String label(Plan plan, int sequence, int total, String phase) {
    String shortId =
        plan.batchId().length() > 8 ? plan.batchId().substring(0, 8) : plan.batchId();
    return "BATCH_DISB:" + shortId + ":" + sequence + "/" + total + ":" + phase;
  }
}
