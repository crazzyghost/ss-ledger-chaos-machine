package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * DTO mirroring a single per-account row of the ledger's trial-balance response.
 *
 * <p>One row per account that had movement in the requested period. Field names match the ledger's
 * camelCase {@code TrialBalanceEntry}; unknown fields are ignored so the proxy stays
 * forward-compatible as the ledger evolves its shape. Monetary amounts are carried as
 * {@link BigDecimal} (serialized as strings to the admin UI) to avoid floating-point drift.
 *
 * @param accountId the ledger account UUID (kept as {@code String}, matching {@link LedgerAccountDto})
 * @param accountCode the hierarchical account code (rows are returned ordered by this, ascending)
 * @param accountName the display name
 * @param accountOwnerId the owning org id, or {@code null} for SYSTEM accounts
 * @param accountOwnershipType {@code SYSTEM} or {@code ORGANIZATION} (string, not enum — the gateway
 *     is transparent)
 * @param currency the ISO-4217 currency code of this row
 * @param totalDebits sum of debit movement in the period
 * @param totalCredits sum of credit movement in the period
 * @param netMovement signed net movement (debits minus credits, per the ledger's convention)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrialBalanceEntryDto(
    String accountId,
    String accountCode,
    String accountName,
    @Nullable String accountOwnerId,
    String accountOwnershipType,
    String currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDebits,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredits,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal netMovement) {}
