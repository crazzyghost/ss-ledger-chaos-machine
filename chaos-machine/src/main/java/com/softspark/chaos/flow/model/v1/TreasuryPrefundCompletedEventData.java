package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.treasury.prefund.completed events.
 *
 * <p>Published when the treasury pre-fund operation completes, moving funds from the settlement
 * account to the MTN float account.
 *
 * @param prefundRequestId the pre-fund request identifier
 * @param sourceChannel the source payment channel
 * @param destinationChannel the destination payment channel
 * @param sourceVaId the system SETTLEMENT_ACCOUNT virtual account
 * @param destinationVaId the system PLATFORM_FLOAT_MTN virtual account
 * @param amount the pre-fund amount
 * @param currency the ISO-4217 currency code
 * @param completionReference the external completion reference
 * @param completedBy the user or system that completed the operation
 * @param completedAt ISO-8601 timestamp of completion
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TreasuryPrefundCompletedEventData(
    String prefundRequestId,
    String sourceChannel,
    String destinationChannel,
    String sourceVaId,
    String destinationVaId,
    BigDecimal amount,
    String currency,
    String completionReference,
    String completedBy,
    String completedAt) {}
