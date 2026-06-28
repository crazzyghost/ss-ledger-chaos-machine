import { ListPagination } from "@/components/layout/list-pagination";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { getVaReservations } from "@/lib/api";
import { formatDate, formatEnumValue, formatMoney } from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

const PER_PAGE = 20;

const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "ACTIVE", label: "Active" },
  { value: "PARTIALLY_RESOLVED", label: "Partially Resolved" },
  { value: "CAPTURED", label: "Captured" },
  { value: "RELEASED", label: "Released" },
  { value: "EXPIRED", label: "Expired" }
] as const;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// Color-codes a reservation status: in-flight (ACTIVE / PARTIALLY_RESOLVED) reads distinctly from
// the terminal states (CAPTURED success-ish, RELEASED / EXPIRED neutral).
function reservationStatusVariant(status: string | null | undefined): BadgeVariant {
  switch (status?.toUpperCase()) {
    case "ACTIVE":
      return "success";
    case "PARTIALLY_RESOLVED":
      return "warning";
    case "CAPTURED":
      return "secondary";
    case "RELEASED":
    case "EXPIRED":
      return "neutral";
    default:
      return "secondary";
  }
}

/**
 * The per-account reservation lifecycle state consumed from `ledger.reservation.*` (Phase 019) —
 * push-fed and event-faithful, distinct from the read-proxy reservation lookups the wizards use
 * (which carry richer captured/released amounts + expiry).
 */
export function ReservationsTab({ vaId, currency }: { vaId: string; currency?: string }) {
  const { token } = useSession();
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["va-reservations", vaId, { status, page }],
    queryFn: () => getVaReservations(token!, vaId, { status: status || undefined, page, size: PER_PAGE }),
    placeholderData: keepPreviousData,
    retry: false
  });

  const rows = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  function onStatusChange(next: string) {
    setStatus(next);
    setPage(0);
  }

  const headerRow = (
    <TR>
      <TH>Reservation</TH>
      <TH>Type</TH>
      <TH>Status</TH>
      <TH className="text-right">Amount</TH>
      <TH>Transaction</TH>
      <TH>Batch</TH>
      <TH>Updated</TH>
    </TR>
  );

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[11px] text-muted-foreground">
          Consumed{" "}
          <code className="rounded bg-muted px-1 py-0.5">ledger.reservation.*</code> lifecycle state
          (push-fed) — distinct from the read-proxy reservation lookups the wizards use.
        </p>
        <Select
          value={status}
          onChange={onStatusChange}
          options={STATUS_OPTIONS}
          className="h-8 w-44"
        />
      </div>

      {query.isLoading ? (
        <TableContainer className="rounded-lg border border-border bg-card">
          <Table>
            <THead>{headerRow}</THead>
            <TBody>
              <TableLoadingRows columns={7} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load reservations"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : rows.length === 0 ? (
        <StatePanel
          title="No reservations"
          description={
            status
              ? "No reservations match the selected status."
              : "No ledger.reservation.* events have been recorded for this account yet."
          }
        />
      ) : (
        <TableContainer className="rounded-lg border border-border bg-card">
          <Table>
            <THead>{headerRow}</THead>
            <TBody>
              {rows.map(row => {
                const curr = row.currency ?? currency;
                const isBatch = row.reservationType?.toUpperCase() === "BATCH";
                return (
                  <TR key={row.reservationId}>
                    <TD className="font-mono text-[11px]">{row.reservationId}</TD>
                    <TD>
                      <Badge variant={isBatch ? "warning" : "secondary"}>
                        {formatEnumValue(row.reservationType)}
                      </Badge>
                    </TD>
                    <TD>
                      <Badge variant={reservationStatusVariant(row.status)}>
                        {formatEnumValue(row.status)}
                      </Badge>
                    </TD>
                    <TD className="text-right tabular-nums">{formatMoney(row.amount, curr)}</TD>
                    <TD className="font-mono text-[11px] text-muted-foreground">
                      {row.transactionId}
                    </TD>
                    <TD className="font-mono text-[11px] text-muted-foreground">
                      {row.disbursementBatchId ?? "—"}
                    </TD>
                    <TD className="whitespace-nowrap">{formatDate(row.updatedAt)}</TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        </TableContainer>
      )}

      <ListPagination
        page={page}
        total={total}
        pageSize={PER_PAGE}
        itemLabel="reservation"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />
    </div>
  );
}
