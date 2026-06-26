package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.va.settlement.completed events.
 *
 * <p>Published when a VA settlement operation successfully completes and funds are moved from the
 * client VA to the settlement account.
 *
 * @param settlementRequestId the settlement request identifier
 * @param sourceOrganizationId the organization whose VA was settled
 * @param sourceVaId the client virtual account debited
 * @param settlementVaId the system SETTLEMENT_ACCOUNT virtual account credited (the ledger's
 *     confirmed destination field name — <em>not</em> {@code destination_va_id})
 * @param amount the settled amount
 * @param currency the ISO-4217 currency code
 * @param completionReference the external completion reference (the transaction reference)
 * @param completedBy the user or system that completed the settlement
 * @param completedAt ISO-8601 timestamp of completion
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementCompletedEventData(
    String settlementRequestId,
    String sourceOrganizationId,
    String sourceVaId,
    String settlementVaId,
    BigDecimal amount,
    String currency,
    String completionReference,
    String completedBy,
    String completedAt) {}
