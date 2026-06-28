import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import {
  createVirtualAccount,
  getBatchBalances,
  listCurrencies,
  listLedgerAccounts,
  listSupportedCountries,
  listVirtualAccounts,
  type BatchBalanceItem,
  type LedgerAccountFilters,
  type VirtualAccountFilters
} from "@/lib/api";
import {
  formatDate,
  formatEnumValue,
  formatMoney,
  getAccountCategoryVariant,
  getStatusBadgeVariant
} from "@/lib/utils";
import { usePersistedTabs } from "@/lib/use-persisted-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Database, Plus, Wallet } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

const PER_PAGE = 20;
const POLL_WINDOW_MS = 30_000;
const ACCOUNT_CATEGORY_OPTIONS = [
  { value: "", label: "Default (LIABILITY)" },
  { value: "ASSET", label: "Asset" },
  { value: "LIABILITY", label: "Liability" },
  { value: "REVENUE", label: "Revenue" },
  { value: "EXPENSE", label: "Expense" },
  { value: "EQUITY", label: "Equity" },
  { value: "CONTRA", label: "Contra" }
] as const;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

function isLedgerProxyUnavailable(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const status = (error as { status?: number }).status;
  return (
    status === 503 ||
    (status === 500 && error.message.toLowerCase().includes("temporarily unavailable"))
  );
}

// ---------------------------------------------------------------------------
// Create VA Dialog
// ---------------------------------------------------------------------------

