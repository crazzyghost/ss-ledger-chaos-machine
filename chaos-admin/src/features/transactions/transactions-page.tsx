import { CursorPagination } from "@/components/layout/cursor-pagination";
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
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import {
  getAccountTransactionHistory,
  getLedgerAccount,
  listSentHistory,
  listLedgerTransactions,
  listTransactionFailuresByRequestIds,
  type HistoryFilters,
  type LedgerTransactionFilters,
  type LedgerTransactionHistoryRecord,
  type PublishRecordResponse,
  type LedgerTransactionDto,
  type TransactionFailureResponse
} from "@/lib/api";
import {
  formatDate,
  formatEnumValue,
  formatMoney,
  getDirectionVariant,
  getEntryTypeVariant,
  getStatusBadgeVariant
} from "@/lib/utils";
import { usePersistedTabs } from "@/lib/use-persisted-tabs";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { FileText, Wallet } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

// The ledger Outcome of a published row (Phase 017): distinct from the publish-to-Kafka Status.
// "No failure" frames the absence honestly (failures are asynchronous — never an affirmative success).
function OutcomeCell({
  record,
  failureMap,
  loading
}: {
  record: PublishRecordResponse;
  failureMap: Map<string, TransactionFailureResponse>;
  loading: boolean;
}) {
  if (!record.transactionRequestId) {
    return (
      <span className="text-muted-foreground" title="Non-transactional flow — no ledger outcome">
        —
      </span>
    );
  }
  const failure = failureMap.get(record.transactionRequestId);
  if (failure) {
    return (
      <Badge variant="destructive" title={failure.failureReason ?? "Rejected by the ledger"}>
        Failed @ ledger{failure.failureCode ? ` · ${failure.failureCode}` : ""}
      </Badge>
    );
  }
  // Don't assert "no failure" until the per-page lookup has resolved — avoid transiently
  // mislabelling a genuinely-failed row.
  if (loading) {
    return (
      <span className="text-muted-foreground" title="Checking ledger outcome…">
        …
      </span>
    );
  }
  return (
    <Badge
      variant="neutral"
      title="Ledger acceptance — no failure observed (asynchronous; not a success guarantee)"
    >
      No failure
    </Badge>
  );
}

// The ledger's EntryTypeEnum — used to populate the transaction-history entry-type filter.
const ENTRY_TYPE_OPTIONS = [
  { value: "", label: "All types" },
  { value: "COLLECTION", label: "Collection" },
  { value: "DISBURSEMENT", label: "Disbursement" },
  { value: "TOPUP", label: "Topup" },
  { value: "TRANSFER", label: "Transfer" },
  { value: "INTER_VA_TRANSFER", label: "Inter-VA Transfer" },
  { value: "SETTLEMENT", label: "Settlement" },
  { value: "FEE", label: "Fee" },
  { value: "REVERSAL", label: "Reversal" },
  { value: "ADJUSTMENT", label: "Adjustment" },
  { value: "TREASURY_PREFUND", label: "Treasury Prefund" },
  { value: "TREASURY_SWEEP", label: "Treasury Sweep" },
  { value: "TREASURY_TRANSFER", label: "Treasury Transfer" }
] as const;

