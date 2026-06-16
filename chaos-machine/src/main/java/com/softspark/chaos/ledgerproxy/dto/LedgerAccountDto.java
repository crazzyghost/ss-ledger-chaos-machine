package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;

/**
 * DTO mirroring the ledger's account response shape.
 *
 * @param accountId the ledger-assigned account UUID
 * @param accountCode the hierarchical account code
 * @param accountName the display name
 * @param accountCategory the category (ASSET, REVENUE, etc.)
 * @param normalBalance the normal balance side (DEBIT / CREDIT)
 * @param currency the ISO-4217 currency code
 * @param status the account status
 * @param accountOwnershipType SYSTEM or ORGANIZATION
 * @param organizationId owning org id (null for SYSTEM accounts)
 * @param parentAccountId parent account id (null for root accounts)
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerAccountDto(
    String accountId,
    String accountCode,
    String accountName,
    String accountCategory,
    String normalBalance,
    String currency,
    String status,
    String accountOwnershipType,
    @Nullable String organizationId,
    @Nullable String parentAccountId) {}
