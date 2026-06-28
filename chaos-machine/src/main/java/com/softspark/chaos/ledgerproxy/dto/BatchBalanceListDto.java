package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Deserialization envelope for the ledger's batch-balance response.
 *
 * <p>The ledger returns its standard paged {@code ApiResponse} shape ({@code data} +
 * {@code page}/{@code pageSize}/{@code total}/{@code pages}); only {@code data} is meaningful for a
 * keyed balance lookup, so {@link com.softspark.chaos.ledgerproxy.LedgerClient} unwraps it to a flat
 * list (ADR-021).
 *
 * @param data the per-account balance items
 * @param page the ledger page index (unused)
 * @param pageSize the ledger page size (unused)
 * @param total the ledger total count (unused)
 * @param pages the ledger page count (unused)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchBalanceListDto(
    List<BatchBalanceItemDto> data, int page, int pageSize, long total, int pages) {}
