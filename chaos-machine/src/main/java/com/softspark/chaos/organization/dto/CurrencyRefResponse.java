package com.softspark.chaos.organization.dto;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Resolved currency reference embedded in country and supported-country responses.
 *
 * @param currencyId the currency ID (UUID v4)
 * @param code       the ISO-4217 code
 * @param name       the currency name
 */
@RecordBuilder
public record CurrencyRefResponse(String currencyId, String code, String name) {}
