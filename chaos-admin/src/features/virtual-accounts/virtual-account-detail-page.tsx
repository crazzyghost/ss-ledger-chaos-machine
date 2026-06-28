import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import { TransactionsTab } from "@/features/transactions/transactions-page";
import { BalanceHistoryTab } from "@/features/virtual-accounts/balance-history-tab";
import { ReservationsTab } from "@/features/virtual-accounts/reservations-tab";
import { getLedgerAccount, getLedgerAccountBalances, getVirtualAccount } from "@/lib/api";
import {
  formatDate,
  formatEnumValue,
  formatMoney,
  getAccountCategoryVariant,
  getStatusBadgeVariant
} from "@/lib/utils";
import { usePersistedTabs } from "@/lib/use-persisted-tabs";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useState, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";

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

type DetailField = {
  label: string;
  value: string | null | undefined;
  mono?: boolean;
  badge?: "status" | "category";
  ownershipType?: string | null;
};

function DetailCard({
  title,
  fields,
  badgeContext
}: {
  title: string;
  fields: DetailField[];
  badgeContext?: string | null;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="mb-3 text-xs font-semibold">{title}</p>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 md:grid-cols-3">
        {fields.map(field => (
          <div key={field.label}>
            <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
              {field.label}
            </dt>
            <dd className="mt-0.5 text-xs">
              {field.value == null || field.value === "" ? (
                <span className="text-muted-foreground">—</span>
              ) : field.badge === "status" ? (
                <Badge variant={getStatusBadgeVariant(field.value)}>
                  {formatEnumValue(field.value)}
                </Badge>
              ) : field.badge === "category" ? (
                <Badge variant={getAccountCategoryVariant(field.value, badgeContext)}>
                  {formatEnumValue(field.value)}
                </Badge>
              ) : field.mono ? (
                <span className="font-mono text-muted-foreground">{field.value}</span>
              ) : (
                String(field.value)
              )}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

// Formats a Date as the value a <input type="datetime-local"> expects ("YYYY-MM-DDTHH:mm"), i.e. a
// zoneless local wall-clock — the exact shape the ledger's `asOf` LocalDateTime binds.
function toLocalInputValue(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}`;
}

function BalanceBucket({
  label,
  value,
  currency
}: {
  label: string;
  value: number | null | undefined;
  currency: string;
}) {
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-sm font-semibold tabular-nums">{formatMoney(value, currency)}</p>
    </div>
  );
}

function BalancePanel({ token, vaId }: { token: string; vaId: string }) {
  // `asOf` null → current balance (shares the page-header's ["ledger-balance", vaId] cache entry, so
  // no double fetch); set → point-in-time view keyed separately, leaving the header on current.
  const [asOf, setAsOf] = useState<string | null>(null);
  const query = useQuery({
    queryKey: asOf ? ["ledger-balance", vaId, asOf] : ["ledger-balance", vaId],
    queryFn: () => getLedgerAccountBalances(token, vaId, asOf ?? undefined),
    retry: false
  });

  let body: ReactNode;
  if (query.isLoading) {
    body = <div className="h-16 animate-pulse rounded-lg bg-muted/40" />;
  } else if (query.error) {
    body = (
      <InlineNotice
        title="Ledger balance unavailable"
        description={
          asOf
            ? "Couldn’t load the balance as of the selected time — it may be in the future or before this account existed."
            : isLedgerProxyUnavailable(query.error)
              ? "Ledger service is currently degraded. Registry data still shown above."
              : getErrorMessage(query.error)
        }
        tone="warning"
      />
    );
  } else if (query.data) {
    const b = query.data;
    body = (
      <>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <BalanceBucket label="Total" value={b.total} currency={b.currency} />
          <BalanceBucket label="Available" value={b.available} currency={b.currency} />
          <BalanceBucket label="Reserved" value={b.reserved} currency={b.currency} />
          <BalanceBucket label="Pending" value={b.pending} currency={b.currency} />
        </div>
        {b.balanceAsOf && (
          <p className="mt-3 text-[10px] text-muted-foreground">
            Balance as of {formatDate(b.balanceAsOf)}
          </p>
        )}
      </>
    );
  } else {
    body = null;
  }

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-semibold">Ledger Balance</p>
        <div className="flex items-center gap-2">
          <label
            htmlFor="balance-as-of"
            className="text-[10px] uppercase tracking-wide text-muted-foreground"
          >
            Balance As Of
          </label>
          <Input
            id="balance-as-of"
            type="datetime-local"
            className="h-8 w-auto text-xs"
            value={asOf ?? ""}
            max={toLocalInputValue(new Date())}
            onChange={e => setAsOf(e.target.value ? e.target.value : null)}
          />
          {asOf && (
            <Button variant="ghost" size="sm" className="h-8" onClick={() => setAsOf(null)}>
              Now
            </Button>
          )}
        </div>
      </div>
      {body}
    </div>
  );
}

function OverviewTab({ vaId }: { vaId: string }) {
  const { token } = useSession();

  // Load from both sources: the chaos-machine projection and the ledger (the system of record).
  const chaosQuery = useQuery({
    queryKey: ["virtual-account", vaId],
    queryFn: () => getVirtualAccount(token!, vaId),
    retry: false
  });
  const ledgerQuery = useQuery({
    queryKey: ["ledger-account", vaId],
    queryFn: () => getLedgerAccount(token!, vaId),
    retry: false
  });

  const va = chaosQuery.data;
  const ledger = ledgerQuery.data;
  const hasChaos = Boolean(va);
  const hasLedger = Boolean(ledger);
  const isLoading = chaosQuery.isLoading || ledgerQuery.isLoading;

  if (isLoading) {
    return <div className="h-40 animate-pulse rounded-lg border border-border bg-muted/40" />;
  }

  if (!hasChaos && !hasLedger) {
    return (
      <StatePanel
        title="Account not found"
        description={
          isLedgerProxyUnavailable(ledgerQuery.error)
            ? "Not present in the chaos registry and the ledger is currently unavailable."
            : "This account exists in neither the chaos-machine registry nor the ledger."
        }
        tone="danger"
        icon="error"
        action={
          <Button
            onClick={() => {
              void chaosQuery.refetch();
              void ledgerQuery.refetch();
            }}
          >
            Retry
          </Button>
        }
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Source indicators: is this account mirrored in the chaos machine, and present in the ledger? */}
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-lg border border-border bg-card p-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold">Chaos mirror</span>
          <Badge variant={hasChaos ? "success" : "neutral"}>
            {hasChaos ? "Mirrored" : "Not mirrored"}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold">Ledger</span>
          <Badge variant={hasLedger ? "success" : "warning"}>
            {hasLedger ? "Present" : "Unavailable"}
          </Badge>
        </div>
        {!hasChaos && (
          <p className="text-xs text-muted-foreground">
            Ledger-owned account with no chaos-machine projection (created outside the chaos machine,
            or its <code className="rounded bg-muted px-1 py-0.5">ledger.account.created</code> event
            was not consumed).
          </p>
        )}
      </div>

      {/* Ledger balance — surfaced above the registry details so funds are the first thing seen */}
      <BalancePanel token={token!} vaId={vaId} />

      {/* Chaos registry details */}
      {hasChaos && va && (
        <DetailCard
          title="Chaos Registry Details"
          badgeContext={va.ownershipType}
          fields={[
            { label: "VA ID", value: va.vaId, mono: true },
            { label: "Name", value: va.name },
            { label: "Ownership", value: va.ownershipType },
            { label: "Category", value: va.accountCategory, badge: "category" },
            { label: "Currency", value: va.currency },
            { label: "Status", value: va.status, badge: "status" },
            { label: "Channel", value: va.channel ? formatEnumValue(va.channel) : null },
            { label: "Account Role", value: va.accountRole ? formatEnumValue(va.accountRole) : null },
            { label: "Org ID", value: va.organizationId, mono: true },
            { label: "Created Via", value: formatEnumValue(va.createdVia) },
            { label: "Created", value: formatDate(va.createdAt) },
            { label: "Updated", value: formatDate(va.updatedAt) }
          ]}
        />
      )}

      {/* Ledger details */}
      {hasLedger && ledger && (
        <DetailCard
          title="Ledger Account"
          badgeContext={ledger.accountOwnershipType}
          fields={[
            { label: "Account ID", value: ledger.accountId, mono: true },
            { label: "Account Code", value: ledger.accountCode, mono: true },
            { label: "Name", value: ledger.accountName },
            { label: "Category", value: ledger.accountCategory, badge: "category" },
            { label: "Normal Balance", value: ledger.normalBalance },
            { label: "Currency", value: ledger.currency },
            { label: "Status", value: ledger.status, badge: "status" },
            { label: "Ownership", value: ledger.accountOwnershipType },
            { label: "Org ID", value: ledger.organizationId, mono: true },
            { label: "Parent Account", value: ledger.parentAccountId, mono: true },
            { label: "Created", value: formatDate(ledger.createdAt) },
            { label: "Updated", value: formatDate(ledger.updatedAt) }
          ]}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// VA Detail Page
// ---------------------------------------------------------------------------

export function VirtualAccountDetailPage() {
  const navigate = useNavigate();
  const { vaId } = useParams<{ vaId: string }>();
  const { token } = useSession();

  const chaosQuery = useQuery({
    queryKey: ["virtual-account", vaId],
    queryFn: () => getVirtualAccount(token!, vaId!),
    enabled: Boolean(vaId),
    retry: false
  });
  const ledgerQuery = useQuery({
    queryKey: ["ledger-account", vaId],
    queryFn: () => getLedgerAccount(token!, vaId!),
    enabled: Boolean(vaId),
    retry: false
  });

  const [tab, setTab] = usePersistedTabs("tab", "overview");

  const va = chaosQuery.data;
  const ledger = ledgerQuery.data;

  // Current available balance for the sub-header so it stays visible across every tab. Shares the
  // ["ledger-balance", vaId] cache entry with the Overview tab's panel; gated on the account being
  // present in the ledger to avoid a guaranteed 404 for ledger-less projections.
  const balanceQuery = useQuery({
    queryKey: ["ledger-balance", vaId],
    queryFn: () => getLedgerAccountBalances(token!, vaId!),
    enabled: Boolean(vaId) && Boolean(ledger),
    retry: false
  });

  const title = va?.name ?? ledger?.accountName ?? vaId ?? "Virtual Account";
  const ownership = va?.ownershipType ?? ledger?.accountOwnershipType ?? null;
  const currency = va?.currency ?? ledger?.currency ?? null;
  const available =
    balanceQuery.data != null
      ? formatMoney(balanceQuery.data.available, balanceQuery.data.currency)
      : null;
  const description =
    ownership || currency
      ? [ownership ? formatEnumValue(ownership) : null, currency, available]
          .filter(Boolean)
          .join(" · ")
      : "Loading…";

  return (
    <Page>
      <PageHeader
        title={title}
        description={description}
        leadingActions={
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
            <ArrowLeft className="mr-1.5 h-4 w-4" />
            Back
          </Button>
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <Tabs
          value={tab}
          defaultValue="overview"
          onValueChange={setTab}
          className="flex min-h-0 flex-1 flex-col"
        >
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="transactions">Transactions</TabsTrigger>
            <TabsTrigger value="balance">Balance</TabsTrigger>
            <TabsTrigger value="reservations">Reservations</TabsTrigger>
          </TabsList>
          <TabsContent value="overview" className="flex-1 overflow-y-auto p-6 md:p-8">
            {vaId ? <OverviewTab vaId={vaId} /> : null}
          </TabsContent>
          <TabsContent value="transactions" className="flex min-h-0 flex-1 flex-col">
            {vaId ? <TransactionsTab lockedVaId={vaId} /> : null}
          </TabsContent>
          <TabsContent value="balance" className="flex-1 overflow-y-auto p-6 md:p-8">
            {vaId ? <BalanceHistoryTab vaId={vaId} currency={currency ?? undefined} /> : null}
          </TabsContent>
          <TabsContent value="reservations" className="flex-1 overflow-y-auto p-6 md:p-8">
            {vaId ? <ReservationsTab vaId={vaId} currency={currency ?? undefined} /> : null}
          </TabsContent>
        </Tabs>
      </PageContent>
    </Page>
  );
}
