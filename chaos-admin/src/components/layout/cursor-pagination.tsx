import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight } from "lucide-react";

type CursorPaginationProps = {
  // Whether a previous / next page is reachable from the current position.
  hasPrevious: boolean;
  hasNext: boolean;
  // Optional human-readable label, e.g. "Showing 20 transactions".
  label?: string;
  disabled?: boolean;
  onPrevious: () => void;
  onNext: () => void;
};

/**
 * Prev/Next pager for cursor (keyset) paginated lists. Unlike {@link ListPagination} there is no
 * total count or page number — append-only streams expose only forward/back cursors.
 */
export function CursorPagination({
  hasPrevious,
  hasNext,
  label,
  disabled = false,
  onPrevious,
  onNext
}: CursorPaginationProps) {
  return (
    <div className="flex items-center justify-between gap-2 py-1">
      <p className="text-xs text-muted-foreground">{label ?? ""}</p>
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || !hasPrevious}
          onClick={onPrevious}
          className="h-8 w-8 p-0"
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || !hasNext}
          onClick={onNext}
          className="h-8 w-8 p-0"
          aria-label="Next page"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
