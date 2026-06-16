package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Provides the static flow catalog metadata for all supported flow types.
 *
 * <p>Used by the catalog REST endpoint to expose required/optional fields and CSV column schemas.
 */
@Component
public class FlowCatalogProvider {

  private final TopicCatalog topicCatalog;

  public FlowCatalogProvider(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  /**
   * Returns the catalog entries for all 12 supported flow types.
   *
   * @return list of catalog entries
   */
  public List<FlowCatalogEntry> catalog() {
    return List.of(
        entry(
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
        entry(
            FlowType.ORGANIZATION_VA_UPDATED,
            "organization-service",
            List.of("id", "status", "currency_id", "type_id"),
            List.of(),
            List.of("id", "status", "currency_id", "type_id"),
            "id"),
        entry(
            FlowType.TOPUP_CONFIRMED,
            "topup-service",
            List.of("topup_request_id", "organization_id", "approved_by"),
            List.of("currency", "amount", "source_payment_reference", "approved_at"),
            List.of("topup_request_id", "organization_id", "amount", "currency", "approved_by"),
            "organization_id"),
        entry(
            FlowType.TRANSFER_REQUESTED,
            "transfer-service",
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
        entry(
            FlowType.TREASURY_PREFUND_COMPLETED,
            "treasury-service",
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
        entry(
            FlowType.TREASURY_SWEEP_COMPLETED,
            "treasury-service",
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
        entry(
            FlowType.TREASURY_TRANSFER_COMPLETED,
            "treasury-service",
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
        entry(
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
        entry(
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
        entry(
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
        entry(
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
        entry(
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

  private FlowCatalogEntry entry(
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
        requiredFields,
        optionalFields,
        csvColumns,
        partitionKeyField);
  }
}
