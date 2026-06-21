package com.softspark.chaos.account.dto;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.base.validation.ISO4217;
import com.softspark.chaos.base.validation.IsInEnum;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Request record for requesting creation of a virtual account.
 *
 * <p>Phase 009: the chaos machine no longer inserts VAs locally. This request is mapped to the
 * ledger's {@code POST /api/v0/accounts} contract and forwarded; the VA materializes in the
 * registry only when the resulting {@code ledger.account.created} event is consumed. SYSTEM
 * accounts require an {@code accountCode} and {@code accountCategory}; ORGANIZATION accounts require
 * an {@code organizationId} and may be requested with any ACTIVE currency.
 *
 * @param name            the account display name
 * @param ownershipType   the ownership type (SYSTEM or ORGANIZATION)
 * @param currency        the currency code (ISO-4217); validated ACTIVE against the currency table
 * @param organizationId  the owning organization id (required for ORGANIZATION ownership)
 * @param accountCode      hierarchical account code (required for SYSTEM)
 * @param accountCategory  account category (required for SYSTEM; defaults to LIABILITY for ORG)
 * @param parentAccountId  optional ledger parent account id
 * @param overdraftLimit   optional non-negative overdraft limit
 * @param minimumBalance   optional non-negative minimum balance floor
 */
@RecordBuilder
public record CreateVirtualAccountRequest(
    @NotBlank(message = "Name is required") String name,
    @NotNull(message = "Ownership type is required")
        @IsInEnum(enumClass = AccountOwnershipType.class, message = "Invalid ownership type")
        String ownershipType,
    @NotBlank(message = "Currency is required") @ISO4217 String currency,
    @Nullable String organizationId,
    @Nullable String accountCode,
    @Nullable @IsInEnum(enumClass = AccountCategory.class, message = "Invalid account category")
        String accountCategory,
    @Nullable String parentAccountId,
    @Nullable @PositiveOrZero BigDecimal overdraftLimit,
    @Nullable @PositiveOrZero BigDecimal minimumBalance) {}
