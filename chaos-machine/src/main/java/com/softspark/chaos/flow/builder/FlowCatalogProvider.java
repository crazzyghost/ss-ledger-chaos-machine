package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.dto.AccountKind;
import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.BatchDisbursementGroup;
import com.softspark.chaos.flow.dto.CarryOver;
import com.softspark.chaos.flow.dto.FieldKind;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.dto.FlowFieldDescriptorBuilder;
import com.softspark.chaos.flow.dto.FlowLifecycle;
import com.softspark.chaos.flow.dto.InferenceRule;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Provides the static flow catalog metadata for all supported flow types.
 *
 * <p>Used by the catalog REST endpoint to expose, per flow: structured field descriptors (driving
 * the Single Flow Run form), legacy required/optional field hints, the CSV column schema, and — for
 * multi-step transaction types — a {@link FlowLifecycle} grouping with the initiated→secondary
 * carry-over.
 *
 * <p>Eight transaction types are {@code runnerVisible} (carry rich descriptors and appear in the
 * radio): the five Phase 011 flows plus {@code COLLECTION_COMPLETED}, {@code SETTLEMENT_INITIATED},
 * and {@code DISBURSEMENT_INITIATED}. The lifecycle {@code *_COMPLETED}/{@code *_FAILED} phases are
 * hidden ({@code runnerVisible = false}) but keep full descriptors so the wizard and the RANDOM
 * runner can render/build them; the remaining flows carry minimal text descriptors.
 */
@Component
public class FlowCatalogProvider {

  /** Default seed for non-inferable "actor" fields (mirrors the bin-script samples). */
  private static final String DEFAULT_ACTOR = "ops@acme.example";

  private static final String DEFAULT_NARRATIVE = "Chaos run";
  private static final String DEFAULT_AMOUNT = "1000.0000";
  private static final String DEFAULT_PROVIDER = "PROVIDER_GH";
  private static final String DEFAULT_CREDIT_ACCOUNT = "0240000000";
  private static final String DEFAULT_COUNTRY = "GH";
  private static final List<String> CHANNEL_OPTIONS = List.of("bank", "momo");
  private static final List<String> SUBTYPE_OPTIONS = List.of("DOMESTIC", "CROSS_BORDER");
  private static final List<String> BANK_OPTIONS =
      List.of("ABSA", "GCB Bank", "Stanbic", "Ecobank");
  private static final List<String> DISBURSEMENT_FAILURE_CODES =
      List.of(
          "PROVIDER_REJECTED",
          "PROVIDER_TIMEOUT",
          "RECIPIENT_INVALID",
          "VALIDATION_FAILED",
          "PROVIDER_UNAVAILABLE",
          "RESERVATION_MISSING",
          "SUBTYPE_UNSUPPORTED");
  private static final List<String> SETTLEMENT_FAILURE_CODES =
      List.of("BANK_REJECTED", "ACCOUNT_INVALID", "TIMEOUT", "INSUFFICIENT_FUNDS", "UNSPECIFIED");
  private static final List<String> BATCH_FAILURE_CODES =
      List.of(
          "PROVIDER_REJECTED",
          "PROVIDER_TIMEOUT",
          "RECIPIENT_INVALID",
          "VALIDATION_FAILED",
          "PROVIDER_UNAVAILABLE",
          "RESERVATION_MISSING",
          "SUBTYPE_UNSUPPORTED");
  private static final String DEFAULT_BATCH_FEES = "10";
  private static final String DEFAULT_ITEM_COUNT = "4";

  private final TopicCatalog topicCatalog;

