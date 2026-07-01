package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO mirroring one sibling leg of a reconciliation journal-entry record.
 *
 * <p>A sibling line has the same shape as {@link ReconciliationEntryDto} minus its own
 * {@code siblingLines} list (the ledger nests one level of legs). Returned inside each element of
 * {@code GET /api/v0/reporting/reconciliation-export}. Field names match the ledger's camelCase
 * reconciliation record; unknown fields are ignored so the proxy stays forward-compatible.
 *
 * @param lineId the journal-entry-line surrogate key
 * @param journalEntryId the parent journal-entry id
 * @param postedAt ISO-8601 instant at which the entry was posted
 * @param entrySequence globally-ordered sequence number from the parent entry
 * @param accountSequence per-account monotonically increasing sequence number on this line
 * @param accountId the account this leg posted to
 * @param accountCode the account's chart-of-accounts code
 * @param organizationId the owning organization id
 * @param currency ISO-4217 currency code from the parent entry
 * @param direction whether this leg is a {@code DEBIT} or {@code CREDIT}
 * @param amount positive monetary amount of this leg
 * @param runningBalance available-balance snapshot immediately after this leg was posted
 * @param runningReservedBalance reserved-balance witness at post time
 * @param runningPendingBalance pending-balance witness at post time
 * @param totalBalanceBefore the account's total balance immediately before this leg was posted
 * @param transactionRef caller-supplied idempotency key on the parent entry
 * @param entryType business classification of the parent entry
 * @param narrative human-readable description from the parent entry
 * @param memo optional free-text annotation on this leg
 * @param sourceService the service that produced the originating event
 * @param sourceEventId the originating event id
 * @param metadata free-form metadata captured on the line
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReconciliationSiblingLineDto(
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
    @Nullable Map<String, Object> metadata) {}
