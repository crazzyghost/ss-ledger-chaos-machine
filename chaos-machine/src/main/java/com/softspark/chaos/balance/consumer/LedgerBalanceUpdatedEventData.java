package com.softspark.chaos.balance.consumer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of the ledger's {@code AccountBalanceUpdatedEventData} (snake_case, no JSON type headers),
 * carried as the {@code data} of an {@link com.softspark.chaos.kafka.EventEnvelope} on {@code
 * ledger.balance.updated}.
 *
 * <p>The ledger emits <strong>one event per affected account</strong> carrying that account's
 * post-mutation snapshot. There is no transaction linkage in the payload (ADR-027). {@code
 * balanceAsOf} is a zoneless {@link LocalDateTime}; {@code lastEntrySequence} is a per-account
 * monotonic counter that may be {@code 0}.
 *
 * @param accountId the ledger account id (= chaos {@code va_id})
 * @param availableBalance available balance after the mutation
 * @param pendingBalance pending balance after the mutation
 * @param reservedBalance reserved balance after the mutation
 * @param totalBalance total balance after the mutation
 * @param totalDebits running total of debits
 * @param totalCredits running total of credits
 * @param lastEntrySequence per-account monotonic sequence (may be 0)
 * @param balanceAsOf zoneless snapshot timestamp
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LedgerBalanceUpdatedEventData(
    UUID accountId,
    BigDecimal availableBalance,
    BigDecimal pendingBalance,
    BigDecimal reservedBalance,
    BigDecimal totalBalance,
    BigDecimal totalDebits,
    BigDecimal totalCredits,
    long lastEntrySequence,
    LocalDateTime balanceAsOf) {}
