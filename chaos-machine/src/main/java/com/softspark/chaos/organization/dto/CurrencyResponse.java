package com.softspark.chaos.organization.dto;

import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

/**
 * Response record representing a currency.
 *
 * @param currencyId the currency ID (UUID v4)
 * @param code       the ISO-4217 code (upper-cased)
 * @param name       the currency name
 * @param symbol     the currency symbol (nullable)
 * @param status     the currency status
 * @param createdAt  the creation timestamp
 * @param updatedAt  the last update timestamp
 */
@RecordBuilder
public record CurrencyResponse(
    String currencyId,
    String code,
    String name,
    String symbol,
    CurrencyStatus status,
    Instant createdAt,
    Instant updatedAt) {}
