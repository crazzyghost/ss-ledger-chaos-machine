package com.softspark.chaos.account.consumer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Chaos-side mirror of the ledger's {@code account/events/v1/AccountCreatedEventData} payload.
 *
 * <p>This record is the contract oracle for deserializing the {@code data} block of a {@code
 * ledger.account.created} event. The ledger publishes with {@code spring.json.add.type.headers=false}
 * and {@code @JsonNaming(SnakeCaseStrategy)}, so field names arrive in {@code snake_case}; the same
 * naming strategy is applied here. UUID-typed ledger fields ({@code account_id}, {@code
 * organization_id}) are carried as {@link String} since the chaos registry keys virtual accounts by
 * string id.
 *
 * <p>Every field the ledger emits is mirrored so the projection can map the full record and a
 * contract test can fail the build on drift.
 *
 * @param accountId            ledger-assigned account id (becomes the chaos {@code va_id})
 * @param accountCode          dot-separated hierarchical code
 * @param accountName          human-readable display name
 * @param accountCategory      account category (ASSET, LIABILITY, …)
 * @param normalBalance        normal balance side (DEBIT / CREDIT) — informational
 * @param currency             ISO-4217 currency code
 * @param status               account status (ACTIVE, …)
 * @param organizationId       owning organization id, or {@code null} for SYSTEM accounts
 * @param overdraftLimit       optional overdraft limit
 * @param minimumBalance       optional minimum balance floor
 * @param createdAt            ledger creation timestamp — informational
 * @param updatedAt            ledger update timestamp — informational
 * @param accountOwnershipType SYSTEM or ORGANIZATION
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LedgerAccountCreatedEventData(
    String accountId,
    String accountCode,
    String accountName,
    String accountCategory,
    String normalBalance,
    String currency,
    String status,
    String organizationId,
    BigDecimal overdraftLimit,
    BigDecimal minimumBalance,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String accountOwnershipType) {}
