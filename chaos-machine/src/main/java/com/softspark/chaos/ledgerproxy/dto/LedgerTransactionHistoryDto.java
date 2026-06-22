package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO mirroring the ledger's account transaction-history record (one {@code journal_entry_lines}
 * row enriched with parent-entry header fields and counterparty legs).
 *
 * <p>Returned per row by {@code GET /api/v0/accounts/{id}/transactions}. Field names match
 * the ledger's camelCase {@code JournalEntryHistoryRecordDto}; unknown fields are ignored so the
 * proxy stays forward-compatible as the ledger evolves its shape.
 *
 * @param lineId the journal-entry-line surrogate key
 * @param journalEntryId the parent journal-entry id
 * @param postedAt ISO-8601 instant at which the entry was posted
 * @param transactionRef caller-supplied idempotency key on the parent entry
 * @param entryType business classification of the parent entry
 * @param entryLineType per-line business classification ({@code FEE} for fee legs, else the entry
 *     type)
 * @param direction whether this line is a {@code DEBIT} or {@code CREDIT}
 * @param amount positive monetary amount of this line
 * @param currency ISO-4217 currency code from the parent entry
 * @param runningBalance available-balance snapshot immediately after this line was posted
 * @param runningReservedBalance reserved-balance witness at post time
 * @param runningPendingBalance pending-balance witness at post time
 * @param narrative human-readable description from the parent entry
 * @param memo optional free-text annotation on this line
 * @param entrySequence globally-ordered sequence number from the parent entry
 * @param accountSequence per-account monotonically increasing sequence number on this line
 * @param primaryCounterpartyAccountId the single counterparty account, or {@code null} for
 *     multi-leg or same-account entries
 * @param counterPartyJournalEntryLines all counterparty legs in the same entry
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerTransactionHistoryDto(
    String lineId,
    @Nullable String journalEntryId,
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
    long accountSequence,
    @Nullable String primaryCounterpartyAccountId,
    @Nullable List<LedgerCounterpartyLineDto> counterPartyJournalEntryLines) {}
