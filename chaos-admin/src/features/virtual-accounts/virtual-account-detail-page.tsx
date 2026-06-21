import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import { TransactionsTab } from "@/features/transactions/transactions-page";
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

function BalancePanel({ token, vaId }: { token: string; vaId: string }) {
  const query = useQuery({
    queryKey: ["ledger-balance", vaId],
    queryFn: () => getLedgerAccountBalances(token, vaId),
    retry: false
  });

  if (query.isLoading) {
    return <div className="h-20 animate-pulse rounded-lg border border-border bg-muted/40" />;
  }

  if (query.error) {
    return (
      <InlineNotice
        title="Ledger balance unavailable"
        description={
          isLedgerProxyUnavailable(query.error)
            ? "Ledger service is currently degraded. Registry data still shown above."
            : getErrorMessage(query.error)
        }
        tone="warning"
      />
    );
  }

  const b = query.data;
  if (!b) return null;

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="mb-3 text-xs font-semibold">Ledger Balance</p>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
        <div>
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Balance</p>
          <p className="mt-0.5 text-sm font-semibold tabular-nums">
            {formatMoney(b.total, b.currency)}
          </p>
        </div>
        <div>
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Available</p>
          <p className="mt-0.5 text-sm font-semibold tabular-nums">
            {formatMoney(b.available, b.currency)}
          </p>
        </div>
        <div>
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Currency</p>
          <p className="mt-0.5 text-sm font-semibold">{b.currency}</p>
        </div>
      </div>
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

      {/* Ledger balance */}
      <BalancePanel token={token!} vaId={vaId} />
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
  const title = va?.name ?? ledger?.accountName ?? vaId ?? "Virtual Account";
  const ownership = va?.ownershipType ?? ledger?.accountOwnershipType ?? null;
  const currency = va?.currency ?? ledger?.currency ?? null;
  const description =
    ownership || currency
      ? [ownership ? formatEnumValue(ownership) : null, currency].filter(Boolean).join(" · ")
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
          </TabsList>
          <TabsContent value="overview" className="flex-1 overflow-y-auto p-6 md:p-8">
            {vaId ? <OverviewTab vaId={vaId} /> : null}
          </TabsContent>
          <TabsContent value="transactions" className="flex min-h-0 flex-1 flex-col">
            {vaId ? <TransactionsTab lockedVaId={vaId} /> : null}
          </TabsContent>
        </Tabs>
      </PageContent>
    </Page>
  );
}
