package com.softspark.chaos.organization.dto;

import com.softspark.chaos.base.validation.IsInEnum;
import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;

/**
 * Request record for marking a country supported.
 *
 * @param countryId the referenced country ID (must exist)
 * @param status    optional status (defaults to ACTIVE)
 */
@RecordBuilder
public record CreateSupportedCountryRequest(
    @NotBlank(message = "Country ID is required") String countryId,
    @IsInEnum(enumClass = SupportedCountryStatus.class, message = "Invalid status") String status) {}
