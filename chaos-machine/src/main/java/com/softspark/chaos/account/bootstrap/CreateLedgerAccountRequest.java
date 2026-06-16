package com.softspark.chaos.account.bootstrap;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * Request body sent to the ledger service to create a new account.
 *
 * <p>Field names must match the camelCase JSON property names expected by {@code
 * POST /api/v0/accounts} on the ledger.
 *
 * @param accountCode          dot-separated hierarchical code
 * @param accountName          human-readable display name
 * @param accountCategory      one of ASSET / LIABILITY / REVENUE / EXPENSE / CONTRA
 * @param currency             ISO-4217 currency code
 * @param parentAccountId      ledger-assigned UUID of the parent account, or {@code null}
 * @param overdraftLimit       optional non-negative overdraft limit
 * @param minimumBalance       optional non-negative minimum balance floor
 * @param accountOwnershipType SYSTEM or ORGANIZATION
 * @param organizationId       owning organisation ID, or {@code null} for SYSTEM accounts
 */
@RecordBuilder
public record CreateLedgerAccountRequest(
    String accountCode,
    String accountName,
    String accountCategory,
    String currency,
    @Nullable String parentAccountId,
    @Nullable BigDecimal overdraftLimit,
    @Nullable BigDecimal minimumBalance,
    String accountOwnershipType,
    @Nullable String organizationId) {}
