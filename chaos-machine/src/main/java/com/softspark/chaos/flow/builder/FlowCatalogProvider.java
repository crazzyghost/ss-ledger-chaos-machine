package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.dto.AccountKind;
import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.FieldKind;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.dto.FlowFieldDescriptorBuilder;
import com.softspark.chaos.flow.dto.InferenceRule;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Provides the static flow catalog metadata for all supported flow types.
 *
 * <p>Used by the catalog REST endpoint to expose, per flow: structured field descriptors (driving
 * the Single Flow Run form), legacy required/optional field hints, and the CSV column schema.
 *
 * <p>Only the five transaction types listed in idea {@code 004_single_flow_run.md} are
 * {@code runnerVisible} and carry rich descriptors; every other flow is hidden from the runner and
 * carries minimal text descriptors derived from its required/optional lists.
 */
@Component
public class FlowCatalogProvider {

  /** Default seed for non-inferable "actor" fields (mirrors the bin-script samples). */
  private static final String DEFAULT_ACTOR = "ops@acme.example";

  private static final String DEFAULT_NARRATIVE = "Chaos run";
  private static final String DEFAULT_AMOUNT = "1000.0000";
  private static final List<String> CHANNEL_OPTIONS = List.of("bank", "momo");

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
            "organization_id"),
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
            "source_organization_id"),
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
            "prefund_request_id"),
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
            "sweep_request_id"),
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
            "transfer_request_id"),
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
            "id"),
        hiddenEntry(
            FlowType.SETTLEMENT_INITIATED,
            "settlement-service",
            List.of(
                "settlement_request_id",
                "virtual_account_id",
                "organization_id",
                "destination_bank_account",
                "destination_bank",
                "approved_by"),
            List.of("currency", "amount", "approved_at"),
            List.of(
                "settlement_request_id",
                "virtual_account_id",
                "organization_id",
                "amount",
                "currency",
                "destination_bank_account",
                "destination_bank",
                "approved_by"),
            "organization_id"),
        hiddenEntry(
            FlowType.SETTLEMENT_COMPLETED,
            "settlement-service",
            List.of("settlement_request_id", "source_organization_id", "completed_by"),
            List.of("currency", "amount", "completion_reference", "completed_at"),
            List.of(
                "settlement_request_id",
                "source_organization_id",
                "amount",
                "currency",
                "completed_by"),
            "source_organization_id"),
        hiddenEntry(
            FlowType.SETTLEMENT_FAILED,
            "settlement-service",
            List.of(
                "settlement_request_id",
                "organization_id",
                "virtual_account_id",
                "failure_reason_code",
                "failure_note",
                "marked_by"),
            List.of("marked_at"),
            List.of(
                "settlement_request_id",
                "organization_id",
                "virtual_account_id",
                "failure_reason_code",
                "failure_note",
                "marked_by"),
            "organization_id"),
        hiddenEntry(
            FlowType.COLLECTION_COMPLETED,
            "payment-service",
            List.of("collection_request_id", "merchant_reference", "provider_collection_id"),
            List.of("currency", "gross_amount", "net_amount", "fee_type"),
            List.of(
                "collection_request_id",
                "gross_amount",
                "net_amount",
                "currency",
                "merchant_reference",
                "provider_collection_id"),
            "destination"),
        hiddenEntry(
            FlowType.DISBURSEMENT_COMPLETED,
            "disbursement-service",
            List.of(
                "disbursement_request_id",
                "organization_id",
                "recipient_account_number",
                "recipient_bank",
                "merchant_reference",
                "provider_disbursement_id",
                "approved_by"),
            List.of("currency", "gross_amount", "net_amount", "fee_type", "completed_at"),
            List.of(
                "disbursement_request_id",
                "organization_id",
                "gross_amount",
                "net_amount",
                "currency",
                "recipient_account_number",
                "recipient_bank",
                "merchant_reference",
                "provider_disbursement_id",
                "approved_by"),
            "organization_id"));
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
    fields.add(vaRef("destination_va_id", "Destination VA ID", AccountKind.ORGANIZATION, "destination"));
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
    fields.add(advSelect("source_channel", "Source Channel", sourceChannelDefault, CHANNEL_OPTIONS));
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
    // Idea labels these "Initiated By/At"; the payload wire fields are `completed_by`/`completed_at`.
    fields.add(advText("completed_by", "Initiated By", DEFAULT_ACTOR));
    fields.add(advDateTime("completed_at", "Initiated At"));
    fields.addAll(globalAdvanced());
    return List.copyOf(fields);
  }

  /** Advanced fields appended to every runner flow. */
  private List<FlowFieldDescriptor> globalAdvanced() {
    return List.of(
        advText("correlation_id", "Correlation ID", null),
        base("tenant_id", "Tenant ID", FieldKind.TEXT, false, true)
            .inference(InferenceRule.TENANT_FROM_SOURCE_VA)
            .build());
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

  private static FlowFieldDescriptor amount() {
    return base("amount", "Amount", FieldKind.AMOUNT, true, false).defaultValue(DEFAULT_AMOUNT).build();
  }

  private static FlowFieldDescriptor advInferred(String name, String label, InferenceRule rule) {
    return base(name, label, FieldKind.TEXT, false, true).inference(rule).build();
  }

  private static FlowFieldDescriptor advText(String name, String label, String defaultValue) {
    return base(name, label, FieldKind.TEXT, false, true).defaultValue(defaultValue).build();
  }

  /** Advanced (collapsed) field auto-filled with a fresh UUID — used for ledger reference fields. */
  private static FlowFieldDescriptor advAutogen(String name, String label) {
    return base(name, label, FieldKind.TEXT, false, true).autogen(AutogenRule.UUID_V4).build();
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
      String partitionKeyField) {
    return new FlowCatalogEntry(
        flowType,
        topicCatalog.topicFor(flowType),
        source,
        true,
        fields,
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField);
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
        partitionKeyField);
  }
}
