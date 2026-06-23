import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  listFlowConfigs,
  listVirtualAccounts,
  type FlowCatalogEntry,
  type FlowFieldDescriptor,
  type InferenceRule,
  type VirtualAccountResponse
} from "@/lib/api";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

/** The form's contribution to a {@code PublishFlowRequest}, routed to the right buckets. */
export type AssembledFlow = {
  slotOverrides: Record<string, string>;
  flowFields: Record<string, string>;
  correlationId: string;
  tenantId: string;
  currency: string;
  amount: string;
  missingRequired: string[];
};

const VA_PAGE_SIZE = 200;

function seedValue(descriptor: FlowFieldDescriptor): string {
  if (descriptor.autogen === "UUID_V4") return crypto.randomUUID();
  return descriptor.defaultValue ?? "";
}

function seedValues(descriptors: FlowFieldDescriptor[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const d of descriptors) out[d.name] = seedValue(d);
  return out;
}

function inferValue(
  rule: InferenceRule,
  sourceVa: VirtualAccountResponse | null,
  destVa: VirtualAccountResponse | null
): string {
  switch (rule) {
    case "ORG_FROM_SOURCE_VA":
      return sourceVa?.organizationId ?? "";
    case "ORG_FROM_DEST_VA":
      return destVa?.organizationId ?? "";
    case "CURRENCY_FROM_SOURCE_VA":
      return sourceVa?.currency ?? "";
    case "TENANT_FROM_SOURCE_VA":
      return sourceVa?.ownershipType === "ORGANIZATION" ? sourceVa.organizationId ?? "" : "";
    default:
      return "";
  }
}

function assemble(
  descriptors: FlowFieldDescriptor[],
  values: Record<string, string>
): AssembledFlow {
  const slotOverrides: Record<string, string> = {};
  const flowFields: Record<string, string> = {};
  let correlationId = "";
  let tenantId = "";
  let currency = "";
  let amount = "";
  const missingRequired: string[] = [];

  for (const d of descriptors) {
    const v = (values[d.name] ?? "").trim();
    if (d.required && !v) missingRequired.push(d.label);

    if (d.kind === "VA_REF") {
      if (v && d.slotName) slotOverrides[d.slotName] = v;
      continue;
    }
    if (d.name === "correlation_id") {
      correlationId = v;
      continue;
    }
    if (d.name === "tenant_id") {
      tenantId = v;
      continue;
    }
    if (d.name === "currency") {
      currency = v;
      continue;
    }
    if (d.name === "amount") {
      amount = v;
      continue;
    }
    if (v) flowFields[d.name] = v;
  }

  return { slotOverrides, flowFields, correlationId, tenantId, currency, amount, missingRequired };
}

function vaLabel(va: VirtualAccountResponse): string {
  const id = va.vaId.length > 12 ? `${va.vaId.slice(0, 8)}…` : va.vaId;
  return `${va.name} · ${id} · ${va.currency}`;
}

/**
 * The left-column transaction form for the Single Flow Run page. Renders the selected flow's
 * descriptors (required shown, advanced collapsed), drives VA pickers filtered by account kind,
 * auto-generates UUID request ids, and infers org/currency/tenant from the selected VA — all
 * client-side. Reports its assembled contribution to a PublishFlowRequest via {@code onChange}.
 */
