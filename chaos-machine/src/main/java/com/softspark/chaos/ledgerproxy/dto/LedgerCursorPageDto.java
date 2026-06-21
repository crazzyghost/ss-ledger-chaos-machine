package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Generic cursor-paginated response wrapper mirroring the ledger's {@code CursorApiResponse}
 * envelope.
 *
 * <p>Unlike {@link LedgerPageDto}, cursor responses intentionally omit {@code page}, {@code pages}
 * and {@code total} — the ledger walks append-only history streams via opaque keyset cursors rather
 * than computing exact counts.
 *
 * @param <T> the type of items on the page
 * @param data the records on the current page
 * @param nextCursor opaque cursor that fetches the next page, or {@code null} when {@code hasMore}
 *     is {@code false}
 * @param previousCursor opaque cursor that fetches the previous page, or {@code null} on the first
 *     page
 * @param hasMore whether more rows exist after this page
 * @param size the requested page size echoed back by the ledger
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerCursorPageDto<T>(
    List<T> data,
    @Nullable String nextCursor,
    @Nullable String previousCursor,
    boolean hasMore,
    int size) {}
