package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO mirroring the ledger's account balance response.
 *
 * <p>The ledger's REST DTOs serialize camelCase (only its Kafka event payloads use snake_case), so
 * this record maps the ledger's {@code BalanceResponse} field-for-field by camelCase name. An
 * earlier {@code @JsonNaming(SnakeCaseStrategy)} on this record left {@code accountId} and the
 * balance timestamp silently {@code null}; it has been removed so the camelCase contract binds in
 * both directions — deserializing from the ledger and serializing to the chaos UI (Phase 015 /
 * ADR-020). The same record carries the point-in-time snapshot returned for an {@code asOf} query.
 *
 * @param accountId the account UUID
 * @param available the available (spendable) balance
 * @param total the total balance
 * @param pending the pending balance
 * @param reserved the reserved balance
 * @param currency the ISO-4217 currency code
 * @param lastEntrySequence the account's last applied entry sequence at this snapshot
 * @param balanceAsOf the instant the snapshot reflects (the current time, or the requested {@code
 *     asOf} point-in-time)
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerBalanceDto(
    String accountId,
    BigDecimal available,
    BigDecimal total,
    BigDecimal pending,
    BigDecimal reserved,
    String currency,
    long lastEntrySequence,
    LocalDateTime balanceAsOf) {}
