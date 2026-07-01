import { CursorPagination } from "@/components/layout/cursor-pagination";
import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  getAccountTransactionHistory,
  getLedgerAccount,
  listLedgerJournalEntries,
  type LedgerTransactionHistoryRecord,
  type ReconciliationEntryResponse
} from "@/lib/api";
import {
  formatDate,
  formatEnumValue,
  formatMoney,
  getDirectionVariant,
  getEntryTypeVariant
} from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Wallet } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

// The ledger's EntryTypeEnum — used to populate the entry-type filters.
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

// The ledger's reconciliation journal-entries export requires from/to and caps the span (~7 days);
// the UI defaults to and clamps within this window so we pre-empt the ledger's period 400 (ADR-032).
const MAX_WINDOW_DAYS = 7;

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

// A 4xx from the journal-entries proxy means the ledger rejected the period (missing/too-wide window
// — the proxy relays the ledger's 400 as a 4xx). Distinct from a 503 (proxy degraded).
function isPeriodError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const status = (error as { status?: number }).status;
  return status === 400 || status === 404;
}

function toISOFrom(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T00:00:00.000Z`;
}

function toISOTo(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T23:59:59.999Z`;
}

function toDateInput(d: Date): string {
  return d.toISOString().slice(0, 10);
}

// A sensible default window within the ledger's cap: the last 7 calendar days.
function defaultWindow(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - (MAX_WINDOW_DAYS - 1) * 24 * 60 * 60 * 1000);
  return { from: toDateInput(from), to: toDateInput(to) };
}

// The window span in days (or null when either bound is unset/unparseable). Negative = inverted.
function windowSpanDays(from: string, to: string): number | null {
  if (!from || !to) return null;
  const fromMs = Date.parse(`${from}T00:00:00Z`);
  const toMs = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(fromMs) || Number.isNaN(toMs)) return null;
  return (toMs - fromMs) / 86_400_000;
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
                ? "The ledger service is currently unavailable. Try again once it recovers."
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
// Global Ledger Journal Entries (date-windowed, offset-paginated, cross-account)
// ---------------------------------------------------------------------------

type JournalEntryFilters = {
  from: string;
  to: string;
  accountId: string;
  entryType: string;
  transactionRef: string;
  sourceService: string;
};

/**
 * The standalone Transactions page's primary content (ADR-032): a working, global ledger browser
 * over the reconciliation journal-entries export. Date-range-first (defaults to and clamps within
 * the ledger's ~7-day cap), offset-paginated, with optional account/entry-type/ref/source filters.
 * Each row is a journal-entry line; a row click opens the by-reference detail (the full transaction
 * with all legs).
 */
