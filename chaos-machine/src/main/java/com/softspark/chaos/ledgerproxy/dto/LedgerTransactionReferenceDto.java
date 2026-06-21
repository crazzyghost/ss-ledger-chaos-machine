package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * DTO mirroring the ledger's transaction-by-reference history record (one journal-entry line across
 * every account that participated in a single {@code transactionRef}).
 *
 * <p>Returned per row by {@code GET /api/v0/transactions/{ref}} and surfaced through the proxy to
 * back the transaction detail page. Field names match the ledger's camelCase
 * {@code TransactionReferenceHistoryRecord}; unknown fields are ignored for forward-compatibility.
 *
 * @param lineId the journal-entry-line surrogate key
 * @param journalEntryId the parent journal-entry id
 * @param accountId the account this leg was posted against
 * @param accountOwnershipType the owning party type of {@code accountId} ({@code SYSTEM} /
 *     {@code ORGANIZATION})
 * @param accountOwnerId the owner id when the account is organization-owned, else {@code null}
 * @param postedAt ISO-8601 instant at which the entry was posted
 * @param transactionRef the shared transaction reference (idempotency key)
 * @param entryType business classification of the parent entry
 * @param entryLineType per-line business classification ({@code FEE} for fee legs)
 * @param direction whether this line is a {@code DEBIT} or {@code CREDIT}
 * @param amount positive monetary amount of this line
 * @param currency ISO-4217 currency code
 * @param runningBalance available-balance snapshot for {@code accountId} immediately after this line
 * @param runningReservedBalance reserved-balance witness at post time
 * @param runningPendingBalance pending-balance witness at post time
 * @param narrative human-readable description from the parent entry
 * @param memo optional free-text annotation on this line
 * @param entrySequence globally-ordered sequence number from the parent entry
 * @param accountSequence per-account monotonically increasing sequence number on this line
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerTransactionReferenceDto(
    String lineId,
    @Nullable String journalEntryId,
    @Nullable String accountId,
    @Nullable String accountOwnershipType,
    @Nullable String accountOwnerId,
    @Nullable String postedAt,
    @Nullable String transactionRef,
    @Nullable String entryType,
    @Nullable String entryLineType,
    @Nullable String direction,
    @Nullable BigDecimal amount,
    @Nullable String currency,
    @Nullable BigDecimal runningBalance,
    @Nullable BigDecimal runningReservedBalance,
    @Nullable BigDecimal runningPendingBalance,
    @Nullable String narrative,
    @Nullable String memo,
    long entrySequence,
    long accountSequence) {}
