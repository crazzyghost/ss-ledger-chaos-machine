package com.softspark.chaos.kafka;

import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed catalog of all Kafka topics used by the chaos machine.
 * <p>
 * Topic names default to the ledger's conventions and can be overridden via
 * {@code chaos.topics.*} configuration properties. This ensures type-safe topic
 * references and centralized topic management.
 */
@Component
@ConfigurationProperties(prefix = "chaos.topics")
public class TopicCatalog {

  private String organizationOnboarded = "organization.onboarded";
  private String organizationVaUpdated = "organization.va.updated";
  private String organizationTopupConfirmed = "organization.topup.confirmed";
  private String organizationTransferRequested = "organization.transfer.requested";
  private String organizationTreasuryPrefundCompleted = "organization.treasury.prefund.completed";
  private String organizationTreasurySweepCompleted = "organization.treasury.sweep.completed";
  private String organizationTreasuryTransferCompleted = "organization.treasury.transfer.completed";
  private String organizationVaSettlementInitiated = "organization.va.settlement.initiated";
  private String organizationVaSettlementCompleted = "organization.va.settlement.completed";
  private String organizationVaSettlementFailed = "organization.va.settlement.failed";
  private String collectionCompleted = "collection.completed";
  private String disbursementInitiated = "disbursement.initiated";
  private String disbursementCompleted = "disbursement.completed";
  private String disbursementFailed = "disbursement.failed";
  private String disbursementBatchInitiated = "disbursement.batch.initiated";
  private String disbursementBatchItemCompleted = "disbursement.batch.item.completed";
  private String disbursementBatchItemFailed = "disbursement.batch.item.failed";
  private String ledgerAccountCreated = "ledger.account.created";
  private String ledgerTransactionFailed = "ledger.transaction.failed";
  private String ledgerBalanceUpdated = "ledger.balance.updated";
  private String ledgerReservationCreated = "ledger.reservation.created";
  private String ledgerReservationReleased = "ledger.reservation.released";

  /**
   * The ledger's 17 inbound dead-letter topics (Phase 020, ADR-029). An explicit list — not a
   * {@code ledger\..*\.dlt} wildcard — so the chaos machine's own outbound-event DLTs (a different
   * format) are deliberately excluded. Overridable via {@code chaos.topics.ledger-dlts}.
   */
  private List<String> ledgerDlts =
      List.of(
          "ledger.collection.completed.dlt",
          "ledger.disbursement.initiated.dlt",
          "ledger.disbursement.completed.dlt",
          "ledger.disbursement.failed.dlt",
          "ledger.disbursement.batch.initiated.dlt",
          "ledger.disbursement.batch.item.completed.dlt",
          "ledger.disbursement.batch.item.failed.dlt",
          "ledger.organization.va.settlement.initiated.dlt",
          "ledger.organization.va.settlement.completed.dlt",
          "ledger.organization.va.settlement.failed.dlt",
          "ledger.organization.onboarded.dlt",
          "ledger.organization.va.updated.dlt",
          "ledger.organization.topup.confirmed.dlt",
          "ledger.organization.transfer.requested.dlt",
          "ledger.organization.treasury.prefund.completed.dlt",
          "ledger.organization.treasury.sweep.completed.dlt",
          "ledger.organization.treasury.transfer.completed.dlt");

  public String getOrganizationOnboarded() {
    return organizationOnboarded;
  }

  public void setOrganizationOnboarded(String organizationOnboarded) {
    this.organizationOnboarded = organizationOnboarded;
  }

  public String getOrganizationVaUpdated() {
    return organizationVaUpdated;
  }

  public void setOrganizationVaUpdated(String organizationVaUpdated) {
    this.organizationVaUpdated = organizationVaUpdated;
  }

  public String getOrganizationTopupConfirmed() {
    return organizationTopupConfirmed;
  }

  public void setOrganizationTopupConfirmed(String organizationTopupConfirmed) {
    this.organizationTopupConfirmed = organizationTopupConfirmed;
  }

  public String getOrganizationTransferRequested() {
    return organizationTransferRequested;
  }

  public void setOrganizationTransferRequested(String organizationTransferRequested) {
    this.organizationTransferRequested = organizationTransferRequested;
  }

  public String getOrganizationTreasuryPrefundCompleted() {
    return organizationTreasuryPrefundCompleted;
  }

  public void setOrganizationTreasuryPrefundCompleted(String organizationTreasuryPrefundCompleted) {
    this.organizationTreasuryPrefundCompleted = organizationTreasuryPrefundCompleted;
  }

  public String getOrganizationTreasurySweepCompleted() {
    return organizationTreasurySweepCompleted;
  }

  public void setOrganizationTreasurySweepCompleted(String organizationTreasurySweepCompleted) {
    this.organizationTreasurySweepCompleted = organizationTreasurySweepCompleted;
  }

  public String getOrganizationTreasuryTransferCompleted() {
    return organizationTreasuryTransferCompleted;
  }

