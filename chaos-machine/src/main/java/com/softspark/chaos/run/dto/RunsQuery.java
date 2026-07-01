package com.softspark.chaos.run.dto;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Filter/request parameters for the run-grouped feed ({@code GET /api/v0/runs}).
 *
 * <p>Mirrors the chaos pagination contract: {@code page} is floored at 0 and {@code size} is clamped
 * to {@code [1, 100]} in the compact constructor (the controller supplies the default of 20). The
 * {@code from}/{@code to} bounds filter on a run's last activity; {@code kind} filters on the run's
 * kind label (case-insensitive). Blank/unknown filters are ignored by the service.
 *
 * @param from optional inclusive lower bound on last activity
 * @param to optional inclusive upper bound on last activity
 * @param kind optional run-kind filter (a {@code RunKind} name, {@code "SINGLE"}, or
 *     {@code "MANUAL_SEQUENCE"})
 * @param page zero-based page index
 * @param size page size (clamped to {@code [1, 100]})
 */
public record RunsQuery(
    @Nullable Instant from, @Nullable Instant to, @Nullable String kind, int page, int size) {
  public RunsQuery {
    if (page < 0) {
      page = 0;
    }
    size = Math.min(Math.max(size, 1), 100);
  }
}
