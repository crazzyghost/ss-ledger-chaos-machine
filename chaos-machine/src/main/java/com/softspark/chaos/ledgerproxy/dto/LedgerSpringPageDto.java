package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Page wrapper for ledger endpoints that return a <em>raw</em> Spring {@code Page} (legacy DIRECT
 * serialization): items under {@code content}, totals as {@code totalElements}/{@code totalPages},
 * position as {@code number}/{@code size}.
 *
 * <p>Distinct from {@link LedgerPageDto} ({@code data}/{@code pages}/{@code total}/{@code page}/
 * {@code pageSize}), which mirrors the ledger's hand-rolled {@code ApiResponse} envelope. The
 * reservations endpoint is the one read that returns a raw {@code Page}, so it needs this shape.
 *
 * @param <T> the type of items on the page
 * @param content the items on the current page
 * @param totalElements total number of elements across all pages
 * @param totalPages total number of pages
 * @param number the current zero-based page number
 * @param size the page size
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerSpringPageDto<T>(
    List<T> content, long totalElements, int totalPages, int number, int size) {}
