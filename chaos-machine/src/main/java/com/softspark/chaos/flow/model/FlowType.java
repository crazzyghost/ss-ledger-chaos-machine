package com.softspark.chaos.flow.model;

/**
 * Transaction flow types supported by the chaos machine.
 *
 * <p>Each value corresponds to a distinct business event that can be published to a Kafka topic
 * via the flow engine. {@link #ORGANIZATION_VA_UPDATED} was formerly named {@code VA_UPDATED}.
 */
public enum FlowType {
  COLLECTION_COMPLETED,
  DISBURSEMENT_COMPLETED,
  SETTLEMENT_INITIATED,
  SETTLEMENT_COMPLETED,
  SETTLEMENT_FAILED,
  TOPUP_CONFIRMED,
  TRANSFER_REQUESTED,
  TREASURY_PREFUND_COMPLETED,
  TREASURY_SWEEP_COMPLETED,
  TREASURY_TRANSFER_COMPLETED,
  ORGANIZATION_ONBOARDED,
  ORGANIZATION_VA_UPDATED
}