function GlobalLedgerJournalEntries() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const initialWindow = defaultWindow();
  const [draft, setDraft] = useState<JournalEntryFilters>({
    ...initialWindow,
    accountId: "",
    entryType: "",
    transactionRef: "",
    sourceService: ""
  });
  const [applied, setApplied] = useState<JournalEntryFilters>({
    ...initialWindow,
    accountId: "",
    entryType: "",
    transactionRef: "",
    sourceService: ""
  });

  useEffect(() => {
    setPage(0);
  }, [applied]);

  const draftSpan = windowSpanDays(draft.from, draft.to);
  const draftTooWide = draftSpan !== null && draftSpan > MAX_WINDOW_DAYS;
  const draftInverted = draftSpan !== null && draftSpan < 0;
  const draftWindowValid = Boolean(draft.from && draft.to && !draftTooWide && !draftInverted);

  const appliedSpan = windowSpanDays(applied.from, applied.to);
  const appliedWindowValid =
    Boolean(applied.from && applied.to) &&
    appliedSpan !== null &&
    appliedSpan >= 0 &&
    appliedSpan <= MAX_WINDOW_DAYS;

  function applyFilters() {
    if (!draftWindowValid) return;
    setApplied(draft);
  }

  function clearFilters() {
    const reset: JournalEntryFilters = {
      ...defaultWindow(),
      accountId: "",
      entryType: "",
      transactionRef: "",
      sourceService: ""
    };
    setDraft(reset);
    setApplied(reset);
  }

  const query = useQuery({
    queryKey: ["ledger-journal-entries", { ...applied, page }],
    queryFn: () =>
      listLedgerJournalEntries(token!, {
        from: toISOFrom(applied.from)!,
        to: toISOTo(applied.to)!,
        accountId: applied.accountId || undefined,
        entryType: applied.entryType || undefined,
        transactionRef: applied.transactionRef || undefined,
        sourceService: applied.sourceService || undefined,
        page,
        size: PER_PAGE
      }),
    enabled: appliedWindowValid,
    retry: false,
    placeholderData: keepPreviousData
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;
  const is503 = isLedgerProxyUnavailable(query.error);
  const isPeriod = isPeriodError(query.error);

  function openTransaction(row: ReconciliationEntryResponse) {
    if (row.transactionRef) {
      navigate(`/transactions/${encodeURIComponent(row.transactionRef)}`);
    }
  }

  const columns = (
    <TR>
      <TH>Posted</TH>
      <TH>Account</TH>
      <TH>Direction</TH>
      <TH>Amount</TH>
      <TH>Currency</TH>
      <TH>Entry Type</TH>
      <TH>Transaction Ref</TH>
      <TH>Source</TH>
    </TR>
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Date-range-first filter bar. The window is required and capped at MAX_WINDOW_DAYS days. */}
      <div className="border-b border-border bg-muted/30 px-6 py-3 md:px-8">
        <div className="flex flex-wrap items-center gap-2">
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
          <Input
            className="w-44"
            value={draft.accountId}
            onChange={e => setDraft(d => ({ ...d, accountId: e.target.value }))}
            placeholder="Account ID"
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
          <Input
            className="w-44"
            value={draft.transactionRef}
            onChange={e => setDraft(d => ({ ...d, transactionRef: e.target.value }))}
            placeholder="Transaction reference"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Input
            className="w-40"
            value={draft.sourceService}
            onChange={e => setDraft(d => ({ ...d, sourceService: e.target.value }))}
            placeholder="Source service"
            onKeyDown={e => e.key === "Enter" && applyFilters()}
          />
          <Button variant="outline" size="sm" onClick={applyFilters} disabled={!draftWindowValid}>
            Apply
          </Button>
          <Button variant="ghost" size="sm" onClick={clearFilters}>
            Clear
          </Button>
        </div>
        {(draftTooWide || draftInverted) && (
          <p className="mt-2 text-xs text-amber-700">
            {draftInverted
              ? "The “From” date must be on or before the “To” date."
              : `Ledger history is browsed by date window, max ${MAX_WINDOW_DAYS} days. Narrow the range to apply.`}
          </p>
        )}
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
        {query.isLoading ? (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>{columns}</THead>
              <TBody>
                <TableLoadingRows columns={8} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : query.error ? (
          <StatePanel
            title={
              is503
                ? "Ledger proxy degraded"
                : isPeriod
                  ? "Period not accepted"
                  : "Failed to load journal entries"
            }
            description={
              is503
                ? "The ledger service is currently unavailable. Try again once it recovers."
                : isPeriod
                  ? `The ledger rejected this window. A from/to range is required and the span must be at most ${MAX_WINDOW_DAYS} days — narrow the dates and retry.`
                  : getErrorMessage(query.error)
            }
            tone={is503 || isPeriod ? "warning" : "danger"}
            icon={is503 ? "access" : isPeriod ? "warning" : "error"}
            action={
              !is503 && !isPeriod ? (
                <Button onClick={() => void query.refetch()}>Retry</Button>
              ) : undefined
            }
          />
        ) : records.length === 0 ? (
          <StatePanel
            title="No journal entries"
            description="No ledger activity in this window. Widen the date range (up to the cap) or adjust the filters."
            iconNode={<Wallet className="h-5 w-5" />}
          />
        ) : (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>{columns}</THead>
              <TBody>
                {records.map(row => (
                  <TR
                    key={row.lineId ?? `${row.transactionRef}-${row.accountSequence}`}
                    role="button"
                    tabIndex={0}
                    className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                    onClick={() => openTransaction(row)}
                    onKeyDown={event => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        openTransaction(row);
                      }
                    }}
                  >
                    <TD>{row.postedAt ? formatDate(row.postedAt) : "—"}</TD>
                    <TD className="max-w-[12rem] truncate font-mono text-muted-foreground">
                      {row.accountCode ?? row.accountId ?? "—"}
                    </TD>
                    <TD>
                      {row.direction ? (
                        <Badge variant={getDirectionVariant(row.direction)}>
                          {formatEnumValue(row.direction)}
                        </Badge>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TD>
                    <TD className="tabular-nums">
                      {row.amount !== null && row.amount !== undefined
                        ? formatMoney(row.amount, row.currency ?? "GHS")
                        : "—"}
                    </TD>
                    <TD>{row.currency ?? "—"}</TD>
                    <TD>
                      {row.entryType ? (
                        <Badge variant={getEntryTypeVariant(row.entryType)}>
                          {formatEnumValue(row.entryType)}
                        </Badge>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TD>
                    <TD className="max-w-[12rem] truncate font-mono text-muted-foreground">
                      {row.transactionRef ?? "—"}
                    </TD>
                    <TD className="max-w-[10rem] truncate text-muted-foreground">
                      {row.sourceService ?? "—"}
                    </TD>
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
          itemLabel="entry"
          hasNextPage={hasNextPage}
          disabled={query.isFetching}
          onPrevious={() => setPage(p => Math.max(p - 1, 0))}
          onNext={() => setPage(p => p + 1)}
        />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Reusable tab content (embeddable from VA detail page)
// ---------------------------------------------------------------------------

export function TransactionsTab({ lockedVaId }: { lockedVaId?: string }) {
  // On a virtual account detail page we show the ledger's per-account (cursor-paged) history.
  if (lockedVaId) {
    return <AccountLedgerHistoryTab accountId={lockedVaId} />;
  }
  // The standalone page is the global, date-windowed ledger browser (ADR-032). The chaos-sent event
  // history now lives in the Scenario Runner's Run History tab (ADR-030/031).
  return <GlobalLedgerJournalEntries />;
}

// ---------------------------------------------------------------------------
// Standalone Transactions Page
// ---------------------------------------------------------------------------

export function TransactionsPage() {
  return (
    <Page>
      <PageHeader
        title="Transactions"
        description="Browse the ledger's journal entries across accounts for a date window (max 7 days). Click a row to see the full transaction; chaos-sent history now lives under Scenario Runner → Run History."
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <TransactionsTab />
      </PageContent>
    </Page>
  );
}