  public FlowCatalogProvider(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  /**
   * Returns the catalog entries for all supported flow types.
   *
   * @return list of catalog entries
   */
  public List<FlowCatalogEntry> catalog() {
    return List.of(
        // ---- Runner-visible transaction types (rich descriptors) ----------------------------
        runnerEntry(
            FlowType.TOPUP_CONFIRMED,
            "topup-service",
            topUpFields(),
            List.of("topup_request_id", "organization_id", "approved_by"),
            List.of("currency", "amount", "source_payment_reference", "approved_at"),
            List.of("topup_request_id", "organization_id", "amount", "currency", "approved_by"),
            "organization_id",
            null),
        runnerEntry(
            FlowType.TRANSFER_REQUESTED,
            "transfer-service",
            transferFields(),
            List.of(
                "transfer_request_id",
                "source_organization_id",
                "destination_organization_id",
                "narrative",
                "initiated_by"),
            List.of("currency", "amount", "initiated_at"),
            List.of(
                "transfer_request_id",
                "source_organization_id",
                "destination_organization_id",
                "amount",
                "currency",
                "narrative",
                "initiated_by"),
            "source_organization_id",
            null),
        runnerEntry(
            FlowType.TREASURY_PREFUND_COMPLETED,
            "treasury-service",
            treasuryFields("prefund_request_id", "bank", "momo"),
            List.of("prefund_request_id", "source_channel", "destination_channel", "completed_by"),
            List.of("currency", "amount", "completion_reference", "completed_at"),
            List.of(
                "prefund_request_id",
                "source_channel",
                "destination_channel",
                "amount",
                "currency",
                "completed_by"),
            "prefund_request_id",
            null),
        runnerEntry(
            FlowType.TREASURY_SWEEP_COMPLETED,
            "treasury-service",
            treasuryFields("sweep_request_id", "momo", "bank"),
            List.of("sweep_request_id", "source_channel", "destination_channel", "completed_by"),
            List.of("currency", "amount", "completion_reference", "completed_at"),
            List.of(
                "sweep_request_id",
                "source_channel",
                "destination_channel",
                "amount",
                "currency",
                "completed_by"),
            "sweep_request_id",
            null),
        runnerEntry(
            FlowType.TREASURY_TRANSFER_COMPLETED,
            "treasury-service",
            treasuryFields("transfer_request_id", "momo", "momo"),
            List.of("transfer_request_id", "source_channel", "destination_channel", "completed_by"),
            List.of("currency", "amount", "completion_reference", "completed_at"),
            List.of(
                "transfer_request_id",
                "source_channel",
                "destination_channel",
                "amount",
                "currency",
                "completed_by"),
            "transfer_request_id",
            null),
        runnerEntry(
            FlowType.COLLECTION_COMPLETED,
            "payment-service",
            collectionFields(),
            List.of("transaction_id", "provider_id", "provider_reference_id", "merchant_ref_id"),
            List.of("currency", "net_amount", "commission_split_id", "completed_at"),
            List.of(
                "transaction_id",
                "net_amount",
                "currency",
                "provider_id",
                "provider_reference_id",
                "merchant_ref_id"),
            "destination",
            null),
        runnerEntry(
            FlowType.SETTLEMENT_INITIATED,
            "settlements-service",
            settlementInitiatedFields(),
            List.of("settlement_request_id", "virtual_account_id", "organization_id"),
            List.of(
                "currency",
                "amount",
                "destination_bank_account",
                "destination_bank",
                "approved_by",
                "approved_at"),
            List.of(
                "settlement_request_id",
                "virtual_account_id",
                "organization_id",
                "amount",
                "currency",
                "destination_bank_account",
                "destination_bank",
                "approved_by"),
            "organization_id",
            settlementLifecycle()),
        runnerEntry(
            FlowType.DISBURSEMENT_INITIATED,
            "payment-service",
            disbursementInitiatedFields(),
            List.of(
                "transaction_id",
                "virtual_account_id",
                "merchant_id",
                "merchant_ref_id",
                "credit_provider_id",
                "credit_account_id"),
            List.of(
                "currency",
                "narration",
                "fx_quote_reference",
                "disbursement_subtype",
                "source_country",
                "destination_country",
                "corridor"),
            List.of(
                "transaction_id",
                "virtual_account_id",
                "principal_amount",
                "fee_amount",
                "currency",
                "merchant_id",
                "merchant_ref_id",
                "credit_provider_id",
                "credit_account_id"),
            "virtual_account_id",
            disbursementLifecycle()),
        // ---- Batch disbursement: reservation is the single runnerVisible "Batch Disbursement" ---
        batchEntry(
            FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST,
            "payment-service",
            batchReservationFields(),
            List.of(
                "batch_id",
                "batch_correlation_id",
                "merchant_id",
                "merchant_batch_ref",
                "total_principal_amount",
                "total_fees",
                "item_count"),
            List.of("currency", "disbursement_subtype", "callback_url"),
            List.of(
                "batch_id",
                "merchant_id",
                "total_principal_amount",
                "total_fees",
                "item_count",
                "currency"),
            "source",
            true,
            batchDisbursementGroup()),
        // ---- Hidden lifecycle secondary phases (rich descriptors, runnerVisible=false) --------
        richHiddenEntry(
            FlowType.SETTLEMENT_COMPLETED,
            "settlements-service",
            settlementCompletedFields(),
            List.of(
                "settlement_request_id",
                "source_organization_id",
                "completion_reference",
                "completed_by"),
            List.of("currency", "amount", "completed_at"),
            List.of(
                "settlement_request_id",
                "source_organization_id",
                "amount",
                "currency",
                "completion_reference",
                "completed_by"),
            "source_organization_id"),
        richHiddenEntry(
            FlowType.SETTLEMENT_FAILED,
            "settlements-service",
            settlementFailedFields(),
            List.of(
                "settlement_request_id",
                "organization_id",
                "virtual_account_id",
                "failure_reason_code",
                "failure_note",
                "marked_by"),
            List.of("destination_va_id", "marked_at"),
            List.of(
                "settlement_request_id",
                "organization_id",
                "virtual_account_id",
                "failure_reason_code",
                "failure_note",
                "marked_by"),
            "organization_id"),
        richHiddenEntry(
            FlowType.DISBURSEMENT_COMPLETED,
            "payment-service",
            disbursementCompletedFields(),
            List.of(
                "transaction_id",
                "reservation_id",
                "principal_amount",
                "provider_id",
                "provider_reference_id",
                "merchant_ref_id"),
            List.of(
                "currency",
                "disbursement_subtype",
                "recipient_reference",
                "destination_country",
                "corridor",
                "applied_fx_rate",
                "completed_at"),
            List.of(
                "transaction_id",
                "principal_amount",
                "currency",
                "provider_id",
                "provider_reference_id",
                "merchant_ref_id",
                "reservation_id"),
            "source"),
        richHiddenEntry(
            FlowType.DISBURSEMENT_FAILED,
            "payment-service",
            disbursementFailedFields(),
            List.of(
                "transaction_id",
                "virtual_account_id",
                "reservation_id",
                "principal_amount",
                "provider_id",
                "merchant_ref_id",
                "failure_reason"),
            List.of(
                "currency",
                "disbursement_subtype",
                "failure_code",
                "failed_at",
                "provider_reference_id"),
            List.of(
                "transaction_id",
                "virtual_account_id",
                "principal_amount",
                "currency",
                "provider_id",
                "merchant_ref_id",
                "reservation_id",
                "failure_reason"),
            "virtual_account_id"),
        // ---- Hidden batch phases (rich descriptors, runnerVisible=false; batchGroup=null) ------
        batchEntry(
            FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST,
            "payment-service",
            batchItemRequestFields(),
            List.of(
                "batch_id",
                "batch_correlation_id",
                "item_id",
                "merchant_item_ref",
                "merchant_id",
                "virtual_account_id",
                "principal_amount",
                "item_fee",
                "credit_provider_id",
                "credit_account_id"),
            List.of(
                "currency",
                "disbursement_subtype",
                "source_country",
                "destination_country",
                "corridor",
                "fx_quote_reference"),
            List.of(
                "batch_id",
                "item_id",
                "virtual_account_id",
                "principal_amount",
                "item_fee",
                "currency"),
            "virtual_account_id",
            false,
            null),
        batchEntry(
            FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED,
            "payment-service",
            batchItemCompletedFields(),
            List.of(
                "batch_id",
                "item_id",
                "virtual_account_id",
                "reservation_id",
                "principal_amount",
                "provider_id",
                "provider_reference_id",
                "merchant_item_ref"),
            List.of(
                "currency",
                "disbursement_subtype",
                "recipient_reference",
                "destination_country",
                "corridor",
                "applied_fx_rate",
                "completed_at"),
            List.of(
                "batch_id",
                "item_id",
                "virtual_account_id",
                "reservation_id",
                "principal_amount",
                "currency",
                "provider_id",
                "merchant_item_ref"),
            "virtual_account_id",
            false,
            null),
        batchEntry(
            FlowType.DISBURSEMENT_BATCH_ITEM_FAILED,
            "payment-service",
            batchItemFailedFields(),
            List.of(
                "batch_id",
                "item_id",
                "virtual_account_id",
                "reservation_id",
                "principal_amount",
                "provider_id",
                "merchant_item_ref",
                "failure_reason",
                "failure_code"),
            List.of("currency", "disbursement_subtype", "failed_at", "provider_reference_id"),
            List.of(
                "batch_id",
                "item_id",
                "virtual_account_id",
                "reservation_id",
                "principal_amount",
                "currency",
                "provider_id",
                "merchant_item_ref",
                "failure_reason"),
            "virtual_account_id",
            false,
            null),
        // ---- Hidden flows (minimal text descriptors, runnerVisible=false) ---------------------
        hiddenEntry(
            FlowType.ORGANIZATION_ONBOARDED,
            "organization-service",
            List.of(
                "id",
                "name",
                "type_id",
                "type_name",
                "country_id",
                "country_name",
                "iso_code",
                "status"),
            List.of("primary_contact_email"),
            List.of(
                "id",
                "name",
                "type_id",
                "type_name",
                "country_id",
                "country_name",
                "iso_code",
                "status"),
            "id"),
        hiddenEntry(
            FlowType.ORGANIZATION_VA_UPDATED,
            "organization-service",
            List.of("id", "status", "currency_id", "type_id"),
            List.of(),
            List.of("id", "status", "currency_id", "type_id"),
            "id"));
  }

  // ---- Lifecycle groupings ----------------------------------------------------------------------

  private static FlowLifecycle settlementLifecycle() {
    return new FlowLifecycle(
        "Settlement",
        FlowType.SETTLEMENT_INITIATED,
        FlowType.SETTLEMENT_COMPLETED,
        FlowType.SETTLEMENT_FAILED,
        List.of(
            new CarryOver("settlement_request_id", "settlement_request_id"),
            new CarryOver("virtual_account_id", "source_va_id"),
            new CarryOver("virtual_account_id", "virtual_account_id"),
            new CarryOver("amount", "amount"),
            new CarryOver("currency", "currency"),
            new CarryOver("organization_id", "source_organization_id"),
            new CarryOver("organization_id", "organization_id")));
  }

  private static FlowLifecycle disbursementLifecycle() {
    return new FlowLifecycle(
        "Disbursement",
        FlowType.DISBURSEMENT_INITIATED,
        FlowType.DISBURSEMENT_COMPLETED,
        FlowType.DISBURSEMENT_FAILED,
        List.of(
            new CarryOver("transaction_id", "transaction_id"),
            new CarryOver("virtual_account_id", "source_va_id"),
            new CarryOver("virtual_account_id", "virtual_account_id"),
            new CarryOver("principal_amount", "principal_amount"),
            new CarryOver("disbursement_subtype", "disbursement_subtype"),
            new CarryOver("currency", "currency"),
            new CarryOver("merchant_ref_id", "merchant_ref_id")));
  }

  // ---- Batch disbursement grouping --------------------------------------------------------------

  private static BatchDisbursementGroup batchDisbursementGroup() {
    return new BatchDisbursementGroup(
        "Batch Disbursement",
        FlowType.DISBURSEMENT_BATCH_RESERVATION_REQUEST,
        FlowType.DISBURSEMENT_BATCH_ITEM_REQUEST,
        FlowType.DISBURSEMENT_BATCH_ITEM_COMPLETED,
        FlowType.DISBURSEMENT_BATCH_ITEM_FAILED,
        // reservation → item (the source VA becomes the item's virtual_account_id)
        List.of(
            new CarryOver("batch_id", "batch_id"),
            new CarryOver("batch_correlation_id", "batch_correlation_id"),
            new CarryOver("merchant_id", "merchant_id"),
            new CarryOver("reservation_id", "reservation_id"),
            new CarryOver("source_va_id", "virtual_account_id"),
            new CarryOver("currency", "currency"),
            new CarryOver("disbursement_subtype", "disbursement_subtype"),
            new CarryOver("correlation_id", "correlation_id")),
        // item request → item terminal (item_fee feeds the terminal fee line)
        List.of(
            new CarryOver("batch_id", "batch_id"),
            new CarryOver("item_id", "item_id"),
            new CarryOver("item_sequence", "item_sequence"),
            new CarryOver("principal_amount", "principal_amount"),
            new CarryOver("item_fee", "item_fee"),
            new CarryOver("virtual_account_id", "virtual_account_id"),
            new CarryOver("disbursement_subtype", "disbursement_subtype"),
            new CarryOver("merchant_item_ref", "merchant_item_ref"),
            new CarryOver("provider_id", "provider_id"),
            new CarryOver("corridor", "corridor"),
            new CarryOver("destination_country", "destination_country")));
  }

  // ---- Per-flow descriptor lists ----------------------------------------------------------------

  private List<FlowFieldDescriptor> topUpFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("topup_request_id", "Transaction Request ID"));
    fields.add(vaRef("source_va_id", "Source VA ID", AccountKind.ORGANIZATION, "source"));
    fields.add(vaRef("destination_va_id", "Destination VA ID", AccountKind.SYSTEM, "destination"));
    fields.add(amount());
    fields.add(advInferred("organization_id", "Organization ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    // The ledger uses source_payment_reference as the journal transactionReference (required,
    // non-blank, unique). Auto-fill with a fresh UUID; still editable/clearable for chaos.
    fields.add(advAutogen("source_payment_reference", "Source Payment Reference"));
    fields.add(advText("approved_by", "Approved By", DEFAULT_ACTOR));
    fields.add(advDateTime("approved_at", "Approved At"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  private List<FlowFieldDescriptor> transferFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("transfer_request_id", "Transaction Request ID"));
    fields.add(vaRef("source_va_id", "Source VA ID", AccountKind.ORGANIZATION, "source"));
    fields.add(
        vaRef("destination_va_id", "Destination VA ID", AccountKind.ORGANIZATION, "destination"));
    fields.add(amount());
    fields.add(
        advInferred(
            "source_organization_id", "Source Organization ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(
        advInferred(
            "destination_organization_id",
            "Destination Organization ID",
            InferenceRule.ORG_FROM_DEST_VA));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    // Idea labels this "Source Payment Reference"; the payload wire field is `narrative`.
    fields.add(advText("narrative", "Source Payment Reference", DEFAULT_NARRATIVE));
    fields.add(advText("initiated_by", "Initiated By", DEFAULT_ACTOR));
    fields.add(advDateTime("initiated_at", "Initiated At"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  private List<FlowFieldDescriptor> treasuryFields(
      String requestIdField, String sourceChannelDefault, String destinationChannelDefault) {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid(requestIdField, "Transaction Request ID"));
    fields.add(vaRef("source_va_id", "Source VA ID", AccountKind.SYSTEM, "source"));
    fields.add(vaRef("destination_va_id", "Destination VA ID", AccountKind.SYSTEM, "destination"));
    fields.add(amount());
    fields.add(
        advSelect("source_channel", "Source Channel", sourceChannelDefault, CHANNEL_OPTIONS));
    fields.add(
        advSelect(
            "destination_channel",
            "Destination Channel",
            destinationChannelDefault,
            CHANNEL_OPTIONS));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    // The ledger uses completion_reference as the journal transactionReference (required,
    // non-blank, unique). Auto-fill with a fresh UUID; still editable/clearable for chaos.
    fields.add(advAutogen("completion_reference", "Completion Reference"));
    // Idea labels these "Initiated By/At"; the payload wire fields are
    // `completed_by`/`completed_at`.
    fields.add(advText("completed_by", "Initiated By", DEFAULT_ACTOR));
    fields.add(advDateTime("completed_at", "Initiated At"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  /** Collection: single {@code collection.completed} with a dynamic fee list. */
  private List<FlowFieldDescriptor> collectionFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("transaction_id", "Transaction ID"));
    fields.add(vaRef("source_va_id", "Source VA (Float)", AccountKind.SYSTEM, "source"));
    fields.add(
        vaRef(
            "destination_va_id",
            "Destination VA (Organization)",
            AccountKind.ORGANIZATION,
            "destination"));
    fields.add(amountField("amount", "Net Amount", DEFAULT_AMOUNT));
    fields.add(feeList("fees", "Fees", AccountKind.SYSTEM));
    fields.add(advText("provider_id", "Provider ID", DEFAULT_PROVIDER));
    fields.add(advUlid("provider_reference_id", "Provider Reference ID"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(advText("commission_split_id", "Commission Split ID", null));
    fields.add(advDateTime("completed_at", "Completed At"));
    fields.add(advUlid("merchant_ref_id", "Merchant Reference ID"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  /** Disbursement initiated: lifecycle step 1 (mints the transaction id; org VA reservation). */
  private List<FlowFieldDescriptor> disbursementInitiatedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("transaction_id", "Transaction ID"));
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(amountField("fee_amount", "Fee Amount", "10"));
    fields.add(advInferred("merchant_id", "Merchant ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(advUlid("merchant_ref_id", "Merchant Reference ID"));
    fields.add(advUlid("narration", "Narration"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(advText("credit_account_id", "Credit Account ID", DEFAULT_CREDIT_ACCOUNT));
    fields.add(advText("credit_provider_id", "Credit Provider ID", DEFAULT_PROVIDER));
    fields.add(country("source_country", "Source Country", DEFAULT_COUNTRY));
    fields.add(country("destination_country", "Destination Country", DEFAULT_COUNTRY));
    fields.add(derivedCorridor());
    fields.add(advText("fx_quote_reference", "FX Quote Reference", null));
    // Disbursement owns correlation_id as a first-class field (the ledger transaction reference),
    // so the global correlation_id advanced is intentionally omitted here.
    fields.add(advAutogen("correlation_id", "Correlation ID"));
    fields.add(advText("authorised_user_id", "Authorised User ID", "chaos-operator"));
    fields.add(advText("authorised_key_fingerprint", "Authorised Key Fingerprint", "ab:cd:ef:00"));
    fields.add(tenantAdvanced());
    return List.copyOf(fields);
  }

  /** Disbursement completed: lifecycle success phase (carry-over + reservation + fees). */
  private List<FlowFieldDescriptor> disbursementCompletedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("transaction_id", "Transaction ID"));
    fields.add(
        vaRef("source_va_id", "Source VA (Organization)", AccountKind.ORGANIZATION, "source"));
    fields.add(
        vaRef(
            "destination_va_id", "Destination VA (Settlement)", AccountKind.SYSTEM, "destination"));
    fields.add(reservationField("reservation_id", "Reservation ID"));
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(feeList("fees", "Fees", AccountKind.SYSTEM));
    fields.add(advText("provider_id", "Provider ID", DEFAULT_PROVIDER));
    fields.add(advUlid("provider_reference_id", "Provider Reference ID"));
    fields.add(advUlid("merchant_ref_id", "Merchant Reference ID"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(advText("recipient_reference", "Recipient Reference", null));
    fields.add(advText("destination_country", "Destination Country", null));
    fields.add(advText("corridor", "Corridor", null));
    fields.add(amountAdv("applied_fx_rate", "Applied FX Rate"));
    fields.add(advDateTime("completed_at", "Completed At"));
    return List.copyOf(fields);
  }

  /** Disbursement failed: lifecycle failure phase (carry-over + reservation + failure fields). */
  private List<FlowFieldDescriptor> disbursementFailedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("transaction_id", "Transaction ID"));
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(reservationField("reservation_id", "Reservation ID"));
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(reqText("failure_reason", "Failure Reason", "Disbursement failed"));
    fields.add(advUlid("merchant_ref_id", "Merchant Reference ID"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(
        advSelect("failure_code", "Failure Code", "PROVIDER_REJECTED", DISBURSEMENT_FAILURE_CODES));
    fields.add(advDateTime("failed_at", "Failed At"));
    fields.add(advText("provider_id", "Provider ID", DEFAULT_PROVIDER));
    fields.add(advUlid("provider_reference_id", "Provider Reference ID"));
    return List.copyOf(fields);
  }

  /** Batch reservation: step 1 (mints batch_id; org VA reservation for total_amount). */
  private List<FlowFieldDescriptor> batchReservationFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(
        vaRef("source_va_id", "Source VA (Organization)", AccountKind.ORGANIZATION, "source"));
    fields.add(
        vaRef(
            "destination_va_id",
            "Destination VA (Platform Float)",
            AccountKind.SYSTEM,
            "destination"));
    fields.add(amountField("total_principal_amount", "Total Principal Amount", DEFAULT_AMOUNT));
    fields.add(amountField("total_fees", "Total Fees", DEFAULT_BATCH_FEES));
    fields.add(integer("item_count", "Item Count (N)", DEFAULT_ITEM_COUNT));
    fields.add(
        reqSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(advUuid("batch_id", "Batch ID"));
    fields.add(advUuid("batch_correlation_id", "Batch Correlation ID"));
    fields.add(advUlid("merchant_batch_ref", "Merchant Batch Reference"));
    fields.add(advInferred("merchant_id", "Merchant ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(advAutogen("correlation_id", "Correlation ID"));
    fields.add(advText("callback_url", "Callback URL", null));
    fields.add(advText("authorised_user_id", "Authorised User ID", "chaos-operator"));
    fields.add(advText("authorised_key_fingerprint", "Authorised Key Fingerprint", "ab:cd:ef:00"));
    fields.add(tenantAdvanced());
    return List.copyOf(fields);
  }

  /** Batch item request: per item, inert at the ledger (carry-over + split prefill). */
  private List<FlowFieldDescriptor> batchItemRequestFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(amountField("item_fee", "Item Fee", DEFAULT_BATCH_FEES));
    fields.add(
        vaRefFlowAdv(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(advText("credit_provider_id", "Credit Provider ID", DEFAULT_PROVIDER));
    fields.add(advText("credit_account_id", "Credit Account ID", DEFAULT_CREDIT_ACCOUNT));
    fields.add(advUuid("item_id", "Item ID"));
    fields.add(integerAdv("item_sequence", "Item Sequence"));
    fields.add(advUlid("merchant_item_ref", "Merchant Item Reference"));
    fields.add(advUuid("batch_id", "Batch ID"));
    fields.add(advUuid("batch_correlation_id", "Batch Correlation ID"));
    fields.add(advText("merchant_id", "Merchant ID", null));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(country("source_country", "Source Country", DEFAULT_COUNTRY));
    fields.add(country("destination_country", "Destination Country", DEFAULT_COUNTRY));
    fields.add(derivedCorridor());
    fields.add(advText("fx_quote_reference", "FX Quote Reference", null));
    fields.add(advAutogen("correlation_id", "Correlation ID"));
    return List.copyOf(fields);
  }

  /** Batch item completed: per item success (partial capture; carry-over + reservation + fees). */
  private List<FlowFieldDescriptor> batchItemCompletedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(reservationField("reservation_id", "Reservation ID"));
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(feeList("fees", "Fees", AccountKind.SYSTEM));
    fields.add(advText("provider_id", "Provider ID", DEFAULT_PROVIDER));
    fields.add(advUlid("provider_reference_id", "Provider Reference ID"));
    fields.add(advUlid("merchant_item_ref", "Merchant Item Reference"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(advText("recipient_reference", "Recipient Reference", null));
    fields.add(advText("destination_country", "Destination Country", null));
    fields.add(advText("corridor", "Corridor", null));
    fields.add(amountAdv("applied_fx_rate", "Applied FX Rate"));
    fields.add(advDateTime("completed_at", "Completed At"));
    fields.add(integerAdv("item_sequence", "Item Sequence"));
    fields.add(advUuid("batch_id", "Batch ID"));
    fields.add(advUuid("item_id", "Item ID"));
    return List.copyOf(fields);
  }

  /** Batch item failed: per item failure (partial release; carry-over + reservation + failure). */
  private List<FlowFieldDescriptor> batchItemFailedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(reservationField("reservation_id", "Reservation ID"));
    fields.add(amountField("principal_amount", "Principal Amount", DEFAULT_AMOUNT));
    fields.add(feeList("fees", "Fees", AccountKind.SYSTEM));
    fields.add(reqText("failure_reason", "Failure Reason", "Batch item disbursement failed"));
    fields.add(advSelect("failure_code", "Failure Code", "RECIPIENT_INVALID", BATCH_FAILURE_CODES));
    fields.add(advText("provider_id", "Provider ID", DEFAULT_PROVIDER));
    fields.add(advUlid("provider_reference_id", "Provider Reference ID"));
    fields.add(advUlid("merchant_item_ref", "Merchant Item Reference"));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(
        advSelect("disbursement_subtype", "Disbursement Subtype", "DOMESTIC", SUBTYPE_OPTIONS));
    fields.add(advDateTime("failed_at", "Failed At"));
    fields.add(integerAdv("item_sequence", "Item Sequence"));
    fields.add(advUuid("batch_id", "Batch ID"));
    fields.add(advUuid("item_id", "Item ID"));
    return List.copyOf(fields);
  }

  /** Settlement initiated: lifecycle step 1 (org VA → bank). */
  private List<FlowFieldDescriptor> settlementInitiatedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("settlement_request_id", "Settlement Request ID"));
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(amountField("amount", "Amount", DEFAULT_AMOUNT));
    fields.add(advInferred("organization_id", "Organization ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(advUlid("destination_bank_account", "Destination Bank Account"));
    fields.add(advSelect("destination_bank", "Destination Bank", "ABSA", BANK_OPTIONS));
    fields.add(advText("approved_by", "Approved By", DEFAULT_ACTOR));
    fields.add(advDateTime("approved_at", "Approved At"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  /** Settlement completed: lifecycle success phase (carry-over + settlement VA). */
  private List<FlowFieldDescriptor> settlementCompletedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("settlement_request_id", "Settlement Request ID"));
    fields.add(
        advInferred(
            "source_organization_id", "Source Organization ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(
        vaRef("source_va_id", "Source VA (Organization)", AccountKind.ORGANIZATION, "source"));
    fields.add(
        vaRef(
            "settlement_va_id",
            "Settlement VA (Settlement Account)",
            AccountKind.SYSTEM,
            "destination"));
    fields.add(amountField("amount", "Amount", DEFAULT_AMOUNT));
    fields.add(advInferred("currency", "Currency", InferenceRule.CURRENCY_FROM_SOURCE_VA));
    fields.add(advAutogen("completion_reference", "Completion Reference"));
    fields.add(advText("completed_by", "Completed By", DEFAULT_ACTOR));
    fields.add(advDateTime("completed_at", "Completed At"));
    return List.copyOf(fields);
  }

  /** Settlement failed: lifecycle failure phase (carry-over + failure fields). */
  private List<FlowFieldDescriptor> settlementFailedFields() {
    var fields = new ArrayList<FlowFieldDescriptor>();
    fields.add(uuid("settlement_request_id", "Settlement Request ID"));
    fields.add(advInferred("organization_id", "Organization ID", InferenceRule.ORG_FROM_SOURCE_VA));
    fields.add(
        vaRefFlow(
            "virtual_account_id", "Virtual Account (Organization)", AccountKind.ORGANIZATION));
    fields.add(
        reqSelect(
            "failure_reason_code",
            "Failure Reason Code",
            "BANK_REJECTED",
            SETTLEMENT_FAILURE_CODES));
    fields.add(reqText("failure_note", "Failure Note", "Settlement failed"));
    fields.add(vaRefFlowAdv("destination_va_id", "Destination VA", AccountKind.SYSTEM));
    fields.add(advText("marked_by", "Marked By", DEFAULT_ACTOR));
    fields.add(advDateTime("marked_at", "Marked At"));
    return List.copyOf(fields);
  }

  /** Advanced fields appended to every runner flow. */
  private List<FlowFieldDescriptor> globalAdvanced() {
    return List.of(advText("correlation_id", "Correlation ID", null), tenantAdvanced());
  }

  private FlowFieldDescriptor tenantAdvanced() {
    return base("tenant_id", "Tenant ID", FieldKind.TEXT, false, true)
        .inference(InferenceRule.TENANT_FROM_SOURCE_VA)
        .build();
  }

  // ---- Descriptor factory helpers ---------------------------------------------------------------

  private static FlowFieldDescriptorBuilder base(
      String name, String label, FieldKind kind, boolean required, boolean advanced) {
    return FlowFieldDescriptorBuilder.builder()
        .name(name)
        .label(label)
        .kind(kind)
        .required(required)
        .advanced(advanced)
        .defaultValue(null)
        .autogen(AutogenRule.NONE)
        .inference(InferenceRule.NONE)
        .accountKind(null)
        .slotName(null)
        .options(null);
  }

  private static FlowFieldDescriptor uuid(String name, String label) {
    return base(name, label, FieldKind.UUID, true, false).autogen(AutogenRule.UUID_V4).build();
  }

  private static FlowFieldDescriptor vaRef(
      String name, String label, AccountKind accountKind, String slotName) {
    return base(name, label, FieldKind.VA_REF, true, false)
        .accountKind(accountKind)
        .slotName(slotName)
        .build();
  }

  /** A required VA picker whose value routes to {@code flowFields[name]} (no slot). */
  private static FlowFieldDescriptor vaRefFlow(String name, String label, AccountKind accountKind) {
    return base(name, label, FieldKind.VA_REF, true, false).accountKind(accountKind).build();
  }

  /** An advanced/optional VA picker whose value routes to {@code flowFields[name]} (no slot). */
  private static FlowFieldDescriptor vaRefFlowAdv(
      String name, String label, AccountKind accountKind) {
    return base(name, label, FieldKind.VA_REF, false, true).accountKind(accountKind).build();
  }

  /** The dynamic fee list; rows credit a SYSTEM fee-revenue VA and assemble into {@code fees[]}. */
  private static FlowFieldDescriptor feeList(String name, String label, AccountKind accountKind) {
    return base(name, label, FieldKind.FEE_LIST, true, false).accountKind(accountKind).build();
  }

  /** A supported-country select; options are client-fetched (Phase 010), seeded with an ISO code. */
  private static FlowFieldDescriptor country(String name, String label, String defaultIso) {
    return base(name, label, FieldKind.COUNTRY, false, true).defaultValue(defaultIso).build();
  }

  private static FlowFieldDescriptor amount() {
    return base("amount", "Amount", FieldKind.AMOUNT, true, false)
        .defaultValue(DEFAULT_AMOUNT)
        .build();
  }

  private static FlowFieldDescriptor amountField(String name, String label, String defaultValue) {
    return base(name, label, FieldKind.AMOUNT, true, false).defaultValue(defaultValue).build();
  }

  /** An advanced (optional) amount field (e.g. {@code applied_fx_rate}). */
  private static FlowFieldDescriptor amountAdv(String name, String label) {
    return base(name, label, FieldKind.AMOUNT, false, true).build();
  }

  /** A required (shown) whole-number field with a default (e.g. the batch {@code item_count}). */
  private static FlowFieldDescriptor integer(String name, String label, String defaultValue) {
    return base(name, label, FieldKind.INTEGER, true, false).defaultValue(defaultValue).build();
  }

  /** An advanced (collapsed) whole-number field (e.g. the carried {@code item_sequence}). */
  private static FlowFieldDescriptor integerAdv(String name, String label) {
    return base(name, label, FieldKind.INTEGER, false, true).build();
  }

  /** An advanced (collapsed) UUID field auto-filled with a fresh UUID (carried batch/item ids). */
  private static FlowFieldDescriptor advUuid(String name, String label) {
    return base(name, label, FieldKind.UUID, false, true).autogen(AutogenRule.UUID_V4).build();
  }

  private static FlowFieldDescriptor advInferred(String name, String label, InferenceRule rule) {
    return base(name, label, FieldKind.TEXT, false, true).inference(rule).build();
  }

  private static FlowFieldDescriptor advText(
      String name, String label, @Nullable String defaultValue) {
    return base(name, label, FieldKind.TEXT, false, true).defaultValue(defaultValue).build();
  }

  /** A required (shown) text field with a default. */
  private static FlowFieldDescriptor reqText(String name, String label, String defaultValue) {
    return base(name, label, FieldKind.TEXT, true, false).defaultValue(defaultValue).build();
  }

  /** Advanced (collapsed) field auto-filled with a fresh UUID — used for ledger reference fields. */
  private static FlowFieldDescriptor advAutogen(String name, String label) {
    return base(name, label, FieldKind.TEXT, false, true).autogen(AutogenRule.UUID_V4).build();
  }

  /** Advanced (collapsed) field auto-filled with a fresh ULID — used for ULID reference fields. */
  private static FlowFieldDescriptor advUlid(String name, String label) {
    return base(name, label, FieldKind.TEXT, false, true).autogen(AutogenRule.ULID).build();
  }

  private static FlowFieldDescriptor advDateTime(String name, String label) {
    return base(name, label, FieldKind.DATETIME, false, true).build();
  }

  private static FlowFieldDescriptor advSelect(
      String name, String label, String defaultValue, List<String> options) {
    return base(name, label, FieldKind.SELECT, false, true)
        .defaultValue(defaultValue)
        .options(options)
        .build();
  }

  /** A required (shown) select with a default. */
  private static FlowFieldDescriptor reqSelect(
      String name, String label, String defaultValue, List<String> options) {
    return base(name, label, FieldKind.SELECT, true, false)
        .defaultValue(defaultValue)
        .options(options)
        .build();
  }

  /** The reservation id field, rendered with the poll/loading/manual control by the wizard. */
  private static FlowFieldDescriptor reservationField(String name, String label) {
    return base(name, label, FieldKind.TEXT, true, false).build();
  }

  /** The derived corridor: {@code "{source_country}-{destination_country}"}, editable. */
  private static FlowFieldDescriptor derivedCorridor() {
    return base("corridor", "Corridor", FieldKind.TEXT, false, true)
        .inference(InferenceRule.CORRIDOR_FROM_COUNTRIES)
        .build();
  }

  /** Minimal text descriptors derived from a hidden flow's required/optional field lists. */
  private static List<FlowFieldDescriptor> minimalFields(
      List<String> requiredFields, List<String> optionalFields) {
    var fields = new ArrayList<FlowFieldDescriptor>();
    for (var name : requiredFields) {
      fields.add(base(name, name, FieldKind.TEXT, true, false).build());
    }
    for (var name : optionalFields) {
      fields.add(base(name, name, FieldKind.TEXT, false, true).build());
    }
    return List.copyOf(fields);
  }

  // ---- Entry factories --------------------------------------------------------------------------

  private FlowCatalogEntry runnerEntry(
      FlowType flowType,
      String source,
      List<FlowFieldDescriptor> fields,
      List<String> requiredFields,
      List<String> optionalFields,
      List<String> csvColumns,
      String partitionKeyField,
      @Nullable FlowLifecycle lifecycle) {
    return new FlowCatalogEntry(
        flowType,
        topicCatalog.topicFor(flowType),
        source,
        true,
        fields,
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField,
        lifecycle,
        null);
  }

  /**
   * A {@code runnerVisible} entry carrying a {@link BatchDisbursementGroup} (the reservation entry)
   * or a hidden batch phase entry ({@code runnerVisible = false}, {@code batchGroup = null}).
   */
  private FlowCatalogEntry batchEntry(
      FlowType flowType,
      String source,
      List<FlowFieldDescriptor> fields,
      List<String> requiredFields,
      List<String> optionalFields,
      List<String> csvColumns,
      String partitionKeyField,
      boolean runnerVisible,
      @Nullable BatchDisbursementGroup batchGroup) {
    return new FlowCatalogEntry(
        flowType,
        topicCatalog.topicFor(flowType),
        source,
        runnerVisible,
        fields,
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField,
        null,
        batchGroup);
  }

  private FlowCatalogEntry richHiddenEntry(
      FlowType flowType,
      String source,
      List<FlowFieldDescriptor> fields,
      List<String> requiredFields,
      List<String> optionalFields,
      List<String> csvColumns,
      String partitionKeyField) {
    return new FlowCatalogEntry(
        flowType,
        topicCatalog.topicFor(flowType),
        source,
        false,
        fields,
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField,
        null,
        null);
  }

  private FlowCatalogEntry hiddenEntry(
      FlowType flowType,
      String source,
      List<String> requiredFields,
      List<String> optionalFields,
      List<String> csvColumns,
      String partitionKeyField) {
    return new FlowCatalogEntry(
        flowType,
        topicCatalog.topicFor(flowType),
        source,
        false,
        minimalFields(requiredFields, optionalFields),
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField,
        null,
        null);
  }
}
