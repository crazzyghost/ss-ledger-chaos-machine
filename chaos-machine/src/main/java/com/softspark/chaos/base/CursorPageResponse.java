package com.softspark.chaos.base;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Standard cursor-paginated response wrapper.
 *
 * <p>The cursor counterpart to {@link PageResponse}: used for endpoints that proxy append-only
 * ledger streams keyset-paginated by opaque cursors. Unlike {@link PageResponse} it carries no
 * {@code page}/{@code total} metadata — only the items, the cursors needed to walk forward and
 * back, and whether more rows remain.
 *
 * @param items the items on the current page
 * @param nextCursor opaque cursor for the next page, or {@code null} when {@code hasMore} is false
 * @param previousCursor opaque cursor for the previous page, or {@code null} on the first page
 * @param hasMore whether more items exist after this page
 * @param size the requested page size echoed back from the upstream source
 * @param <T> the type of items being paginated
 */
public record CursorPageResponse<T>(
    List<T> items,
    @Nullable String nextCursor,
    @Nullable String previousCursor,
    boolean hasMore,
    int size) {
  public CursorPageResponse {
    if (items == null) {
      items = List.of();
    }
  }
}
