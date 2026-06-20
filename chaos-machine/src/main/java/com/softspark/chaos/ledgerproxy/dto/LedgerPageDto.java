package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Generic paginated response wrapper for ledger proxy responses.
 *
 * @param <T> the type of items on the page
 * @param data the items on the current page
 * @param total total number of pages
 * @param pages total number of elements across all pages
 * @param page the current zero-based page number
 * @param pageSize the page size
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerPageDto<T>(List<T> data, int pages, long total, int page, int pageSize) {}
