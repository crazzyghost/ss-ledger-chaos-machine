package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.treasury.transfer.completed events.
 *
 * <p>Published when a treasury inter-system transfer completes between any two system virtual
 * accounts.
 *
 * @param transferRequestId the transfer request identifier
 * @param sourceChannel the source payment channel
 * @param destinationChannel the destination payment channel
 * @param sourceVaId the source system virtual account
 * @param destinationVaId the destination system virtual account
 * @param amount the transfer amount
 * @param currency the ISO-4217 currency code
 * @param completionReference the external completion reference
 * @param completedBy the user or system that completed the operation
 * @param completedAt ISO-8601 timestamp of completion
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TreasuryTransferCompletedEventData(
    String transferRequestId,
    String sourceChannel,
    String destinationChannel,
    String sourceVaId,
    String destinationVaId,
    BigDecimal amount,
    String currency,
    String completionReference,
    String completedBy,
    String completedAt) {}
