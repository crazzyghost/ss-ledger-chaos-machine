package com.softspark.chaos.organization.dto;

import com.softspark.chaos.base.validation.IsInEnum;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request record for updating a country.
 *
 * @param name         the country name
 * @param isoCode      the ISO 3166-1 alpha-2 or alpha-3 code (length 2-3, upper-cased)
 * @param status       optional status (defaults to ACTIVE when absent)
 * @param modifiedDate optional last business modification instant (defaults to now)
 */
@RecordBuilder
public record UpdateCountryRequest(
    @NotBlank(message = "Name is required") String name,
    @NotBlank(message = "ISO code is required")
        @Size(min = 2, max = 3, message = "ISO code must be 2 or 3 characters")
        String isoCode,
    @IsInEnum(enumClass = CountryStatus.class, message = "Invalid status") String status,
    Instant modifiedDate) {}
