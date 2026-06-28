import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { useSession } from "@/features/auth/session-provider";
import {
  buildChaosOptions,
  ChaosOptionsPanel,
  INITIAL_CHAOS,
  needsConfirm,
  type ChaosFormState
} from "@/features/chaos/chaos-options-panel";
import { BatchDisbursementWizard } from "@/features/chaos/batch-disbursement-wizard";
import { LifecycleWizard } from "@/features/chaos/lifecycle-wizard";
import {
  TransactionTypeForm,
  type AssembledFlow
} from "@/features/chaos/transaction-type-form";
import { useBalanceUpdateWatch } from "@/features/chaos/use-balance-update-watch";
import { useTransactionFailureWatch } from "@/features/chaos/use-transaction-failure-watch";
import { BALANCE_SKEW_MS } from "@/features/chaos/watch-config";
import {
  getFlowCatalog,
  publishNTimes,
  runFlow,
  type BatchRunResponse,
  type FlowResult,
  type NTimesSyncResult,
  type PublishFlowRequest,
  type TransactionFailureResponse
} from "@/lib/api";
import { cn } from "@/lib/utils";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, Play, RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

/** The runner-visible transaction types, in the order the idea lists them. */
const RUNNER_ORDER = [
  "TOPUP_CONFIRMED",
  "TRANSFER_REQUESTED",
  "TREASURY_SWEEP_COMPLETED",
  "TREASURY_PREFUND_COMPLETED",
  "TREASURY_TRANSFER_COMPLETED",
  "COLLECTION_COMPLETED",
  "SETTLEMENT_INITIATED",
  "DISBURSEMENT_INITIATED",
  "DISBURSEMENT_BATCH_RESERVATION_REQUEST"
] as const;

const FLOW_LABELS: Record<string, string> = {
  TOPUP_CONFIRMED: "Top-up",
  TRANSFER_REQUESTED: "Inter-VA Transfer",
  TREASURY_SWEEP_COMPLETED: "Treasury Sweep",
  TREASURY_PREFUND_COMPLETED: "Treasury Prefund",
  TREASURY_TRANSFER_COMPLETED: "Treasury Transfer",
  COLLECTION_COMPLETED: "Collection",
  SETTLEMENT_INITIATED: "Settlement",
  DISBURSEMENT_INITIATED: "Disbursement",
  DISBURSEMENT_BATCH_RESERVATION_REQUEST: "Batch Disbursement"
};

const DEFAULT_FLOW = "TOPUP_CONFIRMED";

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

const EMPTY_ASSEMBLED: AssembledFlow = {
  slotOverrides: {},
  flowFields: {},
  correlationId: "",
  tenantId: "",
  currency: "",
  amount: "",
  fees: [],
  missingRequired: []
};

function FlowResultCard({
  result,
  failure
}: {
  result: FlowResult;
  failure?: TransactionFailureResponse | null;
}) {
  const ledgerFailed = Boolean(failure);
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Flow Result</CardTitle>
        <div className="flex items-center gap-2">
          <Badge variant={result.status === "PUBLISHED" ? "success" : "destructive"}>
            {result.status}
          </Badge>
          {ledgerFailed && <Badge variant="destructive">Failed @ ledger</Badge>}
        </div>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-3">
          {[
            { label: "Event ID", value: result.eventId, mono: true },
            { label: "Topic", value: result.topic, mono: true },
            { label: "Partition", value: result.partition.toString() },
            { label: "Offset", value: result.offset.toString() },
            { label: "History ID", value: result.historyId, mono: true }
          ].map(f => (
            <div key={f.label}>
              <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
                {f.label}
              </dt>
              <dd className={cn("mt-0.5 text-xs", f.mono && "font-mono text-muted-foreground")}>
                {f.value}
              </dd>
            </div>
          ))}
        </dl>
        {result.error && <InlineNotice description={result.error} tone="danger" className="mt-3" />}
        {ledgerFailed && failure && (
          <InlineNotice
            title={`Failed at ledger${failure.failureCode ? `: ${failure.failureCode}` : ""}`}
            description={failure.failureReason ?? "The ledger rejected this transaction."}
            tone="danger"
            className="mt-3"
          />
        )}
      </CardContent>
    </Card>
  );
}

