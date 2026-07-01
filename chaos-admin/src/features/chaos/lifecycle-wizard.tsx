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
import { Input } from "@/components/ui/input";
import {
  getAccountReservations,
  runFlow,
  runRandomLifecycle,
  type ChaosOptions,
  type FlowCatalogEntry,
  type FlowResult,
  type PublishFlowRequest
} from "@/lib/api";
import { runDetailPath } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft, CheckCircle2, Loader2, Play, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  buildChaosOptions,
  ChaosOptionsPanel,
  CHAOS_LIMITS,
  INITIAL_CHAOS,
  isDestructive,
  type ChaosFormState
} from "./chaos-options-panel";
import { OutcomeSelector, type Outcome } from "./outcome-selector";
import { TransactionTypeForm, type AssembledFlow } from "./transaction-type-form";
import { useReservationWatch } from "./use-reservation-watch";
import { useTransactionFailureWatch } from "./use-transaction-failure-watch";

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

const RESERVATION_POLL_INTERVAL_MS = 1500;
const RESERVATION_POLL_TIMEOUT_MS = 15_000;

// `correlationIdOverride`, when provided, forces the published correlation_id — the manual lifecycle
// passes one stable id across its initiated + completed/failed publishes so Run History groups the
// two events under a single run (ADR-031); otherwise each form mint a fresh id and they fragment.
function toRequest(
  a: AssembledFlow,
  chaos: ChaosOptions | null,
  correlationIdOverride?: string
): PublishFlowRequest {
  return {
    correlationId: correlationIdOverride ?? (a.correlationId.trim() || null),
    tenantId: a.tenantId.trim() || null,
    channel: null,
    currency: a.currency.trim() || null,
    amount: a.amount ? parseFloat(a.amount) : null,
    grossAmount: null,
    netAmount: null,
    slotOverrides: a.slotOverrides,
    flowFields: a.flowFields,
    chaos,
    fees: a.fees
  };
}

/** The held step-1 field values keyed by descriptor name, for carry-over + the read-only summary. */
function capture(a: AssembledFlow): Record<string, string> {
  const out: Record<string, string> = { ...a.flowFields };
  if (a.currency) out["currency"] = a.currency;
  if (a.amount) out["amount"] = a.amount;
  for (const [slot, va] of Object.entries(a.slotOverrides)) out[slot] = va;
  return out;
}

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

/**
 * Drives a Settlement/Disbursement lifecycle: an Outcome selector plus either the interactive
 * two-step wizard (Succeed/Fail) or the unattended RANDOM bulk panel. Step 1 publishes the
 * `initiated` event; step 2 renders the initiated summary (read-only) above the prepopulated,
 * editable completed/failed form (carry-over applied) and publishes on confirm. For Disbursement,
 * the `reservation_id` is resolved between steps by polling the ledger read-proxy (timeout → manual).
 * RANDOM submits N distinct lifecycles to the backend runner and hands off to the run-results view.
 */
