package com.softspark.chaos.base;

import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Helper for translating client-supplied {@code sortBy}/{@code sortDir} request parameters into a
 * Spring Data {@link Sort}, restricted to a whitelist of sortable entity attributes.
 *
 * <p>An unknown or blank {@code sortBy} falls back to the supplied default sort, so callers never
 * sort by an arbitrary (or non-existent) property.
 */
public final class SortSupport {

  private SortSupport() {}

  /**
   * Resolves a {@link Sort} from request parameters.
   *
   * @param sortBy   the requested sort attribute (entity property name); ignored if not in
   *                 {@code allowed}
   * @param sortDir  the requested direction ({@code asc}/{@code desc}, case-insensitive; default
   *                 ascending)
   * @param allowed  the set of permitted entity property names
   * @param fallback the sort applied when {@code sortBy} is blank or not permitted
   * @return the resolved sort
   */
  public static Sort resolve(String sortBy, String sortDir, Set<String> allowed, Sort fallback) {
    if (sortBy == null || sortBy.isBlank() || !allowed.contains(sortBy)) {
      return fallback;
    }
    Sort.Direction dir =
        "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(dir, sortBy);
  }
}