function NTimesSyncResultCard({
  result,
  failures = []
}: {
  result: NTimesSyncResult;
  failures?: TransactionFailureResponse[];
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>N Times Result</CardTitle>
        <div className="flex items-center gap-2">
          <Badge variant={result.failed === 0 ? "success" : "destructive"}>
            {result.succeeded}/{result.count} published
          </Badge>
          {failures.length > 0 && (
            <Badge variant="destructive">
              {failures.length} of {result.count} failed @ ledger
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {[
            { label: "Count", value: result.count.toString() },
            { label: "Succeeded", value: result.succeeded.toString() },
            { label: "Failed", value: result.failed.toString() },
            { label: "Correlation ID", value: result.correlationId, mono: true }
          ].map(f => (
            <div key={f.label}>
              <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
                {f.label}
              </dt>
              <dd className={cn("mt-0.5 text-xs", f.mono && "font-mono text-muted-foreground")}>
                {f.value}
              </dd>
            </div>
          ))}
        </dl>
        <div>
          <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">Event IDs</dt>
          <dd className="mt-1 max-h-32 space-y-0.5 overflow-auto font-mono text-[11px] text-muted-foreground">
            {result.eventIds.map(id => (
              <div key={id}>{id}</div>
            ))}
          </dd>
        </div>
      </CardContent>
    </Card>
  );
}

type RunOutcome =
  | { type: "flow"; result: FlowResult; accountIds: string[]; since: string }
  | { type: "ntimesSync"; result: NTimesSyncResult; accountIds: string[]; since: string }
  | { type: "ntimesAsync"; run: BatchRunResponse };

export function SingleFlowRunPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [flowType, setFlowType] = useState<string>(DEFAULT_FLOW);
  const [assembled, setAssembled] = useState<AssembledFlow>(EMPTY_ASSEMBLED);
  const [chaos, setChaos] = useState<ChaosFormState>(INITIAL_CHAOS);
  const [formError, setFormError] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [result, setResult] = useState<FlowResult | null>(null);
  const [nTimesResult, setNTimesResult] = useState<NTimesSyncResult | null>(null);
  // Run-page watches (Phases 017/018): after a publish, watch for ledger failures (by request id)
  // and balance updates (by involved account + time watermark). The hooks fire their own toasts; the
  // failure hook also returns matches so the result cards can flip to a "Failed @ ledger" state.
  const [watchRequestIds, setWatchRequestIds] = useState<string[]>([]);
  const [watchAccountIds, setWatchAccountIds] = useState<string[]>([]);
  const [watchSince, setWatchSince] = useState<string | null>(null);

  const { failures } = useTransactionFailureWatch(watchRequestIds);
  useBalanceUpdateWatch(watchAccountIds, watchSince);

  function clearWatches() {
    setWatchRequestIds([]);
    setWatchAccountIds([]);
    setWatchSince(null);
  }

  const catalogQuery = useQuery({
    queryKey: ["flow-catalog"],
    queryFn: () => getFlowCatalog(token!),
    enabled: Boolean(token)
  });

  const runnerFlows = useMemo(() => {
    const byType = new Map((catalogQuery.data ?? []).map(c => [c.flowType, c]));
    return RUNNER_ORDER.map(t => byType.get(t)).filter(
      (c): c is NonNullable<typeof c> => Boolean(c?.runnerVisible)
    );
  }, [catalogQuery.data]);

  const selectedCatalog = runnerFlows.find(c => c.flowType === flowType) ?? null;

  const buildRequest = (): PublishFlowRequest => ({
    correlationId: assembled.correlationId.trim() || null,
    tenantId: assembled.tenantId.trim() || null,
    channel: null,
    currency: assembled.currency.trim() || null,
    amount: assembled.amount ? parseFloat(assembled.amount) : null,
    grossAmount: null,
    netAmount: null,
    slotOverrides: assembled.slotOverrides,
    flowFields: assembled.flowFields,
    chaos: buildChaosOptions(chaos),
    fees: assembled.fees
  });

  // Best-effort resolution of the accounts a flow touches — the resolved slot VAs + VA_REF fields,
  // i.e. the values that went into the published payload (the Phase 018 balance-watch scope).
  const involvedAccountIds = (): string[] => {
    const fromSlots = Object.values(assembled.slotOverrides ?? {});
    const fromFields = (selectedCatalog?.fields ?? [])
      .filter(f => f.kind === "VA_REF")
      .map(f => assembled.flowFields?.[f.name])
      .filter((v): v is string => typeof v === "string" && v.trim().length > 0);
    return Array.from(new Set([...fromSlots, ...fromFields].filter(Boolean)));
  };

  const mutation = useMutation<RunOutcome>({
    mutationFn: async () => {
      const req = buildRequest();
      // Capture the watch scope at publish time (current form state), avoiding stale closures.
      const accountIds = involvedAccountIds();
      const since = new Date(Date.now() - BALANCE_SKEW_MS).toISOString();
      if (chaos.strategy === "nTimes") {
        const res = await publishNTimes(token!, flowType, req);
        return res.kind === "async"
          ? { type: "ntimesAsync", run: res.run }
          : { type: "ntimesSync", result: res.result, accountIds, since };
      }
      return { type: "flow", result: await runFlow(token!, flowType, req), accountIds, since };
    },
    onSuccess: outcome => {
      setFormError(null);
      if (outcome.type === "ntimesAsync") {
        clearWatches();
        navigate(`/chaos/batches/${outcome.run.id}`);
        return;
      }
      if (outcome.type === "ntimesSync") {
        setNTimesResult(outcome.result);
        setResult(null);
        setWatchRequestIds(
          outcome.result.transactionRequestIds.filter((id): id is string => Boolean(id))
        );
        setWatchAccountIds(outcome.accountIds);
        setWatchSince(outcome.since);
        return;
      }
      setResult(outcome.result);
      setNTimesResult(null);
      setWatchRequestIds(
        outcome.result.transactionRequestId ? [outcome.result.transactionRequestId] : []
      );
      setWatchAccountIds(outcome.accountIds);
      setWatchSince(outcome.since);
    },
    onError: err => setFormError(getErrorMessage(err))
  });

  function handleSubmit() {
    setFormError(null);
    setResult(null);
    setNTimesResult(null);
    clearWatches();
    if (assembled.missingRequired.length > 0) {
      setFormError(`Required fields missing: ${assembled.missingRequired.join(", ")}`);
      return;
    }
    if (needsConfirm(chaos.strategy)) {
      setConfirmOpen(true);
      return;
    }
    mutation.mutate();
  }

  return (
    <Page>
      <PageHeader
        title="Single Flow Run"
        description="Pick a transaction type, fill the form, and publish a single flow (with optional chaos) to the configured Kafka cluster."
      />
      <PageContent>
        {/* Transaction type radio (always shown) */}
        <Card>
          <CardHeader className="space-y-3">
            <CardTitle>Transaction type</CardTitle>
            <div role="radiogroup" aria-label="Transaction type" className="flex flex-wrap gap-2">
              {catalogQuery.isLoading && runnerFlows.length === 0 ? (
                <div className="h-8 w-full animate-pulse rounded-md bg-muted/40" />
              ) : (
                runnerFlows.map(f => {
                  const active = f.flowType === flowType;
                  return (
                    <button
                      key={f.flowType}
                      type="button"
                      role="radio"
                      aria-checked={active}
                      onClick={() => {
                        setFlowType(f.flowType);
                        setResult(null);
                        setNTimesResult(null);
                        setFormError(null);
                        clearWatches();
                      }}
                      className={cn(
                        "rounded-md border px-3 py-1.5 text-xs transition-colors",
                        active
                          ? "border-primary bg-primary/10 font-medium text-primary"
                          : "border-border text-muted-foreground hover:bg-muted/50"
                      )}
                    >
                      {FLOW_LABELS[f.flowType] ?? f.lifecycle?.label ?? f.batchGroup?.label ?? f.flowType}
                    </button>
                  );
                })
              )}
            </div>
          </CardHeader>
        </Card>

        {selectedCatalog?.batchGroup ? (
          /* Batch disbursement: reservation form + Manual per-item wizard / Automatic run */
          <BatchDisbursementWizard
            key={selectedCatalog.flowType}
            catalog={selectedCatalog}
            catalogEntries={catalogQuery.data ?? []}
            token={token!}
          />
        ) : selectedCatalog?.lifecycle ? (
          /* Lifecycle (Settlement / Disbursement): outcome selector + wizard / RANDOM panel */
          <LifecycleWizard
            key={selectedCatalog.flowType}
            catalog={selectedCatalog}
            catalogEntries={catalogQuery.data ?? []}
            token={token!}
          />
        ) : (
          <>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
              {/* LEFT: transaction form widget */}
              <Card>
                <CardHeader>
                  <CardTitle>{selectedCatalog ? FLOW_LABELS[selectedCatalog.flowType] ?? selectedCatalog.flowType : "Transaction"}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-5">
                  {selectedCatalog ? (
                    <TransactionTypeForm
                      key={selectedCatalog.flowType}
                      catalog={selectedCatalog}
                      token={token!}
                      onChange={setAssembled}
                    />
                  ) : (
                    <p className="text-xs text-muted-foreground">No transaction types available.</p>
                  )}

                  {formError && <InlineNotice description={formError} tone="danger" />}

                  <div className="flex gap-2">
                    <Button onClick={handleSubmit} disabled={mutation.isPending || !selectedCatalog}>
                      {mutation.isPending ? (
                        <>
                          <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" />
                          Publishing…
                        </>
                      ) : (
                        <>
                          <Play className="mr-1.5 h-4 w-4" />
                          Run Flow
                        </>
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setResult(null);
                        setNTimesResult(null);
                        setFormError(null);
                        setChaos(INITIAL_CHAOS);
                        clearWatches();
                      }}
                    >
                      Reset
                    </Button>
                  </div>
                </CardContent>
              </Card>

              {/* RIGHT: chaos options widget */}
              <Card className="self-start">
                <CardHeader>
                  <CardTitle>Chaos options</CardTitle>
                </CardHeader>
                <CardContent>
                  <ChaosOptionsPanel value={chaos} onChange={setChaos} />
                </CardContent>
              </Card>
            </div>

            {result && (
              <FlowResultCard
                result={result}
                failure={
                  failures.find(f => f.transactionRequestId === result.transactionRequestId) ?? null
                }
              />
            )}
            {nTimesResult && <NTimesSyncResultCard result={nTimesResult} failures={failures} />}
          </>
        )}

        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-amber-500" />
                {chaos.strategy === "nTimes" ? "Confirm N Times run" : "Confirm destructive chaos"}
              </DialogTitle>
              <DialogDescription>
                {chaos.strategy === "nTimes" ? (
                  <>
                    You are about to run this flow <strong>{chaos.nTimesCount}</strong> times (
                    <strong>{chaos.nTimesMode}</strong>, <strong>{chaos.nTimesPacing}</strong>{" "}
                    pacing), producing that many distinct transactions against the same accounts.
                    Ensure you are targeting the correct Kafka cluster.
                  </>
                ) : (
                  <>
                    You are about to publish an intentionally <strong>{chaos.strategy}</strong>{" "}
                    event. This will deliberately stress the ledger. Ensure you are targeting the
                    correct Kafka cluster.
                  </>
                )}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setConfirmOpen(false)}>
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={() => {
                  setConfirmOpen(false);
                  mutation.mutate();
                }}
              >
                Confirm &amp; Run
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </PageContent>
    </Page>
  );
}

// Backwards-compatible alias for the existing route import.
export const SingleFlowPage = SingleFlowRunPage;
