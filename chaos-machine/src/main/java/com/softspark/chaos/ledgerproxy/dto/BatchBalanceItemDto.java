package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the ledger's batch-balance lookup (mirrors the ledger's {@code BatchBalanceItemDto},
 * camelCase).
 *
 * <p>Distinct from {@link LedgerBalanceDto}: the batch contract uses the {@code *Balance}-suffixed
 * bucket names ({@code availableBalance}/…/{@code totalBalance}) and carries a per-account {@code
 * status}. A {@code NOT_FOUND}/{@code FORBIDDEN} item has {@code null} balance fields. {@code status}
 * is kept as a {@link String} so the gateway stays transparent (no chaos-side enum).
 *
 * @param accountId the account UUID requested
 * @param status {@code FOUND} | {@code NOT_FOUND} | {@code FORBIDDEN}
 * @param currency the ISO-4217 currency code (null unless {@code FOUND})
 * @param availableBalance the available (spendable) balance (null unless {@code FOUND})
 * @param pendingBalance the pending balance (null unless {@code FOUND})
 * @param reservedBalance the reserved balance (null unless {@code FOUND})
 * @param totalBalance the total balance (null unless {@code FOUND})
 * @param lastEntrySequence the account's last applied entry sequence (null unless {@code FOUND})
 * @param balanceAsOf the instant the snapshot reflects (null unless {@code FOUND})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchBalanceItemDto(
    String accountId,
    String status,
    @Nullable String currency,
    @Nullable BigDecimal availableBalance,
    @Nullable BigDecimal pendingBalance,
    @Nullable BigDecimal reservedBalance,
    @Nullable BigDecimal totalBalance,
    @Nullable Long lastEntrySequence,
    @Nullable LocalDateTime balanceAsOf) {}