function CreateVirtualAccountDialog({ onRequested }: { onRequested: () => void }) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [ownershipType, setOwnershipType] = useState("ORGANIZATION");
  const [currency, setCurrency] = useState("GHS");
  const [organizationId, setOrganizationId] = useState("");
  const [accountCode, setAccountCode] = useState("");
  const [accountCategory, setAccountCategory] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const ownershipOptions = [
    { value: "ORGANIZATION", label: "Organization" },
    { value: "SYSTEM", label: "System" }
  ] as const;

  // Currency list (Phase 010). Degrades to a free ISO-4217 text input when unavailable/empty.
  const currenciesQuery = useQuery({
    queryKey: ["currencies", { perPage: 300 }],
    queryFn: () => listCurrencies(token!, { perPage: 300 }),
    enabled: open
  });
  const currencyOptions = (currenciesQuery.data?.items ?? [])
    .filter(c => c.status === "ACTIVE")
    .map(c => ({ value: c.code, label: `${c.code} — ${c.name}` }));

  const mutation = useMutation({
    mutationFn: () =>
      createVirtualAccount(token!, {
        name: name.trim(),
        ownershipType,
        currency: currency.trim().toUpperCase(),
        organizationId: ownershipType === "ORGANIZATION" ? organizationId.trim() || undefined : undefined,
        accountCode: ownershipType === "SYSTEM" ? accountCode.trim() || undefined : undefined,
        accountCategory: accountCategory || undefined
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["virtual-accounts"] });
      onRequested();
      setOpen(false);
      resetForm();
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function resetForm() {
    setName("");
    setOwnershipType("ORGANIZATION");
    setCurrency("GHS");
    setOrganizationId("");
    setAccountCode("");
    setAccountCategory("");
    setFormError(null);
  }

  function handleCreate() {
    setFormError(null);
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    if (!currency.trim()) {
      setFormError("Currency is required.");
      return;
    }
    if (ownershipType === "ORGANIZATION" && !organizationId.trim()) {
      setFormError("Organization ID is required for ORGANIZATION type accounts.");
      return;
    }
    if (ownershipType === "SYSTEM" && !accountCode.trim()) {
      setFormError("Account code is required for SYSTEM type accounts.");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        setOpen(next);
        if (!next) resetForm();
      }}
    >
      <DialogTrigger asChild>
        <Button>
          <Plus className="mr-1.5 h-4 w-4" />
          Create Virtual Account
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Request Virtual Account</DialogTitle>
          <DialogDescription>
            The ledger owns virtual accounts. This forwards a creation request to the ledger; the
            account appears in the registry once the ledger confirms it.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="e.g. Platform Float Account"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Ownership Type</label>
            <Select
              value={ownershipType as typeof ownershipOptions[number]["value"]}
              onChange={v => setOwnershipType(v)}
              options={ownershipOptions}
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Currency</label>
            {currencyOptions.length > 0 ? (
              <Select
                value={currency}
                onChange={v => setCurrency(v)}
                options={currencyOptions}
                placeholder="Select currency…"
                searchable
                searchPlaceholder="Search currencies…"
              />
            ) : (
              <Input
                value={currency}
                onChange={e => setCurrency(e.target.value.toUpperCase())}
                placeholder="GHS"
                maxLength={3}
              />
            )}
          </div>
          {ownershipType === "ORGANIZATION" && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Organization ID</label>
              <Input
                value={organizationId}
                onChange={e => setOrganizationId(e.target.value)}
                placeholder="org-ulid"
              />
            </div>
          )}
          {ownershipType === "SYSTEM" && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Account Code</label>
              <Input
                value={accountCode}
                onChange={e => setAccountCode(e.target.value)}
                placeholder="e.g. ASSET.PLATFORM.FLOAT"
              />
            </div>
          )}
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Account Category (optional)</label>
            <Select
              value={accountCategory as typeof ACCOUNT_CATEGORY_OPTIONS[number]["value"]}
              onChange={v => setAccountCategory(v)}
              options={ACCOUNT_CATEGORY_OPTIONS}
            />
          </div>
          {formError && <InlineNotice description={formError} tone="danger" />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreate} disabled={mutation.isPending}>
            {mutation.isPending ? "Requesting…" : "Request"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Shared filters + table (used by both the Chaos Machine and Ledger tabs)
// ---------------------------------------------------------------------------

const OWNERSHIP_OPTIONS = [
  { value: "" as const, label: "All ownership" },
  { value: "ORGANIZATION" as const, label: "Organization" },
  { value: "SYSTEM" as const, label: "System" }
] as const;

const STATUS_OPTIONS = [
  { value: "" as const, label: "All statuses" },
  { value: "ACTIVE" as const, label: "Active" },
  { value: "SUSPENDED" as const, label: "Suspended" },
  { value: "FROZEN" as const, label: "Frozen" },
  { value: "DORMANT" as const, label: "Dormant" },
  { value: "CLOSED" as const, label: "Closed" }
] as const;

type Filters = {
  search: string;
  ownershipType: string;
  status: string;
  currency: string;
};

const INITIAL_FILTERS: Filters = { search: "", ownershipType: "", status: "", currency: "" };

/**
 * Currency filter options derived from the primary currencies of the supported countries
 * (distinct, sorted). Shared across both account tables.
 */
function useSupportedCurrencyOptions(): { value: string; label: string }[] {
  const { token } = useSession();
  const query = useQuery({
    queryKey: ["supported-country-currencies"],
    queryFn: () => listSupportedCountries(token!, { perPage: 200 }),
    staleTime: 5 * 60 * 1000
  });

  const codes = Array.from(
    new Set(
      (query.data?.items ?? [])
        .map(sc => sc.country?.primaryCurrency?.code)
        .filter((code): code is string => Boolean(code))
    )
  ).sort();

  return [{ value: "", label: "All currencies" }, ...codes.map(c => ({ value: c, label: c }))];
}

/** Shared filter bar for both account tables. */
function AccountFilters({
  draft,
  onDraftChange,
  onApply,
  onClear,
  searchPlaceholder
}: {
  draft: Filters;
  onDraftChange: (next: Filters) => void;
  onApply: () => void;
  onClear: () => void;
  searchPlaceholder: string;
}) {
  const currencyOptions = useSupportedCurrencyOptions();

  return (
    <div className="border-b border-border bg-muted/30 px-6 py-3 md:px-8">
      <div className="flex flex-wrap gap-2">
        <Input
          className="w-full md:max-w-xs"
          value={draft.search}
          onChange={e => onDraftChange({ ...draft, search: e.target.value })}
          placeholder={searchPlaceholder}
          onKeyDown={e => e.key === "Enter" && onApply()}
        />
        <Select
          value={draft.ownershipType as typeof OWNERSHIP_OPTIONS[number]["value"]}
          onChange={v => onDraftChange({ ...draft, ownershipType: v })}
          options={OWNERSHIP_OPTIONS}
          className="w-36"
        />
        <Select
          value={draft.status as typeof STATUS_OPTIONS[number]["value"]}
          onChange={v => onDraftChange({ ...draft, status: v })}
          options={STATUS_OPTIONS}
          className="w-36"
        />
        <Select
          value={draft.currency}
          onChange={v => onDraftChange({ ...draft, currency: v })}
          options={currencyOptions}
          placeholder="All currencies"
          className="w-40"
          searchable
          searchPlaceholder="Search currencies…"
        />
        <Button variant="outline" size="sm" onClick={onApply}>
          Apply
        </Button>
        <Button variant="ghost" size="sm" onClick={onClear}>
          Clear
        </Button>
      </div>
    </div>
  );
}

/** Unified row model rendered identically for chaos-machine VAs and ledger accounts. */
type AccountRow = {
  id: string;
  name: string;
  category: string | null;
  currency: string | null;
  ownershipType: string | null;
  ownerId: string | null;
  status: string | null;
  createdAt: string | null;
};

const ACCOUNT_COLUMNS = (
  <TR>
    <TH>Name</TH>
    <TH>Category</TH>
    <TH>Currency</TH>
    <TH className="text-right">Balance</TH>
    <TH>Owner</TH>
    <TH>Status</TH>
    <TH>Date created</TH>
  </TR>
);

/** Renders a row's total ledger balance, with loading and not-available (—) fallbacks. */
function BalanceCell({
  item,
  loading,
  rowCurrency
}: {
  item: BatchBalanceItem | undefined;
  loading: boolean;
  rowCurrency: string | null;
}) {
  if (item && item.status === "FOUND" && item.totalBalance != null) {
    return (
      <TD className="text-right tabular-nums">
        {formatMoney(item.totalBalance, item.currency ?? rowCurrency ?? "GHS")}
      </TD>
    );
  }
  return (
    <TD className="text-right tabular-nums">
      {loading && !item ? (
        <span className="inline-block h-3 w-16 animate-pulse rounded bg-muted/60 align-middle" />
      ) : (
        <span className="text-muted-foreground">—</span>
      )}
    </TD>
  );
}

/** Shared table for both account tables: Name / Category / Owner / Status / Date created. */
function AccountsTable({
  rows,
  isLoading,
  error,
  errorTitle,
  errorDescription,
  errorTone,
  empty,
  onRetry,
  onRowClick,
  balanceById,
  balancesLoading
}: {
  rows: AccountRow[];
  isLoading: boolean;
  error: unknown;
  errorTitle: string;
  errorDescription: string;
  errorTone: "danger" | "warning";
  empty: { title: string; description: string; icon: ReactNode };
  onRetry: () => void;
  onRowClick: (id: string) => void;
  balanceById: Map<string, BatchBalanceItem>;
  balancesLoading: boolean;
}) {
  if (isLoading) {
    return (
      <TableContainer className="flex-1 border-y border-border bg-card">
        <Table>
          <THead>{ACCOUNT_COLUMNS}</THead>
          <TBody>
            <TableLoadingRows columns={7} rows={6} />
          </TBody>
        </Table>
      </TableContainer>
    );
  }

  if (error) {
    return (
      <StatePanel
        title={errorTitle}
        description={errorDescription}
        tone={errorTone}
        icon="error"
        action={<Button onClick={onRetry}>Retry</Button>}
      />
    );
  }

  if (rows.length === 0) {
    return (
      <StatePanel title={empty.title} description={empty.description} iconNode={empty.icon} />
    );
  }

  return (
    <TableContainer className="flex-1 border-y border-border bg-card">
      <Table>
        <THead>{ACCOUNT_COLUMNS}</THead>
        <TBody>
          {rows.map(row => (
            <TR
              key={row.id}
              role="button"
              tabIndex={0}
              className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
              onClick={() => onRowClick(row.id)}
              onKeyDown={e => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  onRowClick(row.id);
                }
              }}
            >
              <TD className="font-medium">{row.name}</TD>
              <TD>
                {row.category ? (
                  <Badge
                    variant={getAccountCategoryVariant(row.category, row.ownershipType)}
                    className="font-medium"
                  >
                    {formatEnumValue(row.category)}
                  </Badge>
                ) : (
                  <span className="text-xs text-muted-foreground">—</span>
                )}
              </TD>
              <TD>{row.currency ?? "—"}</TD>
              <BalanceCell
                item={balanceById.get(row.id)}
                loading={balancesLoading}
                rowCurrency={row.currency}
              />
              <TD>
                <div className="flex flex-col">
                  <span className="text-xs text-muted-foreground">
                    {row.ownershipType ? formatEnumValue(row.ownershipType) : "—"}
                  </span>
                  <span className="max-w-[12rem] truncate font-mono text-xs">
                    {row.ownerId ?? "—"}
                  </span>
                </div>
              </TD>
              <TD>
                {row.status ? (
                  <Badge variant={getStatusBadgeVariant(row.status)}>
                    {formatEnumValue(row.status)}
                  </Badge>
                ) : (
                  "—"
                )}
              </TD>
              <TD>{formatDate(row.createdAt)}</TD>
            </TR>
          ))}
        </TBody>
      </Table>
    </TableContainer>
  );
}