  public void setOrganizationTreasuryTransferCompleted(
      String organizationTreasuryTransferCompleted) {
    this.organizationTreasuryTransferCompleted = organizationTreasuryTransferCompleted;
  }

  public String getOrganizationVaSettlementInitiated() {
    return organizationVaSettlementInitiated;
  }

  public void setOrganizationVaSettlementInitiated(String organizationVaSettlementInitiated) {
    this.organizationVaSettlementInitiated = organizationVaSettlementInitiated;
  }

  public String getOrganizationVaSettlementCompleted() {
    return organizationVaSettlementCompleted;
  }

  public void setOrganizationVaSettlementCompleted(String organizationVaSettlementCompleted) {
    this.organizationVaSettlementCompleted = organizationVaSettlementCompleted;
  }

  public String getOrganizationVaSettlementFailed() {
    return organizationVaSettlementFailed;
  }

  public void setOrganizationVaSettlementFailed(String organizationVaSettlementFailed) {
    this.organizationVaSettlementFailed = organizationVaSettlementFailed;
  }

  public String getCollectionCompleted() {
    return collectionCompleted;
  }

  public void setCollectionCompleted(String collectionCompleted) {
    this.collectionCompleted = collectionCompleted;
  }

  /**
   * Returns the topic name for disbursement initiated events.
   *
   * @return the disbursement initiated topic name
   */
  public String getDisbursementInitiated() {
    return disbursementInitiated;
  }

  /**
   * Sets the topic name for disbursement initiated events.
   *
   * @param disbursementInitiated the disbursement initiated topic name
   */
  public void setDisbursementInitiated(String disbursementInitiated) {
    this.disbursementInitiated = disbursementInitiated;
  }

  /**
   * Returns the topic name for disbursement completed events.
   *
   * @return the disbursement completed topic name
   */
  public String getDisbursementCompleted() {
    return disbursementCompleted;
  }

  /**
   * Sets the topic name for disbursement completed events.
   *
   * @param disbursementCompleted the disbursement completed topic name
   */
  public void setDisbursementCompleted(String disbursementCompleted) {
    this.disbursementCompleted = disbursementCompleted;
  }

  /**
   * Returns the topic name for disbursement failed events.
   *
   * @return the disbursement failed topic name
   */
  public String getDisbursementFailed() {
    return disbursementFailed;
  }

  /**
   * Sets the topic name for disbursement failed events.
   *
   * @param disbursementFailed the disbursement failed topic name
   */
  public void setDisbursementFailed(String disbursementFailed) {
    this.disbursementFailed = disbursementFailed;
  }

  /**
   * Returns the topic for batch-disbursement initiated events (shared by the batch reservation
   * request and the per-item request, discriminated by the payload {@code operation} field).
   *
   * @return the {@code disbursement.batch.initiated} topic name
   */
  public String getDisbursementBatchInitiated() {
    return disbursementBatchInitiated;
  }

  /**
   * Sets the topic for batch-disbursement initiated events.
   *
   * @param disbursementBatchInitiated the topic name
   */
  public void setDisbursementBatchInitiated(String disbursementBatchInitiated) {
    this.disbursementBatchInitiated = disbursementBatchInitiated;
  }

  /**
   * Returns the topic for batch-disbursement item-completed events.
   *
   * @return the {@code disbursement.batch.item.completed} topic name
   */
  public String getDisbursementBatchItemCompleted() {
    return disbursementBatchItemCompleted;
  }

  /**
   * Sets the topic for batch-disbursement item-completed events.
   *
   * @param disbursementBatchItemCompleted the topic name
   */
  public void setDisbursementBatchItemCompleted(String disbursementBatchItemCompleted) {
    this.disbursementBatchItemCompleted = disbursementBatchItemCompleted;
  }

  /**
   * Returns the topic for batch-disbursement item-failed events.
   *
   * @return the {@code disbursement.batch.item.failed} topic name
   */
  public String getDisbursementBatchItemFailed() {
    return disbursementBatchItemFailed;
  }

  /**
   * Sets the topic for batch-disbursement item-failed events.
   *
   * @param disbursementBatchItemFailed the topic name
   */
  public void setDisbursementBatchItemFailed(String disbursementBatchItemFailed) {
    this.disbursementBatchItemFailed = disbursementBatchItemFailed;
  }

  /**
   * Returns the topic the chaos machine consumes for ledger account-created events.
   *
   * @return the {@code ledger.account.created} topic name
   */
  public String getLedgerAccountCreated() {
    return ledgerAccountCreated;
  }

  /**
   * Sets the topic the chaos machine consumes for ledger account-created events.
   *
   * @param ledgerAccountCreated the topic name
   */
  public void setLedgerAccountCreated(String ledgerAccountCreated) {
    this.ledgerAccountCreated = ledgerAccountCreated;
  }