export function LifecycleWizard({
  catalog,
  catalogEntries,
  token
}: {
  catalog: FlowCatalogEntry;
  catalogEntries: FlowCatalogEntry[];
  token: string;
}) {
  const navigate = useNavigate();
  const lifecycle = catalog.lifecycle!;
  const isDisbursement = lifecycle.initiated === "DISBURSEMENT_INITIATED";

  const [outcome, setOutcome] = useState<Outcome>("SUCCEED");
  const [step, setStep] = useState<1 | 2>(1);

  const [step1Assembled, setStep1Assembled] = useState<AssembledFlow>(EMPTY_ASSEMBLED);
  const [step1Chaos, setStep1Chaos] = useState<ChaosFormState>(INITIAL_CHAOS);
  const [step1Values, setStep1Values] = useState<Record<string, string> | null>(null);

  const [step2Assembled, setStep2Assembled] = useState<AssembledFlow>(EMPTY_ASSEMBLED);
  const [step2Chaos, setStep2Chaos] = useState<ChaosFormState>(INITIAL_CHAOS);

  const [randomCount, setRandomCount] = useState(5);
  const [confirm, setConfirm] = useState<null | "step1" | "step2" | "random">(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<FlowResult | null>(null);
  // Request id(s) of the events published by this wizard, fed to the run-page failure watch so a
  // ledger rejection (e.g. an insufficient-funds settlement.initiated) raises a danger toast.
  const [failureWatchIds, setFailureWatchIds] = useState<string[]>([]);
  const [timedOut, setTimedOut] = useState(false);

  // "full" runs the two-step initiate→confirm wizard; "completion" skips initiation and publishes the
  // completed/failed event directly (resume a lifecycle initiated earlier — "leave and come back").
  const [mode, setMode] = useState<"full" | "completion">("full");
  const [resolvedReservation, setResolvedReservation] = useState<string | null>(null);
  const [lookupMiss, setLookupMiss] = useState(false);

  const secondaryType = outcome === "FAIL" ? lifecycle.failed : lifecycle.completed;
  const secondaryEntry = useMemo(
    () => catalogEntries.find(e => e.flowType === secondaryType) ?? null,
    [catalogEntries, secondaryType]
  );

  // Reset to a clean step 1 whenever the outcome changes.
  useEffect(() => {
    setStep(1);
    setStep1Values(null);
    setResult(null);
    setError(null);
    setTimedOut(false);
    setResolvedReservation(null);
    setLookupMiss(false);
    setFailureWatchIds([]);
  }, [outcome]);

  // Reset the transient state when switching between full and completion-only.
  useEffect(() => {
    setStep(1);
    setStep1Values(null);
    setResult(null);
    setError(null);
    setResolvedReservation(null);
    setLookupMiss(false);
    setFailureWatchIds([]);
  }, [mode]);

  const orgVaId = step1Values?.["virtual_account_id"] ?? "";
  const transactionId = step1Values?.["transaction_id"] ?? "";

  // Phase 019: once the initiated event is published, watch the reservation projection by this flow's
  // request id (disbursement → transaction_id, settlement → settlement_request_id) to toast on the
  // reservation's create + release/expire/capture. Independent of the ADR-018 sourcing poll below.
  const reservationWatchRef = useMemo(() => {
    if (!step1Values) return null;
    const field = isDisbursement ? "transaction_id" : "settlement_request_id";
    return step1Values[field] || null;
  }, [step1Values, isDisbursement]);
  useReservationWatch(reservationWatchRef);
  useTransactionFailureWatch(failureWatchIds);

  const reservationQuery = useQuery({
    queryKey: ["reservation", orgVaId, transactionId],
    queryFn: () => getAccountReservations(token, orgVaId, transactionId),
    enabled:
      outcome !== "RANDOM" &&
      step === 2 &&
      isDisbursement &&
      Boolean(orgVaId && transactionId) &&
      !timedOut,
    refetchInterval: q =>
      q.state.data && q.state.data.length > 0 ? false : RESERVATION_POLL_INTERVAL_MS
  });
  const reservationId = reservationQuery.data?.[0]?.id ?? null;
  const reservationStatus: "n/a" | "polling" | "found" | "timeout" = !isDisbursement
    ? "n/a"
    : reservationId
      ? "found"
      : timedOut
        ? "timeout"
        : "polling";

  // Bound the disbursement reservation poll with a timeout on entering step 2.
  useEffect(() => {
    if (step !== 2 || !isDisbursement || reservationId) return;
    const handle = setTimeout(() => setTimedOut(true), RESERVATION_POLL_TIMEOUT_MS);
    return () => clearTimeout(handle);
  }, [step, isDisbursement, reservationId]);

  const step2Initial = useMemo(() => {
    if (!secondaryEntry || !step1Values) return {};
    const names = new Set(secondaryEntry.fields.map(f => f.name));
    const initial: Record<string, string> = {};
    for (const co of lifecycle.carryOver) {
      const v = step1Values[co.fromField];
      if (names.has(co.toField) && v != null && v !== "") initial[co.toField] = v;
    }
    if (reservationId && names.has("reservation_id")) initial["reservation_id"] = reservationId;
    return initial;
  }, [secondaryEntry, step1Values, lifecycle.carryOver, reservationId]);

  // A fresh correlation id is minted at each initiated publish and reused by that run's secondary
  // publish, so Run History groups the two events as one run (ADR-031). Re-firing ("Run another")
  // mints a new id, so sequential runs stay distinct. Completion-only mode is a single publish and
  // keeps its own form correlation_id (no override).
  const [runCorrelationId, setRunCorrelationId] = useState("");

  const step1Mutation = useMutation<FlowResult>({
    mutationFn: () => {
      const cid = crypto.randomUUID();
      setRunCorrelationId(cid);
      return runFlow(
        token,
        lifecycle.initiated,
        toRequest(step1Assembled, buildChaosOptions(step1Chaos), cid)
      );
    },
    onSuccess: r => {
      if (r.status !== "PUBLISHED") {
        setError(r.error ?? "Initiated publish failed");
        return;
      }
      setError(null);
      setStep1Values(capture(step1Assembled));
      if (r.transactionRequestId) setFailureWatchIds([r.transactionRequestId]);
      setStep2Chaos(INITIAL_CHAOS);
      setTimedOut(false);
      setStep(2);
    },
    onError: err => setError(getErrorMessage(err))
  });

  const step2Mutation = useMutation<FlowResult>({
    mutationFn: () =>
      runFlow(
        token,
        secondaryType,
        toRequest(
          step2Assembled,
          buildChaosOptions(step2Chaos),
          // Full mode: reuse the initiated run's id so the pair groups as one run. Completion-only
          // mode is a single publish — keep the form's own correlation_id.
          mode === "full" ? runCorrelationId || undefined : undefined
        )
      ),
    onSuccess: r => {
      setError(null);
      setResult(r);
      const reqId = r.transactionRequestId;
      if (reqId) setFailureWatchIds(prev => (prev.includes(reqId) ? prev : [...prev, reqId]));
    },
    onError: err => setError(getErrorMessage(err))
  });

  const randomMutation = useMutation({
    mutationFn: () => {
      const count = Math.min(Math.max(randomCount, 1), CHAOS_LIMITS.maxNTimes);
      const req = toRequest(step1Assembled, {
        nTimes: { count, pacing: "BURST", mode: "ASYNC" }
      });
      return runRandomLifecycle(token, lifecycle.initiated, req);
    },
    onSuccess: run => navigate(runDetailPath(run.id)),
    onError: err => setError(getErrorMessage(err))
  });

  // Completion-only: resolve the disbursement reservation_id from the form's org VA + transaction_id
  // (the reservation was created when the lifecycle was initiated earlier). One-shot, on demand.
  const completionOrgVa = isDisbursement
    ? (outcome === "FAIL"
        ? step2Assembled.flowFields["virtual_account_id"]
        : step2Assembled.slotOverrides["source"]) ?? ""
    : "";
  const completionTxId = step2Assembled.flowFields["transaction_id"] ?? "";

  const lookupMutation = useMutation({
    mutationFn: () => getAccountReservations(token, completionOrgVa, completionTxId),
    onSuccess: rows => {
      const id = rows[0]?.id ?? null;
      setResolvedReservation(id);
      setLookupMiss(!id);
    },
    onError: () => setLookupMiss(true)
  });

  const injectedReservation = useMemo(
    () => (resolvedReservation ? { reservation_id: resolvedReservation } : undefined),
    [resolvedReservation]
  );

  const submitStep1 = () => {
    setError(null);
    if (step1Assembled.missingRequired.length > 0) {
      setError(`Required fields missing: ${step1Assembled.missingRequired.join(", ")}`);
      return;
    }
    if (isDestructive(step1Chaos.strategy)) {
      setConfirm("step1");
      return;
    }
    step1Mutation.mutate();
  };

  const submitStep2 = () => {
    setError(null);
    if (step2Assembled.missingRequired.length > 0) {
      setError(`Required fields missing: ${step2Assembled.missingRequired.join(", ")}`);
      return;
    }
    if (isDestructive(step2Chaos.strategy)) {
      setConfirm("step2");
      return;
    }
    step2Mutation.mutate();
  };

  const pending = step1Mutation.isPending || step2Mutation.isPending || randomMutation.isPending;

  // ---------------------------------------------------------------- RANDOM panel
  if (outcome === "RANDOM") {
    return (
      <>
        <OutcomeCard outcome={outcome} onOutcome={setOutcome} />
        <Card>
          <CardHeader>
            <CardTitle>{lifecycle.label} — Random (unattended)</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <TransactionTypeForm
              key={`random-${catalog.flowType}`}
              catalog={catalog}
              token={token}
              onChange={setStep1Assembled}
            />
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-xs font-medium">
                  Count — lifecycles to fire (max {CHAOS_LIMITS.maxNTimes})
                </label>
                <Input
                  type="number"
                  min={1}
                  max={CHAOS_LIMITS.maxNTimes}
                  value={randomCount}
                  onChange={e => setRandomCount(parseInt(e.target.value) || 1)}
                />
              </div>
            </div>
            <InlineNotice
              title="Random = unattended, N distinct lifecycles"
              description="The system publishes each lifecycle's initiated event, then decides Succeed or Fail at random and auto-publishes the completed/failed event — no confirmation per lifecycle. N-Times here means the number of lifecycles. You will be taken to the run-results view to watch progress."
              tone="default"
            />
            {error && <InlineNotice description={error} tone="danger" />}
            <Button
              onClick={() => setConfirm("random")}
              disabled={pending || step1Assembled.missingRequired.length > 0}
            >
              {pending ? (
                <>
                  <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Starting…
                </>
              ) : (
                <>
                  <Play className="mr-1.5 h-4 w-4" /> Run {Math.max(randomCount, 1)} lifecycles
                </>
              )}
            </Button>
          </CardContent>
        </Card>
        <ConfirmDialog
          open={confirm === "random"}
          onCancel={() => setConfirm(null)}
          onConfirm={() => {
            setConfirm(null);
            randomMutation.mutate();
          }}
          title="Confirm random lifecycle run"
          body={
            <>
              You are about to fire <strong>{Math.max(randomCount, 1)}</strong> distinct{" "}
              {lifecycle.label} lifecycles unattended (system decides Succeed/Fail per lifecycle).
              Ensure you are targeting the correct Kafka cluster.
            </>
          }
        />
      </>
    );
  }

  // ---------------------------------------------------------------- Done (step-2 published)
  if (result) {
    return (
      <>
        <OutcomeCard outcome={outcome} onOutcome={setOutcome} />
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-emerald-500" /> Lifecycle published
            </CardTitle>
            <Badge variant={result.status === "PUBLISHED" ? "success" : "destructive"}>
              {result.status}
            </Badge>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-xs text-muted-foreground">
              Published <strong>{lifecycle.initiated}</strong> then{" "}
              <strong>{secondaryType}</strong> for the same transaction.
            </p>
            <Summary values={{ "Secondary event": result.eventId, Topic: result.topic }} />
            <Button
              variant="outline"
              onClick={() => {
                setResult(null);
                setStep(1);
                setStep1Values(null);
                setFailureWatchIds([]);
              }}
            >
              Run another
            </Button>
          </CardContent>
        </Card>
      </>
    );
  }

  // ---------------------------------------------------------------- Interactive wizard
  const showStep2Form =
    step === 2 && secondaryEntry && (!isDisbursement || reservationStatus !== "polling");

  return (
    <>
      <OutcomeCard outcome={outcome} onOutcome={setOutcome} mode={mode} onMode={setMode} />

      {mode === "completion" ? (
        <Card>
          <CardHeader>
            <CardTitle>
              {lifecycle.label} — {outcome === "FAIL" ? "Fail" : "Complete"} (completion only)
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <InlineNotice
              title="Completion only — initiation skipped"
              description={
                isDisbursement
                  ? "Resume a lifecycle you initiated earlier: paste the real transaction_id below, then resolve or paste the reservation_id (the ledger relinks by transaction_id, so a placeholder also works)."
                  : "Resume a lifecycle you initiated earlier: paste the real settlement_request_id and the originating values below before publishing."
              }
              tone="default"
            />
            {isDisbursement && (
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setLookupMiss(false);
                    lookupMutation.mutate();
                  }}
                  disabled={!completionOrgVa || !completionTxId || lookupMutation.isPending}
                >
                  {lookupMutation.isPending ? (
                    <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                  ) : null}
                  Resolve reservation_id from ledger
                </Button>
                {resolvedReservation && (
                  <span className="font-mono text-xs text-emerald-600">
                    Found {resolvedReservation}
                  </span>
                )}
                {lookupMiss && (
                  <span className="text-xs text-amber-600">
                    Not found — enter it manually below.
                  </span>
                )}
                {(!completionOrgVa || !completionTxId) && (
                  <span className="text-xs text-muted-foreground">
                    Pick the org VA and enter transaction_id to enable lookup.
                  </span>
                )}
              </div>
            )}
            {secondaryEntry ? (
              <TransactionTypeForm
                key={`completion-${secondaryType}`}
                catalog={secondaryEntry}
                token={token}
                onChange={setStep2Assembled}
                injectedValues={injectedReservation}
              />
            ) : (
              <p className="text-xs text-muted-foreground">Loading form…</p>
            )}
            <ChaosBlock value={step2Chaos} onChange={setStep2Chaos} />
            {error && <InlineNotice description={error} tone="danger" />}
            <Button onClick={submitStep2} disabled={pending || !secondaryEntry}>
              {step2Mutation.isPending ? (
                <>
                  <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Publishing…
                </>
              ) : (
                <>
                  <Play className="mr-1.5 h-4 w-4" /> Publish{" "}
                  {outcome === "FAIL" ? "failed" : "completed"}
                </>
              )}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>
              {lifecycle.label} — {outcome === "FAIL" ? "Fail" : "Succeed"}
            </CardTitle>
            <Badge variant="neutral">Step {step} of 2</Badge>
          </CardHeader>
        <CardContent className="space-y-5">
          {step === 1 ? (
            <>
              <p className="text-xs text-muted-foreground">
                Step 1 — publish the <strong>{lifecycle.initiated}</strong> event. You will confirm
                the {outcome === "FAIL" ? "failed" : "completed"} form before it is published.
              </p>
              <TransactionTypeForm
                key={`step1-${catalog.flowType}`}
                catalog={catalog}
                token={token}
                onChange={setStep1Assembled}
              />
              <ChaosBlock value={step1Chaos} onChange={setStep1Chaos} />
              {error && <InlineNotice description={error} tone="danger" />}
              <Button onClick={submitStep1} disabled={pending}>
                {step1Mutation.isPending ? (
                  <>
                    <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Publishing…
                  </>
                ) : (
                  <>
                    <Play className="mr-1.5 h-4 w-4" /> Run initiated → step 2
                  </>
                )}
              </Button>
            </>
          ) : (
            <>
              <div className="rounded-lg border border-border bg-muted/30 p-3">
                <p className="mb-2 text-xs font-medium text-muted-foreground">
                  Initiated (published, read-only)
                </p>
                <Summary values={step1Values ?? {}} />
              </div>

              {isDisbursement && (
                <ReservationStatus status={reservationStatus} reservationId={reservationId} />
              )}

              {showStep2Form && secondaryEntry ? (
                <>
                  <p className="text-xs text-muted-foreground">
                    Step 2 — confirm the editable{" "}
                    <strong>{outcome === "FAIL" ? "failed" : "completed"}</strong> form (carry-over
                    applied), then publish.
                  </p>
                  <TransactionTypeForm
                    key={`step2-${secondaryType}-${reservationId ?? (timedOut ? "manual" : "na")}`}
                    catalog={secondaryEntry}
                    token={token}
                    initialValues={step2Initial}
                    onChange={setStep2Assembled}
                  />
                  <ChaosBlock value={step2Chaos} onChange={setStep2Chaos} />
                  {error && <InlineNotice description={error} tone="danger" />}
                  <div className="flex gap-2">
                    <Button variant="outline" onClick={() => setStep(1)} disabled={pending}>
                      <ArrowLeft className="mr-1.5 h-4 w-4" /> Back
                    </Button>
                    <Button onClick={submitStep2} disabled={pending}>
                      {step2Mutation.isPending ? (
                        <>
                          <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Publishing…
                        </>
                      ) : (
                        <>
                          <Play className="mr-1.5 h-4 w-4" /> Confirm &amp; publish{" "}
                          {outcome === "FAIL" ? "failed" : "completed"}
                        </>
                      )}
                    </Button>
                  </div>
                </>
              ) : (
                <Button variant="outline" onClick={() => setStep(1)}>
                  <ArrowLeft className="mr-1.5 h-4 w-4" /> Back
                </Button>
              )}
            </>
          )}
        </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirm === "step1" || confirm === "step2"}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          const which = confirm;
          setConfirm(null);
          if (which === "step1") step1Mutation.mutate();
          else if (which === "step2") step2Mutation.mutate();
        }}
        title="Confirm destructive chaos"
        body={
          <>
            You are about to publish an intentionally{" "}
            <strong>{(confirm === "step1" ? step1Chaos : step2Chaos).strategy}</strong> event. This
            will deliberately stress the ledger. Ensure you are targeting the correct Kafka cluster.
          </>
        }
      />
    </>
  );
}

