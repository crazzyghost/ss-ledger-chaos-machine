package com.softspark.chaos.account.dto;

import com.softspark.chaos.base.validation.ISO4217;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;

/**
 * Request record for updating an account role.
 *
 * @param defaultVaId the default virtual account ID for this role
 * @param currency    the currency code (ISO-4217)
 */
@RecordBuilder
public record UpdateRoleRequest(
    @NotBlank(message = "Default VA ID is required") String defaultVaId,
    @NotBlank(message = "Currency is required") @ISO4217 String currency) {}
