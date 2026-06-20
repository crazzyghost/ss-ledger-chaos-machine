import { InlineNotice } from "@/components/layout/state-panel";
import { JsonPanel } from "@/components/layout/json-panel";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel } from "@/components/layout/state-panel";
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
import { Select } from "@/components/ui/select";
import { useSession } from "@/features/auth/session-provider";
import {
  ApiError,
  getFlowCatalog,
  listFlowConfigs,
  runFlow,
  type ChaosOptions,
  type FlowCatalogEntry,
  type FlowResult,
  type PublishFlowRequest
} from "@/lib/api";
import { formatEnumValue } from "@/lib/utils";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, Play, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

const CHAOS_LIMITS = {
  maxDuplicates: 10,
  maxBurst: 100,
  maxRatePerSecond: 1000,
  maxDelayMs: 30_000
} as const;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Chaos Options Panel
// ---------------------------------------------------------------------------

type ChaosFormState = {
  strategy: "" | "duplicate" | "outOfOrder" | "malformed" | "unbalanced" | "burst" | "delay";
  duplicateCount: number;
  malformedMutations: string;
  unbalancedDelta: number;
  burstCount: number;
  burstRate: number;
  delayMs: number;
  delayJitterMs: number;
};

const INITIAL_CHAOS: ChaosFormState = {
  strategy: "",
  duplicateCount: 2,
  malformedMutations: "dropField:amount",
  unbalancedDelta: 1,
  burstCount: 5,
  burstRate: 10,
  delayMs: 1000,
  delayJitterMs: 0
};

function buildChaosOptions(f: ChaosFormState): ChaosOptions | null {
  switch (f.strategy) {
    case "duplicate":
      return { duplicate: { count: Math.min(f.duplicateCount, CHAOS_LIMITS.maxDuplicates) } };
    case "outOfOrder":
      return { outOfOrder: { order: [] } };
    case "malformed":
      return {
        malformed: {
          mutations: f.malformedMutations
            .split(",")
            .map(s => s.trim())
            .filter(Boolean)
        }
      };
    case "unbalanced":
      return { unbalanced: { delta: f.unbalancedDelta } };
    case "burst":
      return {
        burst: {
          count: Math.min(f.burstCount, CHAOS_LIMITS.maxBurst),
          ratePerSecond: Math.min(f.burstRate, CHAOS_LIMITS.maxRatePerSecond)
        }
      };
    case "delay":
      return {
        delay: {
          delayMs: Math.min(f.delayMs, CHAOS_LIMITS.maxDelayMs),
          jitterMs: Math.min(f.delayJitterMs, CHAOS_LIMITS.maxDelayMs)
        }
      };
    default:
      return null;
  }
}

const isDestructive = (strategy: ChaosFormState["strategy"]) =>
  ["malformed", "unbalanced", "burst"].includes(strategy);

// ---------------------------------------------------------------------------
// Dynamic fields from catalog
// ---------------------------------------------------------------------------