function OutcomeCard({
  outcome,
  onOutcome,
  mode,
  onMode
}: {
  outcome: Outcome;
  onOutcome: (o: Outcome) => void;
  mode?: "full" | "completion";
  onMode?: (m: "full" | "completion") => void;
}) {
  return (
    <Card>
      <CardHeader className="space-y-3">
        <CardTitle>Outcome</CardTitle>
        <div className="flex flex-wrap items-center gap-3">
          <OutcomeSelector value={outcome} onChange={onOutcome} />
          {mode && onMode && (
            <label
              title="Skip initiation — publish the completed/failed event for a lifecycle initiated earlier"
              className={cn(
                "flex items-center gap-1.5 text-xs",
                outcome === "RANDOM"
                  ? "cursor-not-allowed text-muted-foreground/50"
                  : "cursor-pointer text-muted-foreground"
              )}
            >
              <input
                type="checkbox"
                checked={mode === "completion"}
                disabled={outcome === "RANDOM"}
                onChange={e => onMode(e.target.checked ? "completion" : "full")}
                className="h-3.5 w-3.5 rounded border-border accent-primary"
              />
              Skip initiated event
            </label>
          )}
        </div>
      </CardHeader>
    </Card>
  );
}

function ChaosBlock({
  value,
  onChange
}: {
  value: ChaosFormState;
  onChange: (next: ChaosFormState) => void;
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="mb-2 text-xs font-medium">Chaos for this event</p>
      {/* N-Times does not apply to interactive Succeed/Fail outcomes. */}
      <ChaosOptionsPanel value={value} onChange={onChange} hideNTimes />
    </div>
  );
}

