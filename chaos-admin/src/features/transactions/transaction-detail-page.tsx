import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  getLedgerAccount,
  getTransactionByReference,
  type LedgerTransactionReferenceRecord
} from "@/lib/api";
import {
  formatDate,
  formatEnumValue,
  formatMoney,
  getDirectionVariant,
  getEntryTypeVariant
} from "@/lib/utils";
import { useQueries, useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";

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

function SummaryField({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className={`mt-0.5 text-xs ${mono ? "font-mono text-muted-foreground" : ""}`}>
        {value || <span className="text-muted-foreground">—</span>}
      </dd>
    </div>
  );
}

export function TransactionDetailPage() {
  const navigate = useNavigate();
  const { ref } = useParams<{ ref: string }>();
  const { token } = useSession();

  const query = useQuery({
    queryKey: ["transaction-by-ref", ref],
    queryFn: () => getTransactionByReference(token!, ref!),
    enabled: Boolean(ref),
    retry: false
  });

  const legs = query.data?.items ?? [];
  // Entry-level fields are identical across legs of the same transaction; read them off the first.
  const head: LedgerTransactionReferenceRecord | undefined = legs[0];
  const is503 = isLedgerProxyUnavailable(query.error);

  // The transaction-by-ref record carries only account ids, so resolve each distinct account to a
  // display name. Reuses react-query's ["ledger-account", id] cache shared with the detail page.
  const accountIds = useMemo(
    () =>
      Array.from(
        new Set(legs.map(l => l.accountId).filter((id): id is string => Boolean(id)))
      ),
    [legs]
  );
  const accountQueries = useQueries({
    queries: accountIds.map(id => ({
      queryKey: ["ledger-account", id],
      queryFn: () => getLedgerAccount(token!, id),
      enabled: Boolean(token),
      retry: false,
      staleTime: 5 * 60 * 1000
    }))
  });
  const accountNameById = new Map<string, string>();
  accountIds.forEach((id, i) => {
    const account = accountQueries[i]?.data;
    if (account) {
      accountNameById.set(id, account.accountName ?? account.accountCode ?? id);
    }
  });

  return (
    <Page>
      <PageHeader
        title="Transaction"
        description={ref ?? ""}
        leadingActions={
          <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
            <ArrowLeft className="mr-1.5 h-4 w-4" />
            Back
          </Button>
        }
      />
      <PageContent className="space-y-4 p-6 md:p-8">
        {query.isLoading ? (
          <div className="h-40 animate-pulse rounded-lg border border-border bg-muted/40" />
        ) : query.error ? (
          <StatePanel
            title={is503 ? "Ledger proxy degraded" : "Failed to load transaction"}
            description={
              is503
                ? "The ledger service is currently unavailable. Try again shortly."
                : getErrorMessage(query.error)
            }
            tone={is503 ? "warning" : "danger"}
            icon={is503 ? "access" : "error"}
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          />
        ) : legs.length === 0 ? (
          <StatePanel
            title="Transaction not found"
            description="No ledger entries were found for this transaction reference."
            tone="danger"
            icon="error"
          />
        ) : (
          <>
            {/* Summary (entry-level) */}
            <div className="rounded-lg border border-border bg-card p-4">
              <div className="mb-3 flex items-center gap-2">
                <p className="text-xs font-semibold">Summary</p>
                {head?.entryType && (
                  <Badge variant={getEntryTypeVariant(head.entryType)}>
                    {formatEnumValue(head.entryType)}
                  </Badge>
                )}
              </div>
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3 md:grid-cols-3">
                <SummaryField label="Reference" value={head?.transactionRef ?? ""} mono />
                <SummaryField label="Journal Entry" value={head?.journalEntryId ?? ""} mono />
                <SummaryField label="Entry Type" value={formatEnumValue(head?.entryType)} />
                <SummaryField label="Currency" value={head?.currency ?? ""} />
                <SummaryField
                  label="Posted"
                  value={head?.postedAt ? formatDate(head.postedAt) : ""}
                />
                <SummaryField label="Narrative" value={head?.narrative ?? ""} />
              </dl>
            </div>

            {/* Legs */}
            <TableContainer className="border border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Account</TH>
                    <TH>Ownership</TH>
                    <TH>Entry Line Type</TH>
                    <TH>Direction</TH>
                    <TH>Amount</TH>
                    <TH>Running Balance</TH>
                  </TR>
                </THead>
                <TBody>
                  {legs.map(leg => (
                    <TR
                      key={leg.lineId}
                      role="button"
                      tabIndex={0}
                      className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                      onClick={() => leg.accountId && navigate(`/virtual-accounts/${leg.accountId}`)}
                      onKeyDown={e => {
                        if ((e.key === "Enter" || e.key === " ") && leg.accountId) {
                          e.preventDefault();
                          navigate(`/virtual-accounts/${leg.accountId}`);
                        }
                      }}
                    >
                      <TD className="max-w-[14rem] truncate font-medium">
                        {leg.accountId
                          ? accountNameById.get(leg.accountId) ?? leg.accountId
                          : "—"}
                      </TD>
                      <TD className="text-xs text-muted-foreground">
                        {leg.accountOwnershipType ? formatEnumValue(leg.accountOwnershipType) : "—"}
                      </TD>
                      <TD>
                        {leg.entryLineType ? (
                          <Badge variant={getEntryTypeVariant(leg.entryLineType)}>
                            {formatEnumValue(leg.entryLineType)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TD>
                      <TD>
                        {leg.direction ? (
                          <Badge variant={getDirectionVariant(leg.direction)}>
                            {formatEnumValue(leg.direction)}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TD>
                      <TD className="tabular-nums">
                        {leg.amount !== null && leg.amount !== undefined
                          ? formatMoney(leg.amount, leg.currency ?? "GHS")
                          : "—"}
                      </TD>
                      <TD className="tabular-nums text-muted-foreground">
                        {leg.runningBalance !== null && leg.runningBalance !== undefined
                          ? formatMoney(leg.runningBalance, leg.currency ?? "GHS")
                          : "—"}
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </TableContainer>
          </>
        )}
      </PageContent>
    </Page>
  );
}
