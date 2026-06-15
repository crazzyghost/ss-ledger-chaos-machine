package com.softspark.chaos.base;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.List;

/**
 * Standard paginated response wrapper.
 * <p>
 * Wraps a list of items with pagination metadata to support uniform pagination across all API endpoints.
 *
 * @param items   the items on the current page
 * @param page    the current page number (0-indexed)
 * @param perPage the number of items per page
 * @param total   the total number of items across all pages
 * @param <T>     the type of items being paginated
 */
@RecordBuilder
public record PageResponse<T>(
        List<T> items,
        int page,
        int perPage,
        long total
) {
    public PageResponse {
        if (items == null) {
            items = List.of();
        }
    }
}