function FlowFieldsForm({
  catalog,
  values,
  onChange
}: {
  catalog: FlowCatalogEntry;
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
}) {
  const allFields = [
    ...catalog.requiredFields.map(f => ({ name: f, required: true })),
    ...catalog.optionalFields.map(f => ({ name: f, required: false }))
  ];

  if (allFields.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        No additional fields required for this flow.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3">
      {allFields.map(({ name, required }) => (
        <div key={name} className="space-y-1">
          <label className="text-xs font-medium">
            {formatEnumValue(name)}
            {required && <span className="ml-0.5 text-destructive">*</span>}
          </label>
          <Input
            value={values[name] ?? ""}
            onChange={e => onChange(name, e.target.value)}
            placeholder={required ? "Required" : "Optional"}
            className="text-xs"
          />
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// FlowResult display
// ---------------------------------------------------------------------------

function FlowResultCard({ result }: { result: FlowResult }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-xs font-semibold">Flow Result</p>
        <Badge variant={result.status === "PUBLISHED" ? "success" : "destructive"}>
          {result.status}
        </Badge>
      </div>
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
            <dd
              className={`mt-0.5 text-xs ${f.mono ? "font-mono text-muted-foreground" : ""}`}
            >
              {f.value}
            </dd>
          </div>
        ))}
      </dl>
      {result.error && (
        <InlineNotice description={result.error} tone="danger" className="mt-3" />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Single Flow Page
// ---------------------------------------------------------------------------

export function SingleFlowPage() {
  const { token } = useSession();
  const [selectedFlowType, setSelectedFlowType] = useState("");
  const [flowFields, setFlowFields] = useState<Record<string, string>>({});
  const [amount, setAmount] = useState("");
  const [grossAmount, setGrossAmount] = useState("");
  const [netAmount, setNetAmount] = useState("");
  const [currency, setCurrency] = useState("GHS");
  const [correlationId, setCorrelationId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [channel, setChannel] = useState("");
  const [slotOverrides, setSlotOverrides] = useState<Record<string, string>>({});
  const [chaos, setChaos] = useState<ChaosFormState>(INITIAL_CHAOS);
  const [formError, setFormError] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [result, setResult] = useState<FlowResult | null>(null);

  const catalogQuery = useQuery({
    queryKey: ["flow-catalog"],
    queryFn: () => getFlowCatalog(token!)
  });

  const catalog = catalogQuery.data ?? [];
  const selectedCatalog = catalog.find(c => c.flowType === selectedFlowType) ?? null;
  const flowConfigsQuery = useQuery({
    queryKey: ["flow-configs"],
    queryFn: () => listFlowConfigs(token!),
    enabled: Boolean(token)
  });
  const selectedFlowConfig =
    flowConfigsQuery.data?.find(config => config.flowType === selectedFlowType) ?? null;

  const flowTypeOptions = catalog.map(c => ({
    value: c.flowType,
    label: formatEnumValue(c.flowType)
  }));

  useEffect(() => {
    if (!selectedFlowConfig) {
      setSlotOverrides({});
      return;
    }

    setSlotOverrides(
      Object.fromEntries(
        selectedFlowConfig.slots
          .filter(slot => Boolean(slot.effectiveVaId))
          .map(slot => [slot.slotName, slot.effectiveVaId ?? ""])
      )
    );
  }, [selectedFlowConfig]);

  function updateFlowField(key: string, value: string) {
    setFlowFields(prev => ({ ...prev, [key]: value }));
  }

  const mutation = useMutation({
    mutationFn: () => {
      const chaosOptions = buildChaosOptions(chaos);
      const req: PublishFlowRequest = {
        correlationId: correlationId.trim() || null,
        tenantId: tenantId.trim() || null,
        channel: channel.trim() || null,
        currency: currency.trim() || null,
        amount: amount ? parseFloat(amount) : null,
        grossAmount: grossAmount ? parseFloat(grossAmount) : null,
        netAmount: netAmount ? parseFloat(netAmount) : null,
        slotOverrides: Object.fromEntries(
          Object.entries(slotOverrides).filter(([, value]) => value.trim() !== "")
        ),
        flowFields: Object.fromEntries(
          Object.entries(flowFields).filter(([, v]) => v.trim() !== "")
        ),
        chaos: chaosOptions
      };
      return runFlow(token!, selectedFlowType, req);
    },
    onSuccess: res => {
      setResult(res);
      setFormError(null);
    },
    onError: err => setFormError(getErrorMessage(err))
  });

  function handleSubmit() {
    setFormError(null);
    setResult(null);
    if (!selectedFlowType) {
      setFormError("Please select a flow type.");
      return;
    }
    // Check required fields
    if (selectedCatalog) {
      const missing = selectedCatalog.requiredFields.filter(
        f => !flowFields[f]?.trim()
      );
      if (missing.length > 0) {
        setFormError(`Required fields missing: ${missing.join(", ")}`);
        return;
      }
    }
    if (isDestructive(chaos.strategy)) {
      setConfirmOpen(true);
      return;
    }
    mutation.mutate();
  }

  function downloadTemplate() {
    if (!selectedCatalog) return;
    const cols = selectedCatalog.csvColumns.join(",");
    const blob = new Blob([cols + "\n"], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${selectedFlowType.toLowerCase()}_template.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Page>
      <PageHeader
        title="Single Flow"
        description="Publish a single transaction flow event to the configured Kafka cluster."
      />
      <PageContent className="max-w-3xl">
        <Card>
          <CardHeader>
            <CardTitle>Flow Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* Flow type select */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Flow Type</label>
              {catalogQuery.isLoading ? (
                <div className="h-9 animate-pulse rounded-md bg-muted/40" />
              ) : (
                <Select
                  value={selectedFlowType as string & { __brand: "flow" }}
                  onChange={v => {
                    setSelectedFlowType(v);
                    setFlowFields({});
                  }}
                  options={flowTypeOptions as { value: string & { __brand: "flow" }; label: string }[]}
                  placeholder="Select a flow…"
                  searchable
                  searchPlaceholder="Search flows…"
                />
              )}
            </div>

            {/* Common fields */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Amount (optional)</label>
                <Input
                  type="number"
                  step="0.01"
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder="e.g. 100.00"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Gross Amount (optional)</label>
                <Input
                  type="number"
                  step="0.01"
                  value={grossAmount}
                  onChange={e => setGrossAmount(e.target.value)}
                  placeholder="e.g. 100.00"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Net Amount (optional)</label>
                <Input
                  type="number"
                  step="0.01"
                  value={netAmount}
                  onChange={e => setNetAmount(e.target.value)}
                  placeholder="e.g. 97.50"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Currency</label>
                <Input
                  value={currency}
                  onChange={e => setCurrency(e.target.value.toUpperCase())}
                  placeholder="GHS"
                  maxLength={3}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Channel (optional)</label>
                <Input
                  value={channel}
                  onChange={e => setChannel(e.target.value.toUpperCase())}
                  placeholder="MTN"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Tenant ID (optional)</label>
                <Input
                  value={tenantId}
                  onChange={e => setTenantId(e.target.value)}
                  placeholder="Default tenant if blank"
                />
              </div>
              <div className="col-span-2 space-y-1.5">
                <label className="text-xs font-medium">Correlation ID (optional)</label>
                <Input
                  value={correlationId}
                  onChange={e => setCorrelationId(e.target.value)}
                  placeholder="Auto-generated if blank"
                />
              </div>
            </div>

            {/* Slot overrides */}
            {selectedFlowConfig?.slots.length ? (
              <div className="space-y-2">
                <div>
                  <p className="text-xs font-semibold">Resolved Slot Accounts</p>
                  <p className="text-[10px] text-muted-foreground">
                    Prefilled from chart-of-accounts defaults; edit any VA ID before publishing.
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {selectedFlowConfig.slots.map(slot => (
                    <div key={slot.slotName} className="space-y-1">
                      <label className="text-xs font-medium">{formatEnumValue(slot.slotName)}</label>
                      <Input
                        value={slotOverrides[slot.slotName] ?? ""}
                        onChange={event =>
                          setSlotOverrides(current => ({
                            ...current,
                            [slot.slotName]: event.target.value
                          }))
                        }
                        placeholder={slot.effectiveVaId ?? "No default VA resolved"}
                        className="font-mono text-xs"
                      />
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {/* Dynamic flow fields */}
            {selectedCatalog && (
              <div className="space-y-2">
                <p className="text-xs font-semibold">Flow Fields</p>
                <FlowFieldsForm
                  catalog={selectedCatalog}
                  values={flowFields}
                  onChange={updateFlowField}
                />
                {selectedCatalog.csvColumns.length > 0 && (
                  <Button variant="ghost" size="sm" onClick={downloadTemplate}>
                    Download CSV template
                  </Button>
                )}
              </div>
            )}

            {/* Chaos options */}
            <div className="space-y-3 rounded-lg border border-border p-4">
              <p className="text-xs font-semibold">Chaos Options</p>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Strategy</label>
                <Select
                  value={chaos.strategy as string & { __brand: "chaos" }}
                  onChange={v => setChaos(c => ({ ...c, strategy: v as ChaosFormState["strategy"] }))}
                  options={[
                    { value: "" as string & { __brand: "chaos" }, label: "None (normal flow)" },
                    { value: "duplicate" as string & { __brand: "chaos" }, label: "Duplicate" },
                    { value: "outOfOrder" as string & { __brand: "chaos" }, label: "Out of Order" },
                    { value: "malformed" as string & { __brand: "chaos" }, label: "Malformed ⚠" },
                    { value: "unbalanced" as string & { __brand: "chaos" }, label: "Unbalanced ⚠" },
                    { value: "burst" as string & { __brand: "chaos" }, label: "Burst ⚠" },
                    { value: "delay" as string & { __brand: "chaos" }, label: "Delay" }
                  ]}
                  placeholder="Select strategy…"
                />
              </div>

              {chaos.strategy === "duplicate" && (
                <div className="space-y-1.5">
                  <label className="text-xs font-medium">
                    Duplicate count (max {CHAOS_LIMITS.maxDuplicates})
                  </label>
                  <Input
                    type="number"
                    min={1}
                    max={CHAOS_LIMITS.maxDuplicates}
                    value={chaos.duplicateCount}
                    onChange={e => setChaos(c => ({ ...c, duplicateCount: parseInt(e.target.value) || 2 }))}
                  />
                </div>
              )}

              {chaos.strategy === "malformed" && (
                <div className="space-y-1.5">
                  <label className="text-xs font-medium">Mutations (comma-separated)</label>
                  <Input
                    value={chaos.malformedMutations}
                    onChange={e => setChaos(c => ({ ...c, malformedMutations: e.target.value }))}
                    placeholder="dropField:amount,negativeAmount"
                  />
                </div>
              )}

              {chaos.strategy === "unbalanced" && (
                <div className="space-y-1.5">
                  <label className="text-xs font-medium">Delta (subtracted from net_amount)</label>
                  <Input
                    type="number"
                    step="0.01"
                    value={chaos.unbalancedDelta}
                    onChange={e => setChaos(c => ({ ...c, unbalancedDelta: parseFloat(e.target.value) || 1 }))}
                  />
                </div>
              )}

              {chaos.strategy === "burst" && (
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium">
                      Count (max {CHAOS_LIMITS.maxBurst})
                    </label>
                    <Input
                      type="number"
                      min={1}
                      max={CHAOS_LIMITS.maxBurst}
                      value={chaos.burstCount}
                      onChange={e => setChaos(c => ({ ...c, burstCount: parseInt(e.target.value) || 5 }))}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium">
                      Rate/sec (max {CHAOS_LIMITS.maxRatePerSecond})
                    </label>
                    <Input
                      type="number"
                      min={1}
                      max={CHAOS_LIMITS.maxRatePerSecond}
                      value={chaos.burstRate}
                      onChange={e => setChaos(c => ({ ...c, burstRate: parseInt(e.target.value) || 10 }))}
                    />
                  </div>
                </div>
              )}

              {chaos.strategy === "delay" && (
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium">
                      Delay ms (max {CHAOS_LIMITS.maxDelayMs})
                    </label>
                    <Input
                      type="number"
                      min={0}
                      max={CHAOS_LIMITS.maxDelayMs}
                      value={chaos.delayMs}
                      onChange={e => setChaos(c => ({ ...c, delayMs: parseInt(e.target.value) || 0 }))}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium">Jitter ms</label>
                    <Input
                      type="number"
                      min={0}
                      value={chaos.delayJitterMs}
                      onChange={e => setChaos(c => ({ ...c, delayJitterMs: parseInt(e.target.value) || 0 }))}
                    />
                  </div>
                </div>
              )}

              {isDestructive(chaos.strategy) && (
                <InlineNotice
                  title="Destructive option selected"
                  description="This strategy will intentionally produce malformed or unbalanced events. A confirmation dialog will appear before sending."
                  tone="warning"
                />
              )}
            </div>

            {formError && <InlineNotice description={formError} tone="danger" />}

            <div className="flex gap-2">
              <Button
                onClick={handleSubmit}
                disabled={mutation.isPending || !selectedFlowType}
              >
                {mutation.isPending ? (
                  <>
                    <RefreshCw className="mr-1.5 h-4 w-4 animate-spin" />
                    Publishing…
                  </>
                ) : (
                  <>
                    <Play className="mr-1.5 h-4 w-4" />
                    Publish Flow
                  </>
                )}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setResult(null);
                  setFormError(null);
                  setChaos(INITIAL_CHAOS);
                  setFlowFields({});
                  setGrossAmount("");
                  setNetAmount("");
                  setAmount("");
                  setTenantId("");
                  setChannel("");
                  setSlotOverrides({});
                  setCorrelationId("");
                }}
              >
                Reset
              </Button>
            </div>
          </CardContent>
        </Card>

        {result && <FlowResultCard result={result} />}

        {/* Confirmation dialog for destructive chaos */}
        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-amber-500" />
                Confirm destructive chaos
              </DialogTitle>
              <DialogDescription>
                You are about to publish an intentionally{" "}
                <strong>{chaos.strategy}</strong> event. This will deliberately stress the
                ledger. Ensure you are targeting the correct Kafka cluster.
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
                Confirm & Publish
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </PageContent>
    </Page>
  );
}
