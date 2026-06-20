package com.softspark.chaos.organization.dto;

import com.softspark.chaos.base.validation.ISO4217;
import com.softspark.chaos.base.validation.IsInEnum;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;

/**
 * Request record for creating a currency.
 *
 * @param code   the ISO-4217 currency code (upper-cased, validated)
 * @param name   the currency name
 * @param symbol optional currency symbol
 * @param status optional status (defaults to ACTIVE)
 */
@RecordBuilder
public record CreateCurrencyRequest(
    @NotBlank(message = "Code is required") @ISO4217 String code,
    @NotBlank(message = "Name is required") String name,
    String symbol,
    @IsInEnum(enumClass = CurrencyStatus.class, message = "Invalid status") String status) {}
