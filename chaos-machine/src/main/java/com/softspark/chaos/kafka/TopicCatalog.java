package com.softspark.chaos.kafka;

import com.softspark.chaos.flow.model.FlowType;
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
  private String disbursementCompleted = "disbursement.completed";
  private String ledgerAccountCreated = "ledger.account.created";

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
      case DISBURSEMENT_COMPLETED -> disbursementCompleted;
    };
  }
}
