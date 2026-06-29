import { InlineNotice } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  getDisbursementBatch,
  listVirtualAccounts,
  runDisbursementBatch,
  runFlow,
  type BatchDisbursementRunRequest,
  type BatchOutcomeMode,
  type ChaosOptions,
  type FlowCatalogEntry,
  type PublishFlowRequest
} from "@/lib/api";
import { cn } from "@/lib/utils";
import { ulid } from "@/lib/ulid";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, Loader2, Play, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  buildChaosOptions,
  ChaosOptionsPanel,
  INITIAL_CHAOS,
  type ChaosFormState
} from "./chaos-options-panel";
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
const SPLIT_SCALE = 4;
const FAILURE_CODES = [
  "PROVIDER_REJECTED",
  "PROVIDER_TIMEOUT",
  "RECIPIENT_INVALID",
  "VALIDATION_FAILED",
  "PROVIDER_UNAVAILABLE",
  "RESERVATION_MISSING",
  "SUBTYPE_UNSUPPORTED"
];

type Mode = "manual" | "automatic";

function toRequest(a: AssembledFlow, chaos: ChaosOptions | null): PublishFlowRequest {
  return {
    correlationId: a.correlationId.trim() || null,
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

/** Captures the reservation field values keyed by descriptor name (VA fields resolved back from slots). */
function captureByName(entry: FlowCatalogEntry, a: AssembledFlow): Record<string, string> {
  const out: Record<string, string> = { ...a.flowFields };
  if (a.currency) out["currency"] = a.currency;
  for (const d of entry.fields) {
    if (d.kind === "VA_REF" && d.slotName) {
      const v = a.slotOverrides[d.slotName];
      if (v) out[d.name] = v;
    }
  }
  return out;
}

/** Even split of a decimal total across N (last item absorbs the remainder), mirroring the backend. */
function splitEven(totalStr: string, n: number, scale = SPLIT_SCALE): string[] {
  const factor = 10 ** scale;
  const total = Math.round((parseFloat(totalStr) || 0) * factor);
  const per = Math.floor(total / n);
  const out: number[] = [];
  let acc = 0;
  for (let i = 0; i < n - 1; i++) {
    out.push(per);
    acc += per;
  }
  out.push(total - acc);
  return out.map(u => (u / factor).toFixed(scale));
}

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

/**
 * Drives the batch-disbursement fan-out: a reservation form (step 1) with a Mode toggle, then either
 * the interactive per-item wizard (Manual — cycle N items, Pass/Fail each, with carry-over + split
 * prefill + per-event chaos) or an unattended automatic run (Automatic — split + outcome policy →
 * `disbursement-batch/run` → run-results). Resolves the ledger `reservation_id` by polling the
 * batch-summary read-proxy (timeout → manual entry); the progress panel renders the ledger's live
 * counters.
 */
export function BatchDisbursementWizard({
  catalog,
  catalogEntries,
  token
}: {
  catalog: FlowCatalogEntry;
  catalogEntries: FlowCatalogEntry[];
  token: string;
}) {
  const navigate = useNavigate();
  const group = catalog.batchGroup!;
  const itemRequestEntry = catalogEntries.find(e => e.flowType === group.itemRequest) ?? null;

  const [mode, setMode] = useState<Mode>("manual");
  const [phase, setPhase] = useState<"reservation" | "items" | "done">("reservation");

  const [resvAssembled, setResvAssembled] = useState<AssembledFlow>(EMPTY_ASSEMBLED);
  const [resvChaos, setResvChaos] = useState<ChaosFormState>(INITIAL_CHAOS);
  const [resvValues, setResvValues] = useState<Record<string, string> | null>(null);
  const [batchId, setBatchId] = useState("");
  const [timedOut, setTimedOut] = useState(false);
  const [manualReservation, setManualReservation] = useState("");

  // Automatic-mode controls.
  const [outcomeMode, setOutcomeMode] = useState<BatchOutcomeMode>("ALL_PASS");
  const [passCount, setPassCount] = useState(1);
  const [feeVaId, setFeeVaId] = useState("");
  const [autoChaos, setAutoChaos] = useState<ChaosFormState>(INITIAL_CHAOS);

  // Manual per-item state.
  const [itemIndex, setItemIndex] = useState(0);
  const [itemAssembled, setItemAssembled] = useState<AssembledFlow>(EMPTY_ASSEMBLED);
  const [itemChaos, setItemChaos] = useState<ChaosFormState>(INITIAL_CHAOS);
  const [itemPass, setItemPass] = useState(true);
  const [providerId, setProviderId] = useState("PROVIDER_GH");
  const [failureReason, setFailureReason] = useState("Batch item disbursement failed");
  const [failureCode, setFailureCode] = useState("RECIPIENT_INVALID");
  const [publishedItems, setPublishedItems] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // Request ids published by this wizard (batch_id + each item_id), fed to the run-page failure watch
  // so a ledger rejection of the reservation or an item raises a danger toast.
  const [failureWatchIds, setFailureWatchIds] = useState<string[]>([]);

  const totalPrincipal = resvAssembled.flowFields["total_principal_amount"] ?? "";
  const totalFees = resvAssembled.flowFields["total_fees"] ?? "";
  const itemCount = Math.max(1, parseInt(resvAssembled.flowFields["item_count"] ?? "0") || 0);
  const totalAmount = (
    (parseFloat(totalPrincipal) || 0) + (parseFloat(totalFees) || 0)
  ).toFixed(SPLIT_SCALE);
  const sourceVa = resvAssembled.slotOverrides["source"] ?? "";
  const destVa = resvAssembled.slotOverrides["destination"] ?? "";

  const principalSplit = useMemo(
    () => (itemCount > 0 ? splitEven(totalPrincipal || "0", itemCount) : []),
    [totalPrincipal, itemCount]
  );
  const feeSplit = useMemo(
    () => (itemCount > 0 ? splitEven(totalFees || "0", itemCount) : []),
    [totalFees, itemCount]
  );

  const systemVasQuery = useQuery({
    queryKey: ["vas", "SYSTEM"],
    queryFn: () => listVirtualAccounts(token, { ownershipType: "SYSTEM", perPage: 200 }),
    enabled: Boolean(token)
  });

  // Poll the ledger batch summary by batch_id (reservation_id + live counters) while cycling items.
  const summaryQuery = useQuery({
    queryKey: ["disbursement-batch", batchId],
    queryFn: () => getDisbursementBatch(token, batchId),
    enabled: phase === "items" && Boolean(batchId),
    refetchInterval: RESERVATION_POLL_INTERVAL_MS
  });
  const polledReservation = summaryQuery.data?.reservationId ?? null;
  const reservationId = polledReservation || manualReservation || null;

  // Phase 019: watch the batch reservation lifecycle by batch_id (one created + N partial releases) to
  // toast on the aggregate reservation's create + resolution. Independent of the read-proxy summary
  // poll above; deduped/capped so the fan-out doesn't spam.
  useReservationWatch(batchId || null, { kind: "batchId" });
  useTransactionFailureWatch(failureWatchIds);

  // Bound the reservation poll with a timeout once we enter the item phase.
  useEffect(() => {
    if (phase !== "items" || polledReservation) return;
    const handle = setTimeout(() => setTimedOut(true), RESERVATION_POLL_TIMEOUT_MS);
    return () => clearTimeout(handle);
  }, [phase, polledReservation]);

  // Carry-over + split prefill for the current item's request form.
  const itemInitial = useMemo(() => {
    if (!itemRequestEntry || !resvValues) return {};
    const names = new Set(itemRequestEntry.fields.map(f => f.name));
    const init: Record<string, string> = {};
    for (const co of group.reservationToItem) {
      const v = resvValues[co.fromField];
      if (names.has(co.toField) && v) init[co.toField] = v;
    }
    init["item_sequence"] = String(itemIndex + 1);
    if (principalSplit[itemIndex]) init["principal_amount"] = principalSplit[itemIndex];
    if (feeSplit[itemIndex]) init["item_fee"] = feeSplit[itemIndex];
    return init;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itemRequestEntry, resvValues, itemIndex, principalSplit, feeSplit]);

  const reservationMutation = useMutation({
    mutationFn: () => runFlow(token, group.reservation, toRequest(resvAssembled, buildChaosOptions(resvChaos))),
    onSuccess: r => {
      if (r.status !== "PUBLISHED") {
        setError(r.error ?? "Reservation publish failed");
        return;
      }
      setError(null);
      const captured = captureByName(catalog, resvAssembled);
      setResvValues(captured);
      setBatchId(captured["batch_id"] ?? "");
      if (r.transactionRequestId) setFailureWatchIds([r.transactionRequestId]);
      setTimedOut(false);
      setItemIndex(0);
      setPublishedItems(0);
      setPhase("items");
    },
    onError: err => setError(getErrorMessage(err))
  });

  const itemMutation = useMutation({
    mutationFn: async () => {
      // 1. item.request
      await runFlow(token, group.itemRequest, toRequest(itemAssembled, buildChaosOptions(itemChaos)));
      // 2. terminal: completed | failed
      const ff = itemAssembled.flowFields;
      const itemFee = ff["item_fee"] ?? "0";
      const fees =
        feeVaId && parseFloat(itemFee) > 0
          ? [{ feeType: "PLATFORM", amount: itemFee, feeCode: ulid(), destinationVaId: feeVaId }]
          : [];
      const terminalFields: Record<string, string> = {
        batch_id: ff["batch_id"] ?? batchId,
        item_id: ff["item_id"] ?? "",
        item_sequence: ff["item_sequence"] ?? String(itemIndex + 1),
        virtual_account_id: ff["virtual_account_id"] ?? sourceVa,
        reservation_id: reservationId ?? "",
        disbursement_subtype: ff["disbursement_subtype"] ?? "DOMESTIC",
        provider_id: providerId,
        provider_reference_id: ulid(),
        principal_amount: ff["principal_amount"] ?? "0",
        merchant_item_ref: ff["merchant_item_ref"] ?? ulid()
      };
      if (ff["destination_country"]) terminalFields["destination_country"] = ff["destination_country"];
      if (ff["corridor"]) terminalFields["corridor"] = ff["corridor"];
      if (!itemPass) {
        terminalFields["failure_reason"] = failureReason;
        terminalFields["failure_code"] = failureCode;
      }
      const terminalType = itemPass ? group.itemCompleted : group.itemFailed;
      const terminalReq: PublishFlowRequest = {
        correlationId: itemAssembled.correlationId.trim() || resvValues?.["correlation_id"] || null,
        tenantId: itemAssembled.tenantId.trim() || null,
        channel: null,
        currency: itemAssembled.currency.trim() || resvValues?.["currency"] || null,
        amount: null,
        grossAmount: null,
        netAmount: null,
        slotOverrides: { source: sourceVa, destination: destVa },
        flowFields: terminalFields,
        chaos: buildChaosOptions(itemChaos),
        fees
      };
      return runFlow(token, terminalType, terminalReq);
    },
    onSuccess: r => {
      setError(null);
      const reqId = r.transactionRequestId;
      if (reqId) setFailureWatchIds(prev => (prev.includes(reqId) ? prev : [...prev, reqId]));
      setPublishedItems(c => c + 1);
      summaryQuery.refetch();
      if (itemIndex + 1 >= itemCount) {
        setPhase("done");
      } else {
        setItemIndex(i => i + 1);
        setItemPass(true);
        setItemChaos(INITIAL_CHAOS);
      }
    },
    onError: err => setError(getErrorMessage(err))
  });

  const automaticMutation = useMutation({
    mutationFn: () => {
      const body: BatchDisbursementRunRequest = {
        sourceVaId: sourceVa,
        destinationVaId: destVa,
        merchantId: resvAssembled.flowFields["merchant_id"] ?? "",
        currency: resvAssembled.currency.trim() || null,
        totalPrincipalAmount: totalPrincipal || null,
        totalFees: totalFees || null,
        itemCount,
        disbursementSubtype: resvAssembled.flowFields["disbursement_subtype"] ?? null,
        merchantBatchRef: resvAssembled.flowFields["merchant_batch_ref"] ?? null,
        callbackUrl: resvAssembled.flowFields["callback_url"] ?? null,
        authorisedUserId: resvAssembled.flowFields["authorised_user_id"] ?? null,
        authorisedKeyFingerprint: resvAssembled.flowFields["authorised_key_fingerprint"] ?? null,
        tenantId: resvAssembled.tenantId.trim() || null,
        correlationId: resvAssembled.correlationId.trim() || null,
        splitMode: "EVEN",
        outcomePolicy: {
          mode: outcomeMode,
          passCount: outcomeMode === "COUNT" || outcomeMode === "RANDOM" ? passCount : null
        },
        providerId,
        feeVaId: feeVaId || null,
        failureCode,
        failureReason,
        chaos: buildChaosOptions(autoChaos)
      };
      return runDisbursementBatch(token, body);
    },
    onSuccess: run => navigate(`/chaos/batches/${run.id}`),
    onError: err => setError(getErrorMessage(err))
  });

  const reservationStatus: "polling" | "found" | "timeout" = polledReservation
    ? "found"
    : timedOut
      ? "timeout"
      : "polling";

  function submitReservationManual() {
    setError(null);
    if (resvAssembled.missingRequired.length > 0) {
      setError(`Required fields missing: ${resvAssembled.missingRequired.join(", ")}`);
      return;
    }
    reservationMutation.mutate();
  }

  function submitAutomatic() {
    setError(null);
    if (resvAssembled.missingRequired.length > 0) {
      setError(`Required fields missing: ${resvAssembled.missingRequired.join(", ")}`);
      return;
    }
    if ((outcomeMode === "COUNT" || outcomeMode === "RANDOM") && (passCount < 0 || passCount > itemCount)) {
      setError(`Pass count must be between 0 and ${itemCount}.`);
      return;
    }
    automaticMutation.mutate();
  }

  // ---------------------------------------------------------------- Done
  if (phase === "done") {
    return (
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <CardTitle className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-500" /> Batch complete
          </CardTitle>
          <Badge variant="success">
            {publishedItems}/{itemCount} items published
          </Badge>
        </CardHeader>
        <CardContent className="space-y-4">
          <ProgressPanel
            batchId={batchId}
            summary={summaryQuery.data}
            published={publishedItems}
            total={itemCount}
          />
          <Button
            variant="outline"
            onClick={() => {
              setPhase("reservation");
              setResvValues(null);
              setBatchId("");
              setManualReservation("");
              setPublishedItems(0);
              setItemIndex(0);
              setFailureWatchIds([]);
            }}
          >
            Run another batch
          </Button>
        </CardContent>
      </Card>
    );
  }

  // ---------------------------------------------------------------- Item wizard (manual)
  if (phase === "items") {
    return (
      <div className="space-y-4">
        <ProgressPanel
          batchId={batchId}
          summary={summaryQuery.data}
          published={publishedItems}
          total={itemCount}
        />
        {reservationStatus === "polling" && (
          <div className="flex items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Resolving reservation_id from the ledger…
          </div>
        )}
        {reservationStatus === "timeout" && !manualReservation && (
          <InlineNotice
            title="Reservation not resolved before timeout"
            description="Enter the reservation_id manually below (the ledger relinks by batch_id, so the value is cosmetic). Items can still be published."
            tone="warning"
          />
        )}
        {reservationStatus === "timeout" && (
          <div className="space-y-1">
            <label className="text-xs font-medium">Reservation ID (manual)</label>
            <Input
              value={manualReservation}
              onChange={e => setManualReservation(e.target.value)}
              placeholder="paste reservation_id…"
              className="font-mono text-xs"
            />
          </div>
        )}

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>
              Item {itemIndex + 1} of {itemCount}
            </CardTitle>
            <PassFailToggle pass={itemPass} onChange={setItemPass} />
          </CardHeader>
          <CardContent className="space-y-5">
            {itemRequestEntry ? (
              <TransactionTypeForm
                key={`item-${itemIndex}`}
                catalog={itemRequestEntry}
                token={token}
                initialValues={itemInitial}
                onChange={setItemAssembled}
              />
            ) : (
              <p className="text-xs text-muted-foreground">Loading item form…</p>
            )}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="space-y-1">
                <label className="text-xs font-medium">Provider ID</label>
                <Input value={providerId} onChange={e => setProviderId(e.target.value)} className="text-xs" />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-medium">Fee revenue VA (optional)</label>
                <Select
                  value={feeVaId}
                  onChange={setFeeVaId}
                  options={(systemVasQuery.data?.items ?? []).map(v => ({
                    value: v.vaId,
                    label: `${v.name} · ${v.currency}`
                  }))}
                  placeholder="Select fee VA…"
                  searchable
                />
              </div>
              {!itemPass && (
                <>
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Failure reason</label>
                    <Input
                      value={failureReason}
                      onChange={e => setFailureReason(e.target.value)}
                      className="text-xs"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-xs font-medium">Failure code</label>
                    <Select
                      value={failureCode}
                      onChange={setFailureCode}
                      options={FAILURE_CODES.map(c => ({ value: c, label: c }))}
                    />
                  </div>
                </>
              )}
            </div>

            <div className="rounded-lg border border-border p-3">
              <p className="mb-2 text-xs font-medium">Chaos for this item</p>
              <ChaosOptionsPanel value={itemChaos} onChange={setItemChaos} hideNTimes />
            </div>

            {error && <InlineNotice description={error} tone="danger" />}
            <Button onClick={() => itemMutation.mutate()} disabled={itemMutation.isPending || !itemRequestEntry}>
              {itemMutation.isPending ? (
                <>
                  <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Publishing item…
                </>
              ) : (
                <>
                  <Play className="mr-1.5 h-4 w-4" /> Publish item {itemIndex + 1} ({itemPass ? "pass" : "fail"})
                </>
              )}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  // ---------------------------------------------------------------- Reservation (step 1) + mode
  return (
    <>
      <Card>
        <CardHeader className="space-y-3">
          <CardTitle>Mode</CardTitle>
          <div role="radiogroup" aria-label="Batch mode" className="flex gap-2">
            {(["manual", "automatic"] as Mode[]).map(m => (
              <button
                key={m}
                type="button"
                role="radio"
                aria-checked={mode === m}
                onClick={() => setMode(m)}
                className={cn(
                  "rounded-md border px-3 py-1.5 text-xs capitalize transition-colors",
                  mode === m
                    ? "border-primary bg-primary/10 font-medium text-primary"
                    : "border-border text-muted-foreground hover:bg-muted/50"
                )}
              >
                {m}
              </button>
            ))}
          </div>
        </CardHeader>
      </Card>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <Card>
          <CardHeader>
            <CardTitle>Batch reservation</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <TransactionTypeForm
              key={`reservation-${catalog.flowType}`}
              catalog={catalog}
              token={token}
              onChange={setResvAssembled}
            />
            <SplitPreview
              totalAmount={totalAmount}
              itemCount={itemCount}
              principal={principalSplit}
              fee={feeSplit}
            />
            {error && <InlineNotice description={error} tone="danger" />}
            {mode === "manual" ? (
              <Button onClick={submitReservationManual} disabled={reservationMutation.isPending}>
                {reservationMutation.isPending ? (
                  <>
                    <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Publishing reservation…
                  </>
                ) : (
                  <>
                    <Play className="mr-1.5 h-4 w-4" /> Publish reservation → cycle {itemCount} items
                  </>
                )}
              </Button>
            ) : (
              <Button onClick={submitAutomatic} disabled={automaticMutation.isPending}>
                {automaticMutation.isPending ? (
                  <>
                    <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" /> Starting run…
                  </>
                ) : (
                  <>
                    <Play className="mr-1.5 h-4 w-4" /> Run batch ({itemCount} items, {outcomeMode})
                  </>
                )}
              </Button>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          {mode === "automatic" && (
            <Card className="self-start">
              <CardHeader>
                <CardTitle>Outcome policy</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <OutcomePolicyControls
                  mode={outcomeMode}
                  onMode={setOutcomeMode}
                  passCount={passCount}
                  onPassCount={setPassCount}
                  itemCount={itemCount}
                />
                <div className="space-y-1">
                  <label className="text-xs font-medium">Fee revenue VA (optional)</label>
                  <Select
                    value={feeVaId}
                    onChange={setFeeVaId}
                    options={(systemVasQuery.data?.items ?? []).map(v => ({
                      value: v.vaId,
                      label: `${v.name} · ${v.currency}`
                    }))}
                    placeholder="Select fee VA…"
                    searchable
                  />
                </div>
              </CardContent>
            </Card>
          )}
          <Card className="self-start">
            <CardHeader>
              <CardTitle>Chaos options</CardTitle>
            </CardHeader>
            <CardContent>
              <ChaosOptionsPanel
                value={mode === "manual" ? resvChaos : autoChaos}
                onChange={mode === "manual" ? setResvChaos : setAutoChaos}
                hideNTimes
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

function SplitPreview({
  totalAmount,
  itemCount,
  principal,
  fee
}: {
  totalAmount: string;
  itemCount: number;
  principal: string[];
  fee: string[];
}) {
  return (
    <div className="rounded-lg border border-border bg-muted/20 p-3 text-xs">
      <div className="flex flex-wrap gap-x-6 gap-y-1">
        <span>
          <span className="text-muted-foreground">Total amount (principal + fees): </span>
          <span className="font-mono font-medium">{totalAmount}</span>
        </span>
        <span>
          <span className="text-muted-foreground">Even split per item ({itemCount}): </span>
          <span className="font-mono">
            {principal[0] ?? "—"} + {fee[0] ?? "—"} fee
          </span>
        </span>
      </div>
      {itemCount > 1 && (
        <p className="mt-1 text-[11px] text-muted-foreground">
          Last item absorbs the rounding remainder: {principal[itemCount - 1] ?? "—"} +{" "}
          {fee[itemCount - 1] ?? "—"} fee.
        </p>
      )}
    </div>
  );
}

function OutcomePolicyControls({
  mode,
  onMode,
  passCount,
  onPassCount,
  itemCount
}: {
  mode: BatchOutcomeMode;
  onMode: (m: BatchOutcomeMode) => void;
  passCount: number;
  onPassCount: (n: number) => void;
  itemCount: number;
}) {
  const options: { value: BatchOutcomeMode; label: string }[] = [
    { value: "ALL_PASS", label: "All Pass" },
    { value: "ALL_FAIL", label: "All Fail" },
    { value: "COUNT", label: "K Pass" },
    { value: "RANDOM", label: "Random" }
  ];
  return (
    <div className="space-y-2">
      <div role="radiogroup" aria-label="Outcome policy" className="flex flex-wrap gap-2">
        {options.map(o => (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={mode === o.value}
            onClick={() => onMode(o.value)}
            className={cn(
              "rounded-md border px-2.5 py-1 text-xs transition-colors",
              mode === o.value
                ? "border-primary bg-primary/10 font-medium text-primary"
                : "border-border text-muted-foreground hover:bg-muted/50"
            )}
          >
            {o.label}
          </button>
        ))}
      </div>
      {(mode === "COUNT" || mode === "RANDOM") && (
        <div className="space-y-1">
          <label className="text-xs font-medium">
            {mode === "COUNT" ? "Items that pass" : "Target pass count (optional)"} — 0…{itemCount}
          </label>
          <Input
            type="number"
            min={0}
            max={itemCount}
            value={passCount}
            onChange={e => onPassCount(parseInt(e.target.value) || 0)}
            className="text-xs"
          />
        </div>
      )}
    </div>
  );
}

function PassFailToggle({ pass, onChange }: { pass: boolean; onChange: (p: boolean) => void }) {
  return (
    <div className="flex gap-1.5">
      <button
        type="button"
        onClick={() => onChange(true)}
        className={cn(
          "rounded-md border px-2.5 py-1 text-xs transition-colors",
          pass ? "border-emerald-500 bg-emerald-500/10 font-medium text-emerald-600" : "border-border text-muted-foreground"
        )}
      >
        Pass
      </button>
      <button
        type="button"
        onClick={() => onChange(false)}
        className={cn(
          "rounded-md border px-2.5 py-1 text-xs transition-colors",
          !pass ? "border-destructive bg-destructive/10 font-medium text-destructive" : "border-border text-muted-foreground"
        )}
      >
        Fail
      </button>
    </div>
  );
}

function ProgressPanel({
  batchId,
  summary,
  published,
  total
}: {
  batchId: string;
  summary: import("@/lib/api").DisbursementBatchSummary | undefined;
  published: number;
  total: number;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Batch progress</CardTitle>
        <Badge variant="neutral">{summary?.status ?? "—"}</Badge>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: "Published (client)", value: `${published}/${total}` },
            { label: "Processed (ledger)", value: summary ? String(summary.processedCount) : "—" },
            { label: "Failed (ledger)", value: summary ? String(summary.failedCount) : "—" },
            { label: "Pending (ledger)", value: summary ? String(summary.pendingCount) : "—" },
            { label: "Batch ID", value: batchId, mono: true },
            { label: "Reservation ID", value: summary?.reservationId ?? "—", mono: true }
          ].map(f => (
            <div key={f.label}>
              <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">{f.label}</dt>
              <dd className={cn("mt-0.5 break-all text-xs", f.mono && "font-mono text-muted-foreground")}>
                {f.value}
              </dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}
