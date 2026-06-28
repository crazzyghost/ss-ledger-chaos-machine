package com.softspark.chaos.balance.dto;

import com.softspark.chaos.balance.model.BalanceHistory;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

/**
 * Response DTO for a {@code balance_history} row. Field names align with Phase 015's {@code
 * LedgerBalanceDto} ({@code available}/{@code total}/{@code pending}/{@code reserved}) so the UI maps
 * them with no new vocabulary (ADR-027).
 *
 * @param eventId the source envelope event id
 * @param accountId the account (= VA id)
 * @param available available balance after the mutation
 * @param pending pending balance after the mutation
 * @param reserved reserved balance after the mutation
 * @param total total balance after the mutation
 * @param totalDebits running total of debits (nullable)
 * @param totalCredits running total of credits (nullable)
 * @param lastEntrySequence per-account monotonic sequence (may be 0)
 * @param balanceAsOf zoneless snapshot timestamp
 * @param currency best-effort currency from the VA registry (nullable)
 * @param occurredAt the envelope timestamp
 * @param idempotencyKey the journal/reservation linkage hint (nullable)
 */
@RecordBuilder
public record BalanceHistoryResponse(
    String eventId,
    String accountId,
    BigDecimal available,
    BigDecimal pending,
    BigDecimal reserved,
    BigDecimal total,
    @Nullable BigDecimal totalDebits,
    @Nullable BigDecimal totalCredits,
    long lastEntrySequence,
    LocalDateTime balanceAsOf,
    @Nullable String currency,
    Instant occurredAt,
    @Nullable String idempotencyKey) {

  /**
   * Maps a {@link BalanceHistory} entity to a response DTO.
   *
   * @param entity the entity to map
   * @return the response DTO
   */
  public static BalanceHistoryResponse from(BalanceHistory entity) {
    return new BalanceHistoryResponse(
        entity.getEventId(),
        entity.getAccountId(),
        entity.getAvailableBalance(),
        entity.getPendingBalance(),
        entity.getReservedBalance(),
        entity.getTotalBalance(),
        entity.getTotalDebits(),
        entity.getTotalCredits(),
        entity.getLastEntrySequence(),
        entity.getBalanceAsOf(),
        entity.getCurrency(),
        entity.getOccurredAt(),
        entity.getIdempotencyKey());
  }
}
