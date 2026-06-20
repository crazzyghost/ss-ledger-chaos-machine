package com.softspark.chaos.organization.dto;

import com.softspark.chaos.organization.enumeration.CountryStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

/**
 * Response record representing a country.
 *
 * @param countryId         the country ID (UUID v4)
 * @param name              the country name
 * @param isoCode           the ISO code (upper-cased)
 * @param status            the country status
 * @param primaryCurrencyId the referenced primary currency ID (nullable)
 * @param primaryCurrency   the resolved primary currency {@code {id, code, name}} (nullable)
 * @param modifiedDate      the last business modification instant
 * @param createdAt         the creation timestamp
 * @param updatedAt         the last update timestamp
 */
@RecordBuilder
public record CountryResponse(
    String countryId,
    String name,
    String isoCode,
    CountryStatus status,
    String primaryCurrencyId,
    CurrencyRefResponse primaryCurrency,
    Instant modifiedDate,
    Instant createdAt,
    Instant updatedAt) {}
