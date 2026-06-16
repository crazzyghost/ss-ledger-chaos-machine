package com.softspark.chaos.account.bootstrap;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Represents a single account entry from the ledger service's account-query response.
 *
 * <p>Field names correspond to the camelCase JSON properties returned by the ledger's
 * {@code GET /api/v0/accounts} endpoint.
 *
 * @param accountId           ledger-assigned UUID (the virtual-account identifier)
 * @param accountCode         dot-separated hierarchical code
 * @param accountName         human-readable display name
 * @param accountCategory     account category (ASSET, REVENUE, etc.)
 * @param normalBalance       normal balance side (DEBIT or CREDIT)
 * @param currency            ISO-4217 currency code
 * @param status              account status (ACTIVE, etc.)
 * @param accountOwnershipType SYSTEM or ORGANIZATION
 */
@RecordBuilder
public record LedgerAccountResponse(
    String accountId,
    String accountCode,
    String accountName,
    String accountCategory,
    String normalBalance,
    String currency,
    String status,
    String accountOwnershipType) {}
