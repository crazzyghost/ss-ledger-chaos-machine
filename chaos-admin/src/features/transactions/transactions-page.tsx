import { JsonPanel } from "@/components/layout/json-panel";
import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import {
  listSentHistory,
  listLedgerTransactions,
  type HistoryFilters,
  type LedgerTransactionFilters,
  type PublishRecordResponse,
  type LedgerTransactionDto
} from "@/lib/api";
import { formatDate, formatEnumValue, formatMoney, getStatusBadgeVariant } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { FileText, Wallet } from "lucide-react";
import { useEffect, useState } from "react";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

function isLedgerProxyUnavailable(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }
  const status = (error as { status?: number }).status;
  return status === 503 || (status === 500 && error.message.toLowerCase().includes("temporarily unavailable"));
}

// ---------------------------------------------------------------------------
// History Detail Dialog
// ---------------------------------------------------------------------------

function HistoryDetailDialog({
  record,
  open,
  onOpenChange
}: {
  record: PublishRecordResponse | null;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  if (!record) return null;

  let parsedPayload: Record<string, unknown> | string | null = null;
  if (record.payloadJson) {
    try {
      parsedPayload = JSON.parse(record.payloadJson) as Record<string, unknown>;
    } catch {
      parsedPayload = record.payloadJson;
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Event — {record.eventType}</DialogTitle>
          <DialogDescription>
            {record.id} · {formatDate(record.createdAt)}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-3">
            {[
              { label: "Event ID", value: record.eventId, mono: true },
              { label: "Topic", value: record.topic, mono: true },
              { label: "Source", value: record.source },
              { label: "Status", value: record.status, badge: true },
              { label: "Correlation ID", value: record.correlationId ?? "—", mono: true },
              { label: "Source VA", value: record.sourceVaId ?? "—", mono: true },
              { label: "Destination VA", value: record.destinationVaId ?? "—", mono: true },
              { label: "Partition", value: record.kafkaPartition?.toString() ?? "—" },
              { label: "Offset", value: record.kafkaOffset?.toString() ?? "—" }
            ].map(f => (
              <div key={f.label}>
                <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
                  {f.label}
                </dt>
                <dd className="mt-0.5 text-xs">
                  {f.badge ? (
                    <Badge variant={getStatusBadgeVariant(f.value as string)}>
                      {formatEnumValue(f.value as string)}
                    </Badge>
                  ) : f.mono ? (
                    <span className="font-mono">{f.value}</span>
                  ) : (
                    String(f.value)
                  )}
                </dd>
              </div>
            ))}
          </dl>
          {record.chaosStrategy && (
            <InlineNotice
              title={`Chaos: ${record.chaosStrategy}`}
              description={`This was an intentional ${record.intentionalFailure ? "failure" : "chaos"} event.`}
              tone="warning"
            />
          )}
          {parsedPayload && (
            <JsonPanel
              title="Event Payload"
              description="The serialized event envelope sent to Kafka."
              value={parsedPayload}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Sent History Tab
// ---------------------------------------------------------------------------

function SentHistoryTab({ filters }: { filters: HistoryFilters }) {
  const { token } = useSession();
  const [page, setPage] = useState(0);
  const [selectedRecord, setSelectedRecord] = useState<PublishRecordResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  useEffect(() => {
    setPage(0);
  }, [filters.vaId, filters.eventType, filters.correlationId, filters.status, filters.from, filters.to]);

  const query = useQuery({
    queryKey: ["history", { ...filters, page }],
    queryFn: () => listSentHistory(token!, { ...filters, page, size: PER_PAGE })
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  function openDetail(record: PublishRecordResponse) {
    setSelectedRecord(record);
    setDetailOpen(true);
  }

  return (
    <>
      {query.isLoading ? (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Event Type</TH>
                <TH>Source VA</TH>
                <TH>Destination VA</TH>
                <TH>Status</TH>
                <TH>Chaos</TH>
                <TH>Correlation ID</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              <TableLoadingRows columns={7} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load history"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : records.length === 0 ? (
        <StatePanel
          title="No sent events"
          description="No publish history found for the current filters."
          iconNode={<FileText className="h-5 w-5" />}
        />
      ) : (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Event Type</TH>
                <TH>Source VA</TH>
                <TH>Destination VA</TH>
                <TH>Status</TH>
                <TH>Chaos</TH>
                <TH>Correlation ID</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              {records.map(record => (
                <TR
                  key={record.id}
                  role="button"
                  tabIndex={0}
                  className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                  onClick={() => openDetail(record)}
                  onKeyDown={e => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      openDetail(record);
                    }
                  }}
                >
                  <TD className="font-mono text-xs">{record.eventType}</TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.sourceVaId ?? "—"}
                  </TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.destinationVaId ?? "—"}
                  </TD>
                  <TD>
                    <Badge variant={getStatusBadgeVariant(record.status)}>
                      {formatEnumValue(record.status)}
                    </Badge>
                  </TD>
                  <TD>
                    {record.chaosStrategy ? (
                      <Badge variant="warning">{record.chaosStrategy}</Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.correlationId ?? "—"}
                  </TD>
                  <TD>{formatDate(record.createdAt)}</TD>
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
        itemLabel="event"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />

      <HistoryDetailDialog
        record={selectedRecord}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// Ledger Transactions Tab
// ---------------------------------------------------------------------------

function LedgerTransactionsTab({ filters }: { filters: LedgerTransactionFilters }) {
  const { token } = useSession();
  const [page, setPage] = useState(0);
  const [selectedRecord, setSelectedRecord] = useState<LedgerTransactionDto | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  useEffect(() => {
    setPage(0);
  }, [filters.vaId, filters.eventType, filters.correlationId, filters.status, filters.from, filters.to]);

  const query = useQuery({
    queryKey: ["ledger-transactions", { ...filters, page }],
    queryFn: () => listLedgerTransactions(token!, { ...filters, page, size: PER_PAGE }),
    retry: false
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  const is503 = isLedgerProxyUnavailable(query.error);

  return (
    <>
      {query.isLoading ? (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Transaction ID</TH>
                <TH>Event Type</TH>
                <TH>Amount</TH>
                <TH>Currency</TH>
                <TH>Status</TH>
                <TH>Source VA</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              <TableLoadingRows columns={7} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title={is503 ? "Ledger proxy degraded" : "Failed to load ledger transactions"}
          description={
            is503
              ? "The ledger service is currently unavailable. Sent history is still accessible on the Sent tab."
              : getErrorMessage(query.error)
          }
          tone={is503 ? "warning" : "danger"}
          icon={is503 ? "access" : "error"}
          action={
            !is503 ? (
              <Button onClick={() => void query.refetch()}>Retry</Button>
            ) : undefined
          }
        />
      ) : records.length === 0 ? (
        <StatePanel
          title="No ledger transactions found"
          description="No transactions match the current filters."
          iconNode={<Wallet className="h-5 w-5" />}
        />
      ) : (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Transaction ID</TH>
                <TH>Event Type</TH>
                <TH>Amount</TH>
                <TH>Currency</TH>
                <TH>Status</TH>
                <TH>Source VA</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              {records.map(tx => (
                <TR
                  key={tx.transaction_id}
                  role="button"
                  tabIndex={0}
                  className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                  onClick={() => {
                    setSelectedRecord(tx);
                    setDetailOpen(true);
                  }}
                  onKeyDown={event => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setSelectedRecord(tx);
                      setDetailOpen(true);
                    }
                  }}
                >
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {tx.transaction_id}
                  </TD>
                  <TD className="font-mono text-xs">{tx.event_type ?? "—"}</TD>
                  <TD className="tabular-nums">
                    {tx.amount !== null && tx.amount !== undefined
                      ? formatMoney(tx.amount, tx.currency ?? "GHS")
                      : "—"}
                  </TD>
                  <TD>{tx.currency ?? "—"}</TD>
                  <TD>
                    {tx.status ? (
                      <Badge variant={getStatusBadgeVariant(tx.status)}>
                        {formatEnumValue(tx.status)}
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {tx.source_va_id ?? "—"}
                  </TD>
                  <TD>{tx.created_at ? formatDate(tx.created_at) : "—"}</TD>
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
        itemLabel="transaction"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Ledger transaction</DialogTitle>
            <DialogDescription>
              {selectedRecord?.transaction_id ?? "—"}
            </DialogDescription>
          </DialogHeader>
          {selectedRecord ? (
            <JsonPanel
              title="Transaction payload"
              description="Current proxied ledger transaction fields."
              value={selectedRecord}
            />
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}

// ---------------------------------------------------------------------------
// Shared filter state (used by standalone page + per-VA tab)
// ---------------------------------------------------------------------------

type TransactionFilters = {
  vaId: string;
  eventType: string;
  correlationId: string;
  status: string;
  from: string;
  to: string;
};

const INITIAL: TransactionFilters = {
  vaId: "",
  eventType: "",
  correlationId: "",
  status: "",
  from: "",
  to: ""
};

function toISOFrom(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T00:00:00.000Z`;
}

function toISOTo(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T23:59:59.999Z`;
}

// ---------------------------------------------------------------------------
// Reusable tab content (embeddable from VA detail page)
// ---------------------------------------------------------------------------

export function TransactionsTab({ lockedVaId }: { lockedVaId?: string }) {
  const [draft, setDraft] = useState<TransactionFilters>({
    ...INITIAL,
    vaId: lockedVaId ?? ""
  });
  const [applied, setApplied] = useState<TransactionFilters>({
    ...INITIAL,
    vaId: lockedVaId ?? ""
  });

  function applyFilters() {
    setApplied(draft);
  }

  function clearFilters() {
    const reset = { ...INITIAL, vaId: lockedVaId ?? "" };
    setDraft(reset);
    setApplied(reset);
  }

  const sentFilters: HistoryFilters = {
    vaId: applied.vaId || undefined,
    eventType: applied.eventType || undefined,
    correlationId: applied.correlationId || undefined,
    status: applied.status || undefined,
    from: toISOFrom(applied.from),
    to: toISOTo(applied.to)
  };

  const ledgerFilters: LedgerTransactionFilters = {
    vaId: applied.vaId || undefined,
    eventType: applied.eventType || undefined,
    correlationId: applied.correlationId || undefined,
    status: applied.status || undefined,
    from: toISOFrom(applied.from),
    to: toISOTo(applied.to)
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Filter bar */}
      <div className="border-b border-border bg-muted/30 px-6 py-3 md:px-8">
        <div className="flex flex-wrap gap-2">
          {!lockedVaId && (
            <Input
              className="w-full md:max-w-xs"
              value={draft.vaId}
              onChange={e => setDraft(d => ({ ...d, vaId: e.target.value }))}
              placeholder="Virtual Account ID"
              onKeyDown={e => e.key === "Enter" && applyFilters()}
            />
          )}
          <Input
            className="w-40"
            value={draft.eventType}
            onChange={e => setDraft(d => ({ ...d, eventType: e.target.value }))}
            placeholder="Event type"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Input
            className="w-40"
            value={draft.correlationId}
            onChange={e => setDraft(d => ({ ...d, correlationId: e.target.value }))}
            placeholder="Correlation ID"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Input
            className="w-28"
            value={draft.status}
            onChange={e => setDraft(d => ({ ...d, status: e.target.value }))}
            placeholder="Status"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <div className="flex items-center gap-1">
            <span className="text-xs text-muted-foreground">From</span>
            <Input
              type="date"
              className="w-36"
              value={draft.from}
              onChange={e => setDraft(d => ({ ...d, from: e.target.value }))}
            />
          </div>
          <div className="flex items-center gap-1">
            <span className="text-xs text-muted-foreground">To</span>
            <Input
              type="date"
              className="w-36"
              value={draft.to}
              onChange={e => setDraft(d => ({ ...d, to: e.target.value }))}
            />
          </div>
          <Button variant="outline" size="sm" onClick={applyFilters}>
            Apply
          </Button>
          <Button variant="ghost" size="sm" onClick={clearFilters}>
            Clear
          </Button>
        </div>
      </div>

      {/* Source tabs */}
      <Tabs defaultValue="sent" className="flex min-h-0 flex-1 flex-col">
        <TabsList>
          <TabsTrigger value="sent">Sent (Chaos History)</TabsTrigger>
          <TabsTrigger value="ledger">Ledger</TabsTrigger>
        </TabsList>
        <TabsContent value="sent" className="space-y-4 p-6 md:p-8">
          <SentHistoryTab filters={sentFilters} />
        </TabsContent>
        <TabsContent value="ledger" className="space-y-4 p-6 md:p-8">
          <LedgerTransactionsTab filters={ledgerFilters} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Standalone Transactions Page
// ---------------------------------------------------------------------------

export function TransactionsPage() {
  return (
    <Page>
      <PageHeader
        title="Transactions"
        description="Browse chaos-sent events and ledger-recorded transactions, filterable by VA, type, correlation ID, and date."
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <TransactionsTab />
      </PageContent>
    </Page>
  );
}
