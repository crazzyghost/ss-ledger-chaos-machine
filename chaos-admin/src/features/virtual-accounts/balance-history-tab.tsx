import { ListPagination } from "@/components/layout/list-pagination";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Button } from "@/components/ui/button";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { getBalanceHistory } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

/**
 * The per-account stream of `ledger.balance.updated` events (Phase 018) — an observational, append-
 * only event log, distinct from the live Ledger Balance panel on the Overview tab (Phase 015), which
 * remains the authority for the current balance.
 */
export function BalanceHistoryTab({ vaId, currency }: { vaId: string; currency?: string }) {
  const { token } = useSession();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["balance-history", vaId, { page }],
    queryFn: () => getBalanceHistory(token!, vaId, { page, size: PER_PAGE }),
    placeholderData: keepPreviousData,
    retry: false
  });

  const rows = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  const headerRow = (
    <TR>
      <TH>When</TH>
      <TH>Seq</TH>
      <TH className="text-right">Total</TH>
      <TH className="text-right">Available</TH>
      <TH className="text-right">Reserved</TH>
      <TH className="text-right">Pending</TH>
    </TR>
  );

  return (
    <div className="space-y-3">
      <p className="text-[11px] text-muted-foreground">
        The stream of{" "}
        <code className="rounded bg-muted px-1 py-0.5">ledger.balance.updated</code> events for this
        account (one row per posting) — an observational event log, distinct from the live Ledger
        Balance panel on the Overview tab.
      </p>

      {query.isLoading ? (
        <TableContainer className="rounded-lg border border-border bg-card">
          <Table>
            <THead>{headerRow}</THead>
            <TBody>
              <TableLoadingRows columns={6} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load balance history"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : rows.length === 0 ? (
        <StatePanel
          title="No balance history"
          description="No ledger.balance.updated events have been recorded for this account yet."
        />
      ) : (
        <TableContainer className="rounded-lg border border-border bg-card">
          <Table>
            <THead>{headerRow}</THead>
            <TBody>
              {rows.map(row => {
                const curr = row.currency ?? currency;
                return (
                  <TR key={row.eventId}>
                    <TD className="whitespace-nowrap">
                      {formatDate(row.balanceAsOf || row.occurredAt)}
                    </TD>
                    <TD className="tabular-nums text-muted-foreground">{row.lastEntrySequence}</TD>
                    <TD className="text-right tabular-nums">{formatMoney(row.total, curr)}</TD>
                    <TD className="text-right tabular-nums">{formatMoney(row.available, curr)}</TD>
                    <TD className="text-right tabular-nums">{formatMoney(row.reserved, curr)}</TD>
                    <TD className="text-right tabular-nums">{formatMoney(row.pending, curr)}</TD>
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
        itemLabel="event"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />
    </div>
  );
}
