package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * DTO mirroring the ledger's account balance response.
 *
 * @param accountId the account UUID
 * @param balance the current ledger balance
 * @param availableBalance the available (spendable) balance
 * @param currency the ISO-4217 currency code
 * @param updatedAt ISO-8601 timestamp of the last balance update
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerBalanceDto(
    String accountId,
    BigDecimal balance,
    BigDecimal availableBalance,
    String currency,
    String updatedAt) {}
