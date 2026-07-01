import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { listDeadLetters } from "@/lib/api";
import { dlqDetailPath } from "@/lib/routes";
import { formatDate, formatEnumValue } from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { AlertTriangle } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

const PER_PAGE = 20;

// The coarse domain buckets the backend derives (ADR-029). Kept as a small client-side enum; the
// backend tolerates unseen values, so the list still renders any domain it returns.
const DOMAIN_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "All domains" },
  { value: "COLLECTION", label: "Collection" },
  { value: "DISBURSEMENT", label: "Disbursement" },
  { value: "BATCH_DISBURSEMENT", label: "Batch Disbursement" },
  { value: "SETTLEMENT", label: "Settlement" },
  { value: "TREASURY", label: "Treasury" },
  { value: "ORGANIZATION", label: "Organization" },
  { value: "UNKNOWN", label: "Unknown" }
];

type DlqFilterState = { domain: string; transactionId: string; transactionType: string };
const INITIAL_FILTERS: DlqFilterState = { domain: "", transactionId: "", transactionType: "" };

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

/**
 * Dead Letter Queue list — the messages the ledger rejected from the chaos machine's published
 * (often deliberately-broken) traffic. Filterable by domain / transaction id / transaction type;
 * each row links to the tabbed detail.
 */
export function DeadLetterQueuePage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<DlqFilterState>(INITIAL_FILTERS);
  const [applied, setApplied] = useState<DlqFilterState>(INITIAL_FILTERS);
  const [page, setPage] = useState(0);

  useEffect(() => {
    setPage(0);
  }, [applied.domain, applied.transactionId, applied.transactionType]);

  const query = useQuery({
    queryKey: ["dlq", { ...applied, page }],
    queryFn: () =>
      listDeadLetters(token!, {
        domain: applied.domain || undefined,
        transactionId: applied.transactionId.trim() || undefined,
        transactionType: applied.transactionType.trim() || undefined,
        page,
        size: PER_PAGE
      }),
    placeholderData: keepPreviousData
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  function applyFilters() {
    setApplied(draft);
  }

  function clearFilters() {
    setDraft(INITIAL_FILTERS);
    setApplied(INITIAL_FILTERS);
  }

  return (
    <Page>
      <PageHeader
        title="Dead Letter Queue"
        description="Messages the ledger rejected from the chaos machine's published traffic after exhausting its retries — with the error class, reason, retry count, and the exact payload that was sent."
      />
      <PageContent>
        {/* Filter bar */}
        <div className="flex flex-wrap items-center gap-2">
          <Select
            className="w-44"
            value={draft.domain}
            onChange={value => setDraft(d => ({ ...d, domain: value }))}
            options={DOMAIN_OPTIONS}
            placeholder="All domains"
          />
          <Input
            className="w-48"
            placeholder="Transaction ID"
            value={draft.transactionId}
            onChange={e => setDraft(d => ({ ...d, transactionId: e.target.value }))}
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Input
            className="w-48"
            placeholder="Transaction type"
            value={draft.transactionType}
            onChange={e => setDraft(d => ({ ...d, transactionType: e.target.value }))}
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Button size="sm" onClick={applyFilters}>
            Apply
          </Button>
          <Button size="sm" variant="ghost" onClick={clearFilters}>
            Clear
          </Button>
        </div>

        {query.isLoading ? (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>
                <TR>
                  <TH>Domain</TH>
                  <TH>Original Topic</TH>
                  <TH>Transaction</TH>
                  <TH>Error</TH>
                  <TH>Retries</TH>
                  <TH>Received</TH>
                </TR>
              </THead>
              <TBody>
                <TableLoadingRows columns={6} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : query.error ? (
          <StatePanel
            title="Failed to load dead letters"
            description={getErrorMessage(query.error)}
            tone="danger"
            icon="error"
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          />
        ) : records.length === 0 ? (
          <StatePanel
            title="No dead letters"
            description="Nothing the ledger has rejected matches the current filters."
            iconNode={<AlertTriangle className="h-5 w-5" />}
          />
        ) : (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>
                <TR>
                  <TH>Domain</TH>
                  <TH>Original Topic</TH>
                  <TH>Transaction</TH>
                  <TH>Error</TH>
                  <TH>Retries</TH>
                  <TH>Received</TH>
                </TR>
              </THead>
              <TBody>
                {records.map(record => (
                  <TR
                    key={record.id}
                    role="button"
                    tabIndex={0}
                    className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                    onClick={() => navigate(dlqDetailPath(record.id))}
                    onKeyDown={e => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(dlqDetailPath(record.id));
                      }
                    }}
                  >
                    <TD>
                      <Badge variant="neutral">{formatEnumValue(record.domain)}</Badge>
                    </TD>
                    <TD className="font-mono text-xs">{record.originalTopic}</TD>
                    <TD className="max-w-[12rem] truncate">
                      <span className="font-mono text-muted-foreground">
                        {record.transactionId ?? "—"}
                      </span>
                      {record.transactionType ? (
                        <span className="ml-1 text-[10px] uppercase tracking-wide text-muted-foreground">
                          {record.transactionType}
                        </span>
                      ) : null}
                    </TD>
                    <TD className="max-w-[16rem] truncate text-destructive">
                      {record.errorType ? (
                        <span className="font-medium">{record.errorType}</span>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                      {record.errorMessage ? (
                        <span className="ml-1 text-muted-foreground">— {record.errorMessage}</span>
                      ) : null}
                    </TD>
                    <TD>{record.retryCount ?? "—"}</TD>
                    <TD>{formatDate(record.receivedAt)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </TableContainer>
        )}

        <ListPagination
          page={page}
          total={total}
          pageSize={PER_PAGE}
          itemLabel="dead letter"
          hasNextPage={hasNextPage}
          disabled={query.isFetching}
          onPrevious={() => setPage(p => Math.max(p - 1, 0))}
          onNext={() => setPage(p => p + 1)}
        />
      </PageContent>
    </Page>
  );
}