function ReservationStatus({
  status,
  reservationId
}: {
  status: "n/a" | "polling" | "found" | "timeout";
  reservationId: string | null;
}) {
  if (status === "polling") {
    return (
      <div className="flex items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        Resolving reservation_id from the ledger…
      </div>
    );
  }
  if (status === "found") {
    return (
      <InlineNotice
        title="Reservation resolved"
        description={`reservation_id ${reservationId} prefilled into the form below.`}
        tone="default"
      />
    );
  }
  if (status === "timeout") {
    return (
      <InlineNotice
        title="Reservation not found before timeout"
        description="Enter the reservation_id manually in the form below (paste the id observed from the ledger). The ledger relinks by transaction_id, so the value is cosmetic."
        tone="warning"
      />
    );
  }
  return null;
}

function Summary({ values }: { values: Record<string, string> }) {
  const entries = Object.entries(values).filter(([, v]) => v != null && v !== "");
  if (entries.length === 0) return null;
  return (
    <dl className="grid grid-cols-2 gap-2">
      {entries.map(([k, v]) => (
        <div key={k}>
          <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">{k}</dt>
          <dd className="mt-0.5 break-all font-mono text-[11px] text-muted-foreground">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

function ConfirmDialog({
  open,
  onCancel,
  onConfirm,
  title,
  body
}: {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  title: string;
  body: React.ReactNode;
}) {
  return (
    <Dialog open={open} onOpenChange={o => (o ? undefined : onCancel())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-500" />
            {title}
          </DialogTitle>
          <DialogDescription>{body}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={onConfirm}>
            Confirm &amp; Run
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
