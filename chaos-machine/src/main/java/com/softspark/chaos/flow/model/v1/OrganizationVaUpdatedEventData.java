package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Event data for organization.va.updated events.
 * <p>
 * Published when an organization's virtual account is created or updated.
 *
 * @param id       the virtual account ID
 * @param status   the account status
 * @param currency the currency information
 * @param type     the account type information
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrganizationVaUpdatedEventData(
        String id,
        String status,
        CurrencyInfo currency,
        AccountType type
) {

    /**
     * Currency information.
     *
     * @param id the currency ID (ISO-4217 code)
     */
    @RecordBuilder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CurrencyInfo(String id) {
    }

    /**
     * Account type information.
     *
     * @param id the account type ID
     */
    @RecordBuilder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AccountType(String id) {
    }
}
