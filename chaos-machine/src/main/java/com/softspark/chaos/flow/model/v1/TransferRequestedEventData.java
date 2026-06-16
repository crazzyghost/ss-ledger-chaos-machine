package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.transfer.requested events.
 *
 * <p>Published when an organization requests a fund transfer to another organization.
 *
 * @param transferRequestId the transfer request identifier
 * @param sourceOrganizationId the sending organization
 * @param destinationOrganizationId the receiving organization
 * @param sourceVaId the source virtual account (client)
 * @param destinationVaId the destination virtual account (client)
 * @param amount the transfer amount
 * @param currency the ISO-4217 currency code
 * @param narrative the transfer narrative / description
 * @param initiatedBy the user or system that initiated the transfer
 * @param initiatedAt ISO-8601 timestamp of initiation
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransferRequestedEventData(
    String transferRequestId,
    String sourceOrganizationId,
    String destinationOrganizationId,
    String sourceVaId,
    String destinationVaId,
    BigDecimal amount,
    String currency,
    String narrative,
    String initiatedBy,
    String initiatedAt) {}
