package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO mirroring one row of the ledger's reconciliation journal-entries export
 * ({@code ReconciliationEntryRecord}).
 *
 * <p>Each row is a journal-entry <em>line</em> enriched with its parent-entry context and the
 * sibling legs that posted in the same entry. Returned per element by the ledger's
 * {@code GET /api/v0/reporting/reconciliation-export} (paged-JSON mode), which the chaos gateway
 * read-proxies as {@code GET /api/v0/ledger/reporting/reconciliation-export} (ADR-032). Field names match
 * the ledger's camelCase record; unknown fields are ignored so the proxy stays forward-compatible as
 * the ledger evolves its shape.
 *
 * @param lineId the journal-entry-line surrogate key
 * @param journalEntryId the parent journal-entry id
 * @param postedAt ISO-8601 instant at which the entry was posted
 * @param entrySequence globally-ordered sequence number from the parent entry
 * @param accountSequence per-account monotonically increasing sequence number on this line
 * @param accountId the account this line posted to
 * @param accountCode the account's chart-of-accounts code
 * @param organizationId the owning organization id
 * @param currency ISO-4217 currency code from the parent entry
 * @param direction whether this line is a {@code DEBIT} or {@code CREDIT}
 * @param amount positive monetary amount of this line
 * @param runningBalance available-balance snapshot immediately after this line was posted
 * @param runningReservedBalance reserved-balance witness at post time
 * @param runningPendingBalance pending-balance witness at post time
 * @param totalBalanceBefore the account's total balance immediately before this line was posted
 * @param transactionRef caller-supplied idempotency key on the parent entry (the row's link key)
 * @param entryType business classification of the parent entry
 * @param narrative human-readable description from the parent entry
 * @param memo optional free-text annotation on this line
 * @param sourceService the service that produced the originating event
 * @param sourceEventId the originating event id
 * @param metadata free-form metadata captured on the line
 * @param siblingLines the other legs that posted in the same journal entry
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReconciliationEntryDto(
    @Nullable String lineId,
    @Nullable String journalEntryId,
    @Nullable String postedAt,
    @Nullable Long entrySequence,
    @Nullable Long accountSequence,
    @Nullable String accountId,
    @Nullable String accountCode,
    @Nullable String organizationId,
    @Nullable String currency,
    @Nullable String direction,
    @Nullable BigDecimal amount,
    @Nullable BigDecimal runningBalance,
    @Nullable BigDecimal runningReservedBalance,
    @Nullable BigDecimal runningPendingBalance,
    @Nullable BigDecimal totalBalanceBefore,
    @Nullable String transactionRef,
    @Nullable String entryType,
    @Nullable String narrative,
    @Nullable String memo,
    @Nullable String sourceService,
    @Nullable String sourceEventId,
    @Nullable Map<String, Object> metadata,
    @Nullable List<ReconciliationSiblingLineDto> siblingLines) {}
