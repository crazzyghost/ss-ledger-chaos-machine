package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.va.settlement.initiated events.
 *
 * <p>Published when a VA settlement request is approved and initiated.
 *
 * @param settlementRequestId the settlement request identifier
 * @param virtualAccountId the client virtual account being settled
 * @param organizationId the organization initiating the settlement
 * @param amount the settlement amount
 * @param currency the ISO-4217 currency code
 * @param destinationBankAccount the destination bank account number
 * @param destinationBank the destination bank code or name
 * @param approvedBy the user or system that approved the settlement
 * @param approvedAt ISO-8601 timestamp of approval
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementInitiatedEventData(
    String settlementRequestId,
    String virtualAccountId,
    String organizationId,
    BigDecimal amount,
    String currency,
    String destinationBankAccount,
    String destinationBank,
    String approvedBy,
    String approvedAt) {}
