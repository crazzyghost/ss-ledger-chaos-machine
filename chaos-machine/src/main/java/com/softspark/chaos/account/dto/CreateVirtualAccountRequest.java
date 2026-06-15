package com.softspark.chaos.account.dto;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.base.validation.ISO4217;
import com.softspark.chaos.base.validation.IsInEnum;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request record for creating a virtual account.
 *
 * @param name             the virtual account name
 * @param ownershipType    the ownership type (SYSTEM or ORGANIZATION)
 * @param currency         the currency code (ISO-4217)
 * @param organizationId   optional organization ID (required for ORGANIZATION ownership)
 * @param organizationName optional organization name (for create-or-link)
 * @param channel          optional channel
 * @param status           optional status (defaults to ACTIVE)
 * @param vaId             optional VA ID (ULID generated if not provided)
 * @param announce         whether to announce the VA creation to Kafka (defaults to false)
 */
@RecordBuilder
public record CreateVirtualAccountRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Ownership type is required")
        @IsInEnum(enumClass = AccountOwnershipType.class, message = "Invalid ownership type")
        String ownershipType,

        @NotBlank(message = "Currency is required")
        @ISO4217
        String currency,

        String organizationId,
        String organizationName,

        @IsInEnum(enumClass = Channel.class, message = "Invalid channel")
        String channel,

        @IsInEnum(enumClass = AccountStatus.class, message = "Invalid status")
        String status,

        String vaId,
        Boolean announce
) {
}
