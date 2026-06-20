import { Select } from "@/components/ui/select";
import { useDebouncedValue } from "@/lib/use-list-controls";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

export type AsyncOption<T = unknown> = { value: string; label: string; data?: T };

/**
 * A searchable dropdown that queries the server as the user types, rather than filtering a single
 * pre-loaded page client-side. This avoids the "only the first page is searchable" problem for large
 * reference sets (e.g. 250 countries) where the backend caps a single page well below the full size.
 *
 * <p>The currently-selected option is pinned to the top of the list (using {@link selectedLabel}) so
 * the trigger keeps showing its label even when it falls outside the active search results.
 */
export function AsyncSearchSelect<T = unknown>({
  value,
  selectedLabel,
  onChange,
  queryKey,
  fetchOptions,
  enabled = true,
  placeholder = "Select…",
  searchPlaceholder = "Search…",
  emptyText = "No results found.",
  className
}: {
  value: string;
  selectedLabel?: string;
  onChange: (value: string, option?: AsyncOption<T>) => void;
  queryKey: readonly unknown[];
  fetchOptions: (search: string) => Promise<AsyncOption<T>[]>;
  enabled?: boolean;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  className?: string;
}) {
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search, 300);

  const query = useQuery({
    queryKey: [...queryKey, debounced],
    queryFn: () => fetchOptions(debounced),
    enabled,
    placeholderData: keepPreviousData
  });

  let options: AsyncOption<T>[] = query.data ?? [];
  if (value && !options.some((o) => o.value === value)) {
    options = [{ value, label: selectedLabel ?? value }, ...options];
  }

  return (
    <Select
      value={value}
      onChange={(v) => onChange(v, options.find((o) => o.value === v))}
      options={options}
      placeholder={query.isLoading ? "Loading…" : placeholder}
      searchable
      searchValue={search}
      onSearchChange={setSearch}
      searchPlaceholder={searchPlaceholder}
      emptyText={emptyText}
      className={className}
    />
  );
}