const DIRECTION_OPTIONS = [
  { value: "", label: "All directions" },
  { value: "DEBIT", label: "Debit" },
  { value: "CREDIT", label: "Credit" }
] as const;

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
  failure,
  open,
  onOpenChange
}: {
  record: PublishRecordResponse | null;
  failure?: TransactionFailureResponse | null;
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
          {failure && (
            <InlineNotice
              title={`Failed at ledger${failure.failureCode ? `: ${failure.failureCode}` : ""}`}
              description={`${failure.failureReason ?? "Rejected by the ledger"} · recording ${failure.ledgerTransactionId} · ${formatDate(failure.occurredAt)}`}
              tone="danger"
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

  // One batch failures lookup per page (Phase 017): collect the page's request ids and resolve their
  // ledger outcome in a single call, building a requestId → failure map.
  const requestIds = useMemo(
    () =>
      Array.from(
        new Set(records.map(r => r.transactionRequestId).filter((id): id is string => Boolean(id)))
      ),
    [records]
  );
  const failuresQuery = useQuery({
    queryKey: ["transaction-failures", "by-request-ids", requestIds],
    queryFn: () => listTransactionFailuresByRequestIds(token!, requestIds),
    enabled: requestIds.length > 0
  });
  const failureMap = useMemo(() => {
    const map = new Map<string, TransactionFailureResponse>();
    for (const f of failuresQuery.data?.items ?? []) map.set(f.transactionRequestId, f);
    return map;
  }, [failuresQuery.data]);

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
                <TH title="Publish to Kafka">Status</TH>
                <TH title="Ledger acceptance (async)">Outcome</TH>
                <TH>Chaos</TH>
                <TH>Correlation ID</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              <TableLoadingRows columns={8} rows={6} />
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
                <TH title="Publish to Kafka">Status</TH>
                <TH title="Ledger acceptance (async)">Outcome</TH>
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
                    <OutcomeCell
                      record={record}
                      failureMap={failureMap}
                      loading={failuresQuery.isLoading}
                    />
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
        failure={
          selectedRecord?.transactionRequestId
            ? failureMap.get(selectedRecord.transactionRequestId) ?? null
            : null
        }
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
// Account Transaction History Tab (cursor-paginated, per virtual account)
// ---------------------------------------------------------------------------

// The signed change this line applied to the account's *available* balance, per the ledger's own
// rule: a line moves the balance up when its direction matches the account's normal side, down
// otherwise (see BalanceProjectionService.signedDelta in ss-ledger-service).
function signedAvailableDelta(
  direction: string | null,
  normalBalance: string | null,
  amount: number | null
): number | null {
  if (amount === null || amount === undefined || !direction || !normalBalance) return null;
  const d = direction.toUpperCase();
  const n = normalBalance.toUpperCase();
  const increases = (n === "DEBIT" && d === "DEBIT") || (n === "CREDIT" && d === "CREDIT");
  return increases ? amount : -amount;
}

// runningBalance is the available balance *after* this line; the balance *before* is that minus the
// line's signed effect. Requires the account's normal balance to know the sign.
function balanceBeforeOf(
  tx: LedgerTransactionHistoryRecord,
  normalBalance: string | null
): number | null {
  if (tx.runningBalance === null || tx.runningBalance === undefined) return null;
  const delta = signedAvailableDelta(tx.direction, normalBalance, tx.amount);
  if (delta === null) return null;
  return tx.runningBalance - delta;
}

type LedgerHistoryFilters = {
  transactionRef: string;
  entryType: string;
  direction: string;
  from: string;
  to: string;
};

const INITIAL_HISTORY_FILTERS: LedgerHistoryFilters = {
  transactionRef: "",
  entryType: "",
  direction: "",
  from: "",
  to: ""
};

const HISTORY_COLUMNS = (
  <TR>
    <TH>Posted</TH>
    <TH>Entry Type</TH>
    <TH>Entry Line Type</TH>
    <TH>Direction</TH>
    <TH>Amount</TH>
    <TH>Running Balance</TH>
    <TH>Balance Before</TH>
    <TH>Transaction Reference</TH>
  </TR>
);

/**
 * The correct per-VA ledger history view: reads from the ledger's cursor-paginated
 * `/accounts/{id}/transactions` endpoint. Pages are walked forward/back via opaque cursors
 * (the endpoint has no offset/total), so we keep a stack of the cursors that opened each page.
 * Supports the full filter set the endpoint exposes (ref / entry type / direction / date range) and
 * a row click navigates to the transaction detail page.
 */
function AccountLedgerHistoryTab({ accountId }: { accountId: string }) {
  const { token } = useSession();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<LedgerHistoryFilters>(INITIAL_HISTORY_FILTERS);
  const [applied, setApplied] = useState<LedgerHistoryFilters>(INITIAL_HISTORY_FILTERS);
  // Cursors for pages *after* the first. The current page's cursor is the top of the stack
  // (undefined when the stack is empty → first page).
  const [cursorStack, setCursorStack] = useState<string[]>([]);

  // Any filter change (or account change) invalidates the cursor walk — restart from page one.
  useEffect(() => {
    setCursorStack([]);
  }, [accountId, applied]);

  const cursor = cursorStack.length > 0 ? cursorStack[cursorStack.length - 1] : undefined;

  // The account's normal balance lets us derive "balance before" from the running (after) balance.
  // Shares react-query's cache with the overview tab's ledger-account fetch.
  const accountQuery = useQuery({
    queryKey: ["ledger-account", accountId],
    queryFn: () => getLedgerAccount(token!, accountId),
    retry: false
  });
  const normalBalance = accountQuery.data?.normalBalance ?? null;

  const query = useQuery({
    queryKey: ["account-history", accountId, { ...applied, cursor }],
    queryFn: () =>
      getAccountTransactionHistory(token!, accountId, {
        cursor,
        size: PER_PAGE,
        transactionRef: applied.transactionRef || undefined,
        entryType: applied.entryType || undefined,
        direction: applied.direction || undefined,
        from: toISOFrom(applied.from),
        to: toISOTo(applied.to)
      }),
    retry: false,
    placeholderData: keepPreviousData
  });

  // Render strictly newest-first by the account's own monotonic sequence. The ledger already
  // paginates newest-first (posted_at DESC ⇒ account_sequence DESC) but breaks within-entry ties
  // with account_sequence ASC; sorting here makes the per-account sequence strictly descending down
  // the page and stays consistent with the cursor direction across pages.
  const records = [...(query.data?.items ?? [])].sort(
    (a, b) => b.accountSequence - a.accountSequence
  );
  const hasPrevious = cursorStack.length > 0;
  const hasNext = Boolean(query.data?.hasMore && query.data?.nextCursor);
  const is503 = isLedgerProxyUnavailable(query.error);

  function applyFilters() {
    setApplied(draft);
  }

  function clearFilters() {
    setDraft(INITIAL_HISTORY_FILTERS);
    setApplied(INITIAL_HISTORY_FILTERS);
  }

  function goNext() {
    const next = query.data?.nextCursor;
    if (next) setCursorStack(stack => [...stack, next]);
  }

  function goPrevious() {
    setCursorStack(stack => stack.slice(0, -1));
  }

  function openTransaction(tx: LedgerTransactionHistoryRecord) {
    if (tx.transactionRef) {
      navigate(`/transactions/${encodeURIComponent(tx.transactionRef)}`);
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Filter bar — every parameter the transaction-history endpoint accepts. */}
      <div className="border-b border-border bg-muted/30 px-6 py-3 md:px-8">
        <div className="flex flex-wrap gap-2">
          <Input
            className="w-full md:max-w-xs"
            value={draft.transactionRef}
            onChange={e => setDraft(d => ({ ...d, transactionRef: e.target.value }))}
            placeholder="Transaction reference"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Select
            value={draft.entryType}
            onChange={v => setDraft(d => ({ ...d, entryType: v }))}
            options={ENTRY_TYPE_OPTIONS}
            className="w-44"
            searchable
            searchPlaceholder="Search types…"
          />
          <Select
            value={draft.direction}
            onChange={v => setDraft(d => ({ ...d, direction: v }))}
            options={DIRECTION_OPTIONS}
            className="w-36"
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

      <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
        {query.isLoading ? (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>{HISTORY_COLUMNS}</THead>
              <TBody>
                <TableLoadingRows columns={8} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : query.error ? (
          <StatePanel
            title={is503 ? "Ledger proxy degraded" : "Failed to load transaction history"}
            description={
              is503
                ? "The ledger service is currently unavailable. Chaos-machine history is still accessible on the other tab."
                : getErrorMessage(query.error)
            }
            tone={is503 ? "warning" : "danger"}
            icon={is503 ? "access" : "error"}
            action={
              !is503 ? <Button onClick={() => void query.refetch()}>Retry</Button> : undefined
            }
          />
        ) : records.length === 0 ? (
          <StatePanel
            title="No transaction history"
            description="This account has no ledger movements for the current filters."
            iconNode={<Wallet className="h-5 w-5" />}
          />
        ) : (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>{HISTORY_COLUMNS}</THead>
              <TBody>
                {records.map(tx => {
                  // Prefer the ledger's total balance before this line; fall back to the locally
                  // derived available-balance-before until the ledger emits totalBalanceBefore.
                  const balanceBefore = tx.totalBalanceBefore ?? balanceBeforeOf(tx, normalBalance);
                  return (
                    <TR
                      key={tx.lineId}
                      role="button"
                      tabIndex={0}
                      className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                      onClick={() => openTransaction(tx)}
                      onKeyDown={event => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openTransaction(tx);
                        }
                      }}
                    >
                      <TD>{tx.postedAt ? formatDate(tx.postedAt) : "—"}</TD>
                      <TD>
                        {tx.entryType ? (
                          <Badge variant={getEntryTypeVariant(tx.entryType)}>
                            {formatEnumValue(tx.entryType)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TD>
                      <TD>
                        {tx.entryLineType ? (
                          <Badge variant={getEntryTypeVariant(tx.entryLineType)}>
                            {formatEnumValue(tx.entryLineType)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TD>
                      <TD>
                        {tx.direction ? (
                          <Badge variant={getDirectionVariant(tx.direction)}>
                            {formatEnumValue(tx.direction)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TD>
                      <TD className="tabular-nums">
                        {tx.amount !== null && tx.amount !== undefined
                          ? formatMoney(tx.amount, tx.currency ?? "GHS")
                          : "—"}
                      </TD>
                      <TD className="tabular-nums">
                        {tx.runningBalance !== null && tx.runningBalance !== undefined
                          ? formatMoney(tx.runningBalance, tx.currency ?? "GHS")
                          : "—"}
                      </TD>
                      <TD className="tabular-nums text-muted-foreground">
                        {balanceBefore !== null
                          ? formatMoney(balanceBefore, tx.currency ?? "GHS")
                          : "—"}
                      </TD>
                      <TD className="max-w-[12rem] truncate font-mono text-muted-foreground">
                        {tx.transactionRef ?? "—"}
                      </TD>
                    </TR>
                  );
                })}
              </TBody>
            </Table>
          </TableContainer>
        )}

        <CursorPagination
          hasPrevious={hasPrevious}
          hasNext={hasNext}
          label={records.length > 0 ? `${records.length} on this page` : ""}
          disabled={query.isFetching}
          onPrevious={goPrevious}
          onNext={goNext}
        />
      </div>
    </div>
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
  // On a virtual account detail page we show only the ledger's per-account transaction history.
  // (The chaos-machine vs ledger tab separation is deferred.)
  if (lockedVaId) {
    return <AccountLedgerHistoryTab accountId={lockedVaId} />;
  }

  const [draft, setDraft] = useState<TransactionFilters>({
    ...INITIAL,
    vaId: lockedVaId ?? ""
  });
  const [applied, setApplied] = useState<TransactionFilters>({
    ...INITIAL,
    vaId: lockedVaId ?? ""
  });

  const [sourceTab, setSourceTab] = usePersistedTabs("tab", "sent");

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
      <Tabs
        value={sourceTab}
        defaultValue="sent"
        onValueChange={setSourceTab}
        className="flex min-h-0 flex-1 flex-col"
      >
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
