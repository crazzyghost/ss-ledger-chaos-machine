import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import { TransactionsTab } from "@/features/transactions/transactions-page";
import { getVirtualAccount, getLedgerAccountBalances } from "@/lib/api";
import { formatDate, formatEnumValue, formatMoney, getStatusBadgeVariant } from "@/lib/utils";
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

function BalancePanel({ token, vaId }: { token: string; vaId: string }) {
  const query = useQuery({
    queryKey: ["ledger-balance", vaId],
    queryFn: () => getLedgerAccountBalances(token, vaId),
    retry: false
  });

  if (query.isLoading) {
    return (
      <div className="h-20 animate-pulse rounded-lg border border-border bg-muted/40" />
    );
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

  const query = useQuery({
    queryKey: ["virtual-account", vaId],
    queryFn: () => getVirtualAccount(token!, vaId)
  });

  const va = query.data;

  if (query.isLoading) {
    return <div className="h-40 animate-pulse rounded-lg border border-border bg-muted/40" />;
  }

  if (query.error) {
    return (
      <StatePanel
        title="Failed to load virtual account"
        description={getErrorMessage(query.error)}
        tone="danger"
        icon="error"
        action={<Button onClick={() => void query.refetch()}>Retry</Button>}
      />
    );
  }

  if (!va) return null;

  return (
    <div className="space-y-4">
      {/* Registry fields */}
      <div className="rounded-lg border border-border bg-card p-4">
        <p className="mb-3 text-xs font-semibold">Registry Details</p>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 md:grid-cols-3">
          {[
            { label: "VA ID", value: va.vaId, mono: true },
            { label: "Name", value: va.name },
            { label: "Ownership", value: formatEnumValue(va.ownershipType) },
            { label: "Currency", value: va.currency },
            { label: "Status", value: va.status, badge: true },
            { label: "Channel", value: va.channel ? formatEnumValue(va.channel) : "—" },
            { label: "Account Role", value: va.accountRole ? formatEnumValue(va.accountRole) : "—" },
            { label: "Org ID", value: va.organizationId ?? "—", mono: true },
            { label: "Created Via", value: formatEnumValue(va.createdVia) },
            { label: "Created", value: formatDate(va.createdAt) },
            { label: "Updated", value: formatDate(va.updatedAt) }
          ].map(field => (
            <div key={field.label}>
              <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
                {field.label}
              </dt>
              <dd className="mt-0.5 text-xs">
                {field.badge ? (
                  <Badge variant={getStatusBadgeVariant(field.value as string)}>
                    {formatEnumValue(field.value as string)}
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

  const query = useQuery({
    queryKey: ["virtual-account", vaId],
    queryFn: () => getVirtualAccount(token!, vaId!),
    enabled: Boolean(vaId)
  });

  const va = query.data;

  return (
    <Page>
      <PageHeader
        title={va?.name ?? vaId ?? "Virtual Account"}
        description={va ? `${formatEnumValue(va.ownershipType)} · ${va.currency}` : "Loading…"}
        leadingActions={
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
            <ArrowLeft className="mr-1.5 h-4 w-4" />
            Back
          </Button>
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <Tabs defaultValue="overview" className="flex min-h-0 flex-1 flex-col">
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
