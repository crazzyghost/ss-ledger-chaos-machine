package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * Counterparty leg of a transaction-history record — one line in the same journal entry posted
 * against a different account than the one whose history is being queried.
 *
 * @param accountId the counterparty ledger-account identifier
 * @param accountCode the hierarchical account code for the counterparty account
 * @param accountName the display name for the counterparty account
 * @param direction whether this counterparty leg was a {@code DEBIT} or {@code CREDIT}
 * @param entryLineType per-line business classification ({@code FEE} for fee legs, else the entry
 *     type)
 * @param amount positive monetary value of this counterparty leg
 * @param memo optional free-text annotation on this counterparty leg
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerCounterpartyLineDto(
    @Nullable String accountId,
    @Nullable String accountCode,
    @Nullable String accountName,
    @Nullable String direction,
    @Nullable String entryLineType,
    @Nullable BigDecimal amount,
    @Nullable String memo) {}