// ---------------------------------------------------------------------------
// Chaos Machine Tab — the VA projection owned/mirrored by the chaos machine
// ---------------------------------------------------------------------------

function ChaosAccountsTab({ pollUntil }: { pollUntil: number }) {
  const { token } = useSession();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<Filters>(INITIAL_FILTERS);
  const [applied, setApplied] = useState<Filters>(INITIAL_FILTERS);

  const filters: VirtualAccountFilters = {
    page,
    perPage: PER_PAGE,
    ownershipType: applied.ownershipType || undefined,
    status: applied.status || undefined,
    currency: applied.currency || undefined,
    search: applied.search || undefined
  };

  const query = useQuery({
    queryKey: ["virtual-accounts", filters],
    queryFn: () => listVirtualAccounts(token!, filters),
    refetchInterval: () => (Date.now() < pollUntil ? 3000 : false)
  });

  const isPolling = Date.now() < pollUntil;

  const rows: AccountRow[] = (query.data?.items ?? []).map(a => ({
    id: a.vaId,
    name: a.name,
    category: a.accountCategory,
    currency: a.currency,
    ownershipType: a.ownershipType,
    ownerId: a.organizationId,
    status: a.status,
    createdAt: a.createdAt
  }));
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  // One batch-balance call per page feeds the total-balance column. Keeps polling during the
  // post-create window so a new VA's balance fills in once the ledger has it.
  const accountIds = useMemo(() => (query.data?.items ?? []).map(a => a.vaId), [query.data]);
  const balancesQuery = useQuery({
    queryKey: ["page-balances", accountIds],
    queryFn: () => getBatchBalances(token!, accountIds),
    enabled: accountIds.length > 0,
    refetchInterval: () => (Date.now() < pollUntil ? 3000 : false)
  });
  const balanceById = useMemo(() => {
    const m = new Map<string, BatchBalanceItem>();
    (balancesQuery.data ?? []).forEach(it => m.set(it.accountId, it));
    return m;
  }, [balancesQuery.data]);

  function applyFilters() {
    setPage(0);
    setApplied(draft);
  }

  function clearFilters() {
    setPage(0);
    setDraft(INITIAL_FILTERS);
    setApplied(INITIAL_FILTERS);
  }

  return (
    <div className="flex min-h-0 flex-col">
      <AccountFilters
        draft={draft}
        onDraftChange={setDraft}
        onApply={applyFilters}
        onClear={clearFilters}
        searchPlaceholder="Search by name or ID"
      />
      <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
        {isPolling && (
          <InlineNotice
            title="Account requested"
            description="The request was forwarded to the ledger. The account will appear here once the ledger.account.created event is consumed — refreshing automatically."
            tone="default"
          />
        )}
        <AccountsTable
          rows={rows}
          isLoading={query.isLoading}
          error={query.error}
          errorTitle="Failed to load virtual accounts"
          errorDescription={getErrorMessage(query.error)}
          errorTone="danger"
          empty={{
            title: "No virtual accounts found",
            description: "No accounts match the current filter criteria.",
            icon: <Wallet className="h-5 w-5" />
          }}
          onRetry={() => void query.refetch()}
          onRowClick={id => navigate(`/virtual-accounts/${id}`)}
          balanceById={balanceById}
          balancesLoading={balancesQuery.isLoading}
        />
        <ListPagination
          page={page}
          total={total}
          pageSize={PER_PAGE}
          itemLabel="account"
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
// Ledger Tab — the global, ledger-owned accounts (read via the proxy)
// ---------------------------------------------------------------------------

function LedgerAccountsTab() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<Filters>(INITIAL_FILTERS);
  const [applied, setApplied] = useState<Filters>(INITIAL_FILTERS);

  const filters: LedgerAccountFilters = {
    page,
    size: PER_PAGE,
    ownershipType: applied.ownershipType || undefined,
    status: applied.status || undefined,
    currency: applied.currency || undefined,
    // The ledger list has no name search; reuse the shared search field as an org-id filter.
    organizationId: applied.search || undefined
  };

  const query = useQuery({
    queryKey: ["ledger-accounts", filters],
    queryFn: () => listLedgerAccounts(token!, filters),
    retry: false
  });

  const is503 = isLedgerProxyUnavailable(query.error);
  const rows: AccountRow[] = (query.data?.items ?? []).map(a => ({
    id: a.accountId,
    name: a.accountName ?? a.accountCode ?? a.accountId,
    category: a.accountCategory,
    currency: a.currency,
    ownershipType: a.accountOwnershipType,
    ownerId: a.organizationId,
    status: a.status,
    createdAt: a.createdAt
  }));
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  // One batch-balance call per page feeds the total-balance column.
  const accountIds = useMemo(() => (query.data?.items ?? []).map(a => a.accountId), [query.data]);
  const balancesQuery = useQuery({
    queryKey: ["page-balances", accountIds],
    queryFn: () => getBatchBalances(token!, accountIds),
    enabled: accountIds.length > 0,
    retry: false
  });
  const balanceById = useMemo(() => {
    const m = new Map<string, BatchBalanceItem>();
    (balancesQuery.data ?? []).forEach(it => m.set(it.accountId, it));
    return m;
  }, [balancesQuery.data]);

  function applyFilters() {
    setPage(0);
    setApplied(draft);
  }

  function clearFilters() {
    setPage(0);
    setDraft(INITIAL_FILTERS);
    setApplied(INITIAL_FILTERS);
  }

  return (
    <div className="flex min-h-0 flex-col">
      <AccountFilters
        draft={draft}
        onDraftChange={setDraft}
        onApply={applyFilters}
        onClear={clearFilters}
        searchPlaceholder="Filter by organization ID"
      />
      <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
        <AccountsTable
          rows={rows}
          isLoading={query.isLoading}
          error={query.error}
          errorTitle={is503 ? "Ledger proxy degraded" : "Failed to load ledger accounts"}
          errorDescription={
            is503
              ? "The ledger service is currently unavailable. Chaos-machine accounts are still accessible on the other tab."
              : getErrorMessage(query.error)
          }
          errorTone={is503 ? "warning" : "danger"}
          empty={{
            title: "No ledger accounts found",
            description: "No accounts match the current filter criteria.",
            icon: <Database className="h-5 w-5" />
          }}
          onRetry={() => void query.refetch()}
          onRowClick={id => navigate(`/virtual-accounts/${id}`)}
          balanceById={balanceById}
          balancesLoading={balancesQuery.isLoading}
        />
        <ListPagination
          page={page}
          total={total}
          pageSize={PER_PAGE}
          itemLabel="account"
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
// Main Page
// ---------------------------------------------------------------------------

export function VirtualAccountsPage() {
  // After requesting a VA, briefly poll the chaos list so the async projection surfaces it.
  const [pollUntil, setPollUntil] = useState(0);
  const [tab, setTab] = usePersistedTabs("tab", "ledger");

  return (
    <Page>
      <PageHeader
        title="Virtual Accounts"
        description="Browse chaos-machine accounts and the global ledger. The ledger owns VAs; requested accounts appear once confirmed."
        actions={
          <CreateVirtualAccountDialog
            onRequested={() => setPollUntil(Date.now() + POLL_WINDOW_MS)}
          />
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <Tabs
          value={tab}
          defaultValue="ledger"
          onValueChange={setTab}
          className="flex min-h-0 flex-1 flex-col"
        >
          <TabsList>
            <TabsTrigger value="ledger">Ledger</TabsTrigger>
            <TabsTrigger value="chaos">Chaos Machine</TabsTrigger>
          </TabsList>
          <TabsContent value="ledger" className="flex min-h-0 flex-1 flex-col">
            <LedgerAccountsTab />
          </TabsContent>
          <TabsContent value="chaos" className="flex min-h-0 flex-1 flex-col">
            <ChaosAccountsTab pollUntil={pollUntil} />
          </TabsContent>
        </Tabs>
      </PageContent>
    </Page>
  );
}
