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
import { LifecycleWizard } from "@/features/chaos/lifecycle-wizard";
import {
  TransactionTypeForm,
  type AssembledFlow
} from "@/features/chaos/transaction-type-form";
import {
  getFlowCatalog,
  publishNTimes,
  runFlow,
  type BatchRunResponse,
  type FlowResult,
  type NTimesSyncResult,
  type PublishFlowRequest
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
  "DISBURSEMENT_INITIATED"
] as const;

const FLOW_LABELS: Record<string, string> = {
  TOPUP_CONFIRMED: "Top-up",
  TRANSFER_REQUESTED: "Inter-VA Transfer",
  TREASURY_SWEEP_COMPLETED: "Treasury Sweep",
  TREASURY_PREFUND_COMPLETED: "Treasury Prefund",
  TREASURY_TRANSFER_COMPLETED: "Treasury Transfer",
  COLLECTION_COMPLETED: "Collection",
  SETTLEMENT_INITIATED: "Settlement",
  DISBURSEMENT_INITIATED: "Disbursement"
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

function FlowResultCard({ result }: { result: FlowResult }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Flow Result</CardTitle>
        <Badge variant={result.status === "PUBLISHED" ? "success" : "destructive"}>
          {result.status}
        </Badge>
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
      </CardContent>
    </Card>
  );
}

function NTimesSyncResultCard({ result }: { result: NTimesSyncResult }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>N Times Result</CardTitle>
        <Badge variant={result.failed === 0 ? "success" : "destructive"}>
          {result.succeeded}/{result.count} published
        </Badge>
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
  | { type: "flow"; result: FlowResult }
  | { type: "ntimesSync"; result: NTimesSyncResult }
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

  const mutation = useMutation<RunOutcome>({
    mutationFn: async () => {
      const req = buildRequest();
      if (chaos.strategy === "nTimes") {
        const res = await publishNTimes(token!, flowType, req);
        return res.kind === "async"
          ? { type: "ntimesAsync", run: res.run }
          : { type: "ntimesSync", result: res.result };
      }
      return { type: "flow", result: await runFlow(token!, flowType, req) };
    },
    onSuccess: outcome => {
      setFormError(null);
      if (outcome.type === "ntimesAsync") {
        navigate(`/chaos/batches/${outcome.run.id}`);
        return;
      }
      if (outcome.type === "ntimesSync") {
        setNTimesResult(outcome.result);
        setResult(null);
        return;
      }
      setResult(outcome.result);
      setNTimesResult(null);
    },
    onError: err => setFormError(getErrorMessage(err))
  });

  function handleSubmit() {
    setFormError(null);
    setResult(null);
    setNTimesResult(null);
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
                      }}
                      className={cn(
                        "rounded-md border px-3 py-1.5 text-xs transition-colors",
                        active
                          ? "border-primary bg-primary/10 font-medium text-primary"
                          : "border-border text-muted-foreground hover:bg-muted/50"
                      )}
                    >
                      {FLOW_LABELS[f.flowType] ?? f.lifecycle?.label ?? f.flowType}
                    </button>
                  );
                })
              )}
            </div>
          </CardHeader>
        </Card>

        {selectedCatalog?.lifecycle ? (
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

            {result && <FlowResultCard result={result} />}
            {nTimesResult && <NTimesSyncResultCard result={nTimesResult} />}
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
