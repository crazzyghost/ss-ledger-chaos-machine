import { TH } from "@/components/ui/table";
import type { SortState } from "@/lib/use-list-controls";
import { cn } from "@/lib/utils";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";

/**
 * A sortable table header cell. Clicking cycles the column through asc → desc → unsorted via the
 * supplied {@link onSort} callback (typically `useListControls().toggleSort`).
 */
export function SortableTH({
  label,
  field,
  sort,
  onSort,
  className
}: {
  label: string;
  field: string;
  sort: SortState;
  onSort: (field: string) => void;
  className?: string;
}) {
  const active = sort?.by === field;
  const dir = active ? sort?.dir : null;

  return (
    <TH className={className}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className={cn(
          "inline-flex items-center gap-1 uppercase tracking-wider transition-colors hover:text-foreground",
          active ? "text-foreground" : "text-muted-foreground"
        )}
      >
        {label}
        {dir === "asc" ? (
          <ArrowUp className="h-3 w-3" />
        ) : dir === "desc" ? (
          <ArrowDown className="h-3 w-3" />
        ) : (
          <ArrowUpDown className="h-3 w-3 opacity-40" />
        )}
      </button>
    </TH>
  );
}
