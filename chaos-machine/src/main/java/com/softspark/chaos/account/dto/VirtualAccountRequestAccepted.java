package com.softspark.chaos.account.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;

/**
 * Body returned with {@code 202 Accepted} when a virtual-account creation request has been forwarded
 * to the ledger.
 *
 * <p>The VA does not exist yet — it materializes asynchronously once the ledger publishes {@code
 * ledger.account.created} and the projection consumer upserts it. Clients should poll {@code GET
 * /api/v0/virtual-accounts} until the requested account appears.
 *
 * @param status         a machine-readable status (always {@code REQUESTED})
 * @param message        a human-readable explanation of the eventual-consistency contract
 * @param accountCode     the requested account code, if any
 * @param organizationId  the owning organization id, if any
 * @param currency       the requested currency
 * @param ownershipType  the requested ownership type
 */
@RecordBuilder
public record VirtualAccountRequestAccepted(
    String status,
    String message,
    @Nullable String accountCode,
    @Nullable String organizationId,
    String currency,
    String ownershipType) {}
