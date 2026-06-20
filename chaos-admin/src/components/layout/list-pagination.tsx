import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight } from "lucide-react";

type ListPaginationProps = {
  page: number;
  total: number;
  pageSize: number;
  itemLabel: string;
  hasNextPage: boolean;
  disabled?: boolean;
  onPrevious: () => void;
  onNext: () => void;
};

export function ListPagination({
  page,
  total,
  pageSize,
  itemLabel,
  hasNextPage,
  disabled = false,
  onPrevious,
  onNext
}: ListPaginationProps) {
  if (total === 0) {
    return null;
  }

  const totalPages = total > 0 ? Math.ceil(total / pageSize) : 0;
  const rangeStart = total === 0 ? 0 : page * pageSize + 1;
  const rangeEnd = total === 0 ? 0 : Math.min((page + 1) * pageSize, total);
  const pluralizedLabel = total === 1 ? itemLabel : `${itemLabel}s`;

  return (
    <div className="flex items-center justify-between gap-2 py-1">
      <p className="text-xs text-muted-foreground">
        {total === 0
          ? `No ${pluralizedLabel} found`
          : `${rangeStart}–${rangeEnd} of ${total} ${pluralizedLabel}`}
      </p>
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || page === 0}
          onClick={onPrevious}
          className="h-8 w-8 p-0"
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span className="px-2 text-xs text-muted-foreground">
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || !hasNextPage}
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
