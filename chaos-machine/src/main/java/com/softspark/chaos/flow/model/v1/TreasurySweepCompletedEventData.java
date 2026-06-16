package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.treasury.sweep.completed events.
 *
 * <p>Published when the treasury sweep operation completes, moving funds from the MTN float account
 * back to the settlement account.
 *
 * @param sweepRequestId the sweep request identifier
 * @param sourceChannel the source payment channel
 * @param destinationChannel the destination payment channel
 * @param sourceVaId the system PLATFORM_FLOAT_MTN virtual account
 * @param destinationVaId the system SETTLEMENT_ACCOUNT virtual account
 * @param amount the sweep amount
 * @param currency the ISO-4217 currency code
 * @param completionReference the external completion reference
 * @param completedBy the user or system that completed the operation
 * @param completedAt ISO-8601 timestamp of completion
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TreasurySweepCompletedEventData(
    String sweepRequestId,
    String sourceChannel,
    String destinationChannel,
    String sourceVaId,
    String destinationVaId,
    BigDecimal amount,
    String currency,
    String completionReference,
    String completedBy,
    String completedAt) {}
