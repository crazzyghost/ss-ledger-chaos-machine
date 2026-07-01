package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * Event data for organization.topup.confirmed events.
 *
 * <p>Published when an organization's top-up request is confirmed and funds are credited.
 *
 * @param topupRequestId the top-up request identifier
 * @param organizationId the organization receiving the top-up
 * @param sourceVaId the organization virtual account, credited (wire: {@code organization_va_id})
 * @param destinationVaId the system PLATFORM_FLOAT virtual account, debited (wire: {@code
 *     system_va_id})
 * @param amount the top-up amount
 * @param currency the ISO-4217 currency code
 * @param sourcePaymentReference the payment reference from the source payment provider
 * @param approvedBy the user or system that approved the top-up
 * @param approvedAt ISO-8601 timestamp of approval
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TopUpConfirmedEventData(
    String topupRequestId,
    String organizationId,
    @JsonProperty("organization_va_id") String sourceVaId,
    @JsonProperty("system_va_id") String destinationVaId,
    BigDecimal amount,
    String currency,
    String sourcePaymentReference,
    String approvedBy,
    String approvedAt) {}
