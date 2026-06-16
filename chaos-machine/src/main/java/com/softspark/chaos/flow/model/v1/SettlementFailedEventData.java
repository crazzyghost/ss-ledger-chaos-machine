package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Event data for organization.va.settlement.failed events.
 *
 * <p>Published when a VA settlement attempt fails and the request is marked as failed.
 *
 * @param settlementRequestId the settlement request identifier
 * @param organizationId the organization whose settlement failed
 * @param virtualAccountId the client virtual account for which settlement failed
 * @param failureReasonCode a machine-readable failure reason code
 * @param failureNote a human-readable explanation of the failure
 * @param markedBy the user or system that marked the failure
 * @param markedAt ISO-8601 timestamp when the failure was marked
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementFailedEventData(
    String settlementRequestId,
    String organizationId,
    String virtualAccountId,
    String failureReasonCode,
    String failureNote,
    String markedBy,
    String markedAt) {}
