import type { SortDirection } from "@/lib/api";
import { useEffect, useState } from "react";

export type SortState = { by: string; dir: SortDirection } | null;

/**
 * Debounces a value, emitting the latest after `delayMs` of quiet.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}

/**
 * Shared state for a paginated list with a debounced search box and tri-state column sorting.
 *
 * <p>Clicking a column header cycles asc → desc → unsorted. Changing the search term or sort resets
 * to the first page.
 */
export function useListControls(initialSort: SortState = null) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search, 300);
  const [sort, setSort] = useState<SortState>(initialSort);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sort]);

  function toggleSort(field: string) {
    setSort((prev) => {
      if (!prev || prev.by !== field) return { by: field, dir: "asc" };
      if (prev.dir === "asc") return { by: field, dir: "desc" };
      return null;
    });
  }

  return {
    page,
    setPage,
    search,
    setSearch,
    debouncedSearch,
    sort,
    toggleSort,
    sortBy: sort?.by,
    sortDir: sort?.dir
  };
}