  /**
   * Returns the topic the chaos machine consumes for ledger transaction-failed events (Part 1 of
   * the ledger-outbound series).
   *
   * @return the {@code ledger.transaction.failed} topic name
   */
  public String getLedgerTransactionFailed() {
    return ledgerTransactionFailed;
  }

  /**
   * Sets the topic the chaos machine consumes for ledger transaction-failed events.
   *
   * @param ledgerTransactionFailed the topic name
   */
  public void setLedgerTransactionFailed(String ledgerTransactionFailed) {
    this.ledgerTransactionFailed = ledgerTransactionFailed;
  }

  /**
   * Returns the topic the chaos machine consumes for ledger balance-updated events (Part 2).
   *
   * @return the {@code ledger.balance.updated} topic name
   */
  public String getLedgerBalanceUpdated() {
    return ledgerBalanceUpdated;
  }

  /**
   * Sets the topic the chaos machine consumes for ledger balance-updated events.
   *
   * @param ledgerBalanceUpdated the topic name
   */
  public void setLedgerBalanceUpdated(String ledgerBalanceUpdated) {
    this.ledgerBalanceUpdated = ledgerBalanceUpdated;
  }

  /**
   * Returns the topic the chaos machine consumes for ledger reservation-created events (Part 3).
   *
   * @return the {@code ledger.reservation.created} topic name
   */
  public String getLedgerReservationCreated() {
    return ledgerReservationCreated;
  }

  /**
   * Sets the topic the chaos machine consumes for ledger reservation-created events.
   *
   * @param ledgerReservationCreated the topic name
   */
  public void setLedgerReservationCreated(String ledgerReservationCreated) {
    this.ledgerReservationCreated = ledgerReservationCreated;
  }

  /**
   * Returns the topic the chaos machine consumes for ledger reservation-released events (Part 3).
   *
   * @return the {@code ledger.reservation.released} topic name
   */
  public String getLedgerReservationReleased() {
    return ledgerReservationReleased;
  }

  /**
   * Sets the topic the chaos machine consumes for ledger reservation-released events.
   *
   * @param ledgerReservationReleased the topic name
   */
  public void setLedgerReservationReleased(String ledgerReservationReleased) {
    this.ledgerReservationReleased = ledgerReservationReleased;
  }

  /**
   * Returns the explicit list of ledger inbound dead-letter topics the DLQ consumer subscribes to.
   *
   * @return the {@code ledger.<flow>.dlt} topic names
   */
  public List<String> getLedgerDlts() {
    return ledgerDlts;
  }

  /**
   * Sets the list of ledger inbound dead-letter topics the DLQ consumer subscribes to.
   *
   * @param ledgerDlts the topic names
   */
  public void setLedgerDlts(List<String> ledgerDlts) {
    this.ledgerDlts = ledgerDlts;
  }

  /**
   * Derives the dead-letter topic for a source topic by appending {@code .dlt}. The DLT
   * publishing recoverer uses the same rule, so every ledger-outbound listener dead-letters to its
   * own {@code <topic>.dlt} with no per-event configuration.
   *
   * @param topic the source topic
   * @return {@code topic + ".dlt"}
   */
  public static String dltFor(String topic) {
    return topic + ".dlt";
  }

  /**
   * Resolves the Kafka topic name for the given {@link FlowType}.
   *
   * @param flowType the flow type to resolve
   * @return the Kafka topic name
   * @throws IllegalArgumentException if the flow type has no mapped topic
   */
  public String topicFor(FlowType flowType) {
    return switch (flowType) {
      case ORGANIZATION_ONBOARDED -> organizationOnboarded;
      case ORGANIZATION_VA_UPDATED -> organizationVaUpdated;
      case TOPUP_CONFIRMED -> organizationTopupConfirmed;
      case TRANSFER_REQUESTED -> organizationTransferRequested;
      case TREASURY_PREFUND_COMPLETED -> organizationTreasuryPrefundCompleted;
      case TREASURY_SWEEP_COMPLETED -> organizationTreasurySweepCompleted;
      case TREASURY_TRANSFER_COMPLETED -> organizationTreasuryTransferCompleted;
      case SETTLEMENT_INITIATED -> organizationVaSettlementInitiated;
      case SETTLEMENT_COMPLETED -> organizationVaSettlementCompleted;
      case SETTLEMENT_FAILED -> organizationVaSettlementFailed;
      case COLLECTION_COMPLETED -> collectionCompleted;
      case DISBURSEMENT_INITIATED -> disbursementInitiated;
      case DISBURSEMENT_COMPLETED -> disbursementCompleted;
      case DISBURSEMENT_FAILED -> disbursementFailed;
      case DISBURSEMENT_BATCH_RESERVATION_REQUEST, DISBURSEMENT_BATCH_ITEM_REQUEST ->
          disbursementBatchInitiated;
      case DISBURSEMENT_BATCH_ITEM_COMPLETED -> disbursementBatchItemCompleted;
      case DISBURSEMENT_BATCH_ITEM_FAILED -> disbursementBatchItemFailed;
    };
  }
}