export function TransactionTypeForm({
  catalog,
  token,
  onChange
}: {
  catalog: FlowCatalogEntry;
  token: string;
  onChange: (assembled: AssembledFlow) => void;
}) {
  const descriptors = catalog.fields;
  const flowType = catalog.flowType;

  const [values, setValues] = useState<Record<string, string>>(() => seedValues(descriptors));
  const [edited, setEdited] = useState<Set<string>>(() => new Set());
  const [sourceVaId, setSourceVaId] = useState("");
  const [destVaId, setDestVaId] = useState("");
  const [showAdvanced, setShowAdvanced] = useState(false);

  const orgVasQuery = useQuery({
    queryKey: ["vas", "ORGANIZATION"],
    queryFn: () =>
      listVirtualAccounts(token, { ownershipType: "ORGANIZATION", perPage: VA_PAGE_SIZE }),
    enabled: Boolean(token)
  });
  const systemVasQuery = useQuery({
    queryKey: ["vas", "SYSTEM"],
    queryFn: () => listVirtualAccounts(token, { ownershipType: "SYSTEM", perPage: VA_PAGE_SIZE }),
    enabled: Boolean(token)
  });
  const flowConfigsQuery = useQuery({
    queryKey: ["flow-configs"],
    queryFn: () => listFlowConfigs(token),
    enabled: Boolean(token)
  });

  const orgVas = orgVasQuery.data?.items ?? [];
  const systemVas = systemVasQuery.data?.items ?? [];
  const allVas = [...orgVas, ...systemVas];
  const sourceVa = allVas.find(v => v.vaId === sourceVaId) ?? null;
  const destVa = allVas.find(v => v.vaId === destVaId) ?? null;
  const flowConfig = flowConfigsQuery.data?.find(c => c.flowType === flowType) ?? null;

  // Reseed when the transaction type changes.
  useEffect(() => {
    setValues(seedValues(descriptors));
    setEdited(new Set());
    setSourceVaId("");
    setDestVaId("");
    setShowAdvanced(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flowType]);

  // Default SYSTEM VA pickers to their chart-of-accounts slot default, once VAs/config load.
  useEffect(() => {
    if (!systemVas.length) return;
    setValues(prev => {
      const next = { ...prev };
      let changed = false;
      for (const d of descriptors) {
        if (d.kind !== "VA_REF" || d.accountKind !== "SYSTEM" || next[d.name]) continue;
        const def = flowConfig?.slots.find(s => s.slotName === d.slotName)?.effectiveVaId;
        if (!def) continue;
        next[d.name] = def;
        changed = true;
        if (d.slotName === "source") setSourceVaId(def);
        else if (d.slotName === "destination") setDestVaId(def);
      }
      return changed ? next : prev;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [systemVas.length, flowConfig, flowType]);

  // (Re)apply inference whenever a VA selection changes or the VA lists finish loading.
  useEffect(() => {
    setValues(prev => {
      const next = { ...prev };
      let changed = false;
      for (const d of descriptors) {
        if (d.inference === "NONE" || edited.has(d.name)) continue;
        const inferred = inferValue(d.inference, sourceVa, destVa);
        if (next[d.name] !== inferred) {
          next[d.name] = inferred;
          changed = true;
        }
      }
      return changed ? next : prev;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceVaId, destVaId, orgVas.length, systemVas.length]);

  // Report the assembled request up on every change.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  useEffect(() => {
    onChangeRef.current(assemble(descriptors, values));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values]);

  const setField = useCallback((name: string, value: string) => {
    setEdited(prev => (prev.has(name) ? prev : new Set(prev).add(name)));
    setValues(prev => ({ ...prev, [name]: value }));
  }, []);

  const selectVa = useCallback((descriptor: FlowFieldDescriptor, vaId: string) => {
    setValues(prev => ({ ...prev, [descriptor.name]: vaId }));
    if (descriptor.slotName === "source") setSourceVaId(vaId);
    else if (descriptor.slotName === "destination") setDestVaId(vaId);
  }, []);

  const regenerate = useCallback((name: string) => {
    setEdited(prev => {
      if (!prev.has(name)) return prev;
      const next = new Set(prev);
      next.delete(name);
      return next;
    });
    setValues(prev => ({ ...prev, [name]: crypto.randomUUID() }));
  }, []);

  const required = descriptors.filter(d => !d.advanced);
  const advanced = descriptors.filter(d => d.advanced);
  const advancedFilled = advanced.filter(d => (values[d.name] ?? "").trim() !== "").length;

  function renderField(d: FlowFieldDescriptor) {
    const value = values[d.name] ?? "";

    if (d.kind === "VA_REF") {
      const list = d.accountKind === "ORGANIZATION" ? orgVas : systemVas;
      const loading =
        d.accountKind === "ORGANIZATION" ? orgVasQuery.isLoading : systemVasQuery.isLoading;
      const options = list.map(va => ({ value: va.vaId, label: vaLabel(va) }));
      return (
        <Select
          value={value}
          onChange={v => selectVa(d, v)}
          options={options}
          placeholder={loading ? "Loading accounts…" : `Select ${d.accountKind?.toLowerCase()} VA…`}
          searchable
          searchPlaceholder="Search accounts…"
          emptyText="No matching virtual accounts."
        />
      );
    }

    if (d.kind === "SELECT") {
      const options = (d.options ?? []).map(o => ({ value: o, label: o }));
      return (
        <Select value={value} onChange={v => setField(d.name, v)} options={options} />
      );
    }

    if (d.kind === "UUID") {
      return (
        <div className="flex gap-1.5">
          <Input
            value={value}
            onChange={e => setField(d.name, e.target.value)}
            className="font-mono text-xs"
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            title="Regenerate"
            onClick={() => regenerate(d.name)}
          >
            <RefreshCw className="h-3.5 w-3.5" />
          </Button>
        </div>
      );
    }

    const type = d.kind === "AMOUNT" ? "number" : d.kind === "DATETIME" ? "datetime-local" : "text";
    return (
      <Input
        type={type}
        step={d.kind === "AMOUNT" ? "0.0001" : undefined}
        value={value}
        onChange={e => setField(d.name, e.target.value)}
        className="text-xs"
      />
    );
  }

  function fieldLabel(d: FlowFieldDescriptor) {
    return (
      <label className="text-xs font-medium">
        {d.label}
        {d.required && <span className="ml-0.5 text-destructive">*</span>}
      </label>
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {required.map(d => (
          <div key={d.name} className="space-y-1">
            {fieldLabel(d)}
            {renderField(d)}
          </div>
        ))}
      </div>

      {advanced.length > 0 && (
        <div className="rounded-lg border border-border">
          <button
            type="button"
            onClick={() => setShowAdvanced(s => !s)}
            className="flex w-full items-center justify-between px-3 py-2 text-xs font-medium"
          >
            <span>
              Advanced / inferred
              <span className="ml-2 text-muted-foreground">
                {advancedFilled}/{advanced.length} set
              </span>
            </span>
            <ChevronDown
              className={`h-4 w-4 transition-transform ${showAdvanced ? "rotate-180" : ""}`}
            />
          </button>
          {showAdvanced && (
            <div className="grid grid-cols-1 gap-3 border-t border-border p-3 sm:grid-cols-2">
              {advanced.map(d => (
                <div key={d.name} className="space-y-1">
                  {fieldLabel(d)}
                  {renderField(d)}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
