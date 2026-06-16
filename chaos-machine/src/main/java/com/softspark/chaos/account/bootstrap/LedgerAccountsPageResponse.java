package com.softspark.chaos.account.bootstrap;

import java.util.List;

/**
 * Paginated wrapper for a list of {@link LedgerAccountResponse} entries returned by the ledger.
 *
 * <p>Mirrors the standard Spring Data page structure used by {@code GET /api/v0/accounts}.
 *
 * @param content       the accounts on this page
 * @param totalElements total number of accounts across all pages
 * @param totalPages    number of pages available
 * @param number        zero-based index of the current page
 */
public record LedgerAccountsPageResponse(
    List<LedgerAccountResponse> content, long totalElements, int totalPages, int number) {}
