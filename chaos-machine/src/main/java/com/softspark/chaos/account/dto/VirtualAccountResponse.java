package com.softspark.chaos.account.dto;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.account.enumeration.CreatedVia;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.time.Instant;

/**
 * Response record representing a virtual account.
 *
 * @param vaId           the virtual account ID
 * @param name           the virtual account name
 * @param ownershipType  the ownership type
 * @param organizationId the organization ID (if ORGANIZATION ownership)
 * @param currency       the currency code
 * @param status         the account status
 * @param channel        the channel (if any)
 * @param accountRole    the account role (if SYSTEM ownership)
 * @param createdVia     how the account was created
 * @param createdAt      the creation timestamp
 * @param updatedAt      the last update timestamp
 */
@RecordBuilder
public record VirtualAccountResponse(
        String vaId,
        String name,
        AccountOwnershipType ownershipType,
        String organizationId,
        String currency,
        AccountStatus status,
        Channel channel,
        AccountRole accountRole,
        CreatedVia createdVia,
        Instant createdAt,
        Instant updatedAt
) {
}
