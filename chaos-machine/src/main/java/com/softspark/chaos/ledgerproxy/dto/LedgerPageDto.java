package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Generic paginated response wrapper for ledger proxy responses.
 *
 * @param <T> the type of items on the page
 * @param content the items on the current page
 * @param totalPages total number of pages
 * @param totalElements total number of elements across all pages
 * @param number the current zero-based page number
 * @param size the page size
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerPageDto<T>(
    List<T> content, int totalPages, long totalElements, int number, int size) {}
