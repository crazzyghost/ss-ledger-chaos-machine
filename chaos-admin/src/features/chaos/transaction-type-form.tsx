import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  listFlowConfigs,
  listVirtualAccounts,
  type FeeInput,
  type FlowCatalogEntry,
  type FlowFieldDescriptor,
  type InferenceRule,
  type VirtualAccountResponse
} from "@/lib/api";
import { ulid } from "@/lib/ulid";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { CountrySelect } from "./country-select";
import { FeeListField, emptyFeeRow } from "./fee-list-field";

/** The form's contribution to a {@code PublishFlowRequest}, routed to the right buckets. */
export type AssembledFlow = {
  slotOverrides: Record<string, string>;
  flowFields: Record<string, string>;
  correlationId: string;
  tenantId: string;
  currency: string;
  amount: string;
  fees: FeeInput[];
  missingRequired: string[];
};

const VA_PAGE_SIZE = 200;

function seedValue(descriptor: FlowFieldDescriptor): string {
  if (descriptor.autogen === "UUID_V4") return crypto.randomUUID();
  if (descriptor.autogen === "ULID") return ulid();
  return descriptor.defaultValue ?? "";
}

function seedValues(
  descriptors: FlowFieldDescriptor[],
  initial?: Record<string, string>
): Record<string, string> {
  const out: Record<string, string> = {};
  for (const d of descriptors) {
    out[d.name] = initial?.[d.name] != null ? initial[d.name] : seedValue(d);
  }
  return out;
}

function initialFees(descriptors: FlowFieldDescriptor[]): FeeInput[] {
  return descriptors.some(d => d.kind === "FEE_LIST") ? [emptyFeeRow()] : [];
}

/** The VA used as the inference "source": the {@code source} slot, else the single org VA field. */
function primaryVaValue(
  descriptors: FlowFieldDescriptor[],
  values: Record<string, string>
): string {
  const slotSource = descriptors.find(d => d.kind === "VA_REF" && d.slotName === "source");
  if (slotSource) return values[slotSource.name] ?? "";
  const single = descriptors.find(
    d => d.kind === "VA_REF" && d.slotName === null && d.name === "virtual_account_id"
  );
  return single ? values[single.name] ?? "" : "";
}

function destVaValue(descriptors: FlowFieldDescriptor[], values: Record<string, string>): string {
  const d = descriptors.find(x => x.kind === "VA_REF" && x.slotName === "destination");
  return d ? values[d.name] ?? "" : "";
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
  values: Record<string, string>,
  fees: FeeInput[]
): AssembledFlow {
  const slotOverrides: Record<string, string> = {};
  const flowFields: Record<string, string> = {};
  let correlationId = "";
  let tenantId = "";
  let currency = "";
  let amount = "";
  const missingRequired: string[] = [];

  const completeFees = fees.filter(
    f => String(f.amount ?? "").trim() !== "" && (f.destinationVaId ?? "").trim() !== ""
  );

  for (const d of descriptors) {
    if (d.kind === "FEE_LIST") {
      if (d.required && completeFees.length === 0) missingRequired.push(d.label);
      continue;
    }

    const v = (values[d.name] ?? "").trim();
    if (d.required && !v) missingRequired.push(d.label);

    if (d.kind === "VA_REF") {
      if (v) {
        if (d.slotName) slotOverrides[d.slotName] = v;
        else flowFields[d.name] = v;
      }
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

  return {
    slotOverrides,
    flowFields,
    correlationId,
    tenantId,
    currency,
    amount,
    fees: completeFees,
    missingRequired
  };
}

function vaLabel(va: VirtualAccountResponse): string {
  const id = va.vaId.length > 12 ? `${va.vaId.slice(0, 8)}…` : va.vaId;
  return `${va.name} · ${id} · ${va.currency}`;
}

/**
 * The catalog-driven transaction form for the Single Flow Run page and the lifecycle wizard. Renders
 * the selected flow's descriptors (required shown, advanced collapsed): VA pickers filtered by
 * account kind (routing to slot overrides, or to flow fields when {@code slotName} is null),
 * UUID/ULID autogen, a dynamic fee list, a supported-country select, a derived corridor, and
 * client-side inference of org/currency/tenant from the selected VA. {@code initialValues} pre-fills
 * carry-over fields (treated as edited so inference does not clobber them). Reports its assembled
 * contribution via {@code onChange}.
 */
export function TransactionTypeForm({
  catalog,
  token,
  onChange,
  initialValues,
  injectedValues
}: {
  catalog: FlowCatalogEntry;
  token: string;
  onChange: (assembled: AssembledFlow) => void;
  initialValues?: Record<string, string>;
  /**
   * Values pushed into the form after mount (e.g. a resolved reservation_id), merged over the
   * current values and marked edited. Memoize this by the caller so its identity is stable until the
   * injected value actually changes.
   */
  injectedValues?: Record<string, string>;
}) {
  const descriptors = catalog.fields;
  const flowType = catalog.flowType;

  const initialRef = useRef(initialValues);
  initialRef.current = initialValues;

  const [values, setValues] = useState<Record<string, string>>(() =>
    seedValues(descriptors, initialValues)
  );
  const [edited, setEdited] = useState<Set<string>>(
    () => new Set(initialValues ? Object.keys(initialValues) : [])
  );
  const [fees, setFees] = useState<FeeInput[]>(() => initialFees(descriptors));
  const [sourceVaId, setSourceVaId] = useState(() => primaryVaValue(descriptors, values));
  const [destVaId, setDestVaId] = useState(() => destVaValue(descriptors, values));
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

  // Reseed when the transaction type changes (carrying any initial/carry-over values).
  useEffect(() => {
    const initial = initialRef.current;
    const seeded = seedValues(descriptors, initial);
    setValues(seeded);
    setEdited(new Set(initial ? Object.keys(initial) : []));
    setFees(initialFees(descriptors));
    setSourceVaId(primaryVaValue(descriptors, seeded));
    setDestVaId(destVaValue(descriptors, seeded));
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

  // (Re)apply VA-based inference whenever a VA selection changes or the VA lists finish loading.
  useEffect(() => {
    setValues(prev => {
      const next = { ...prev };
      let changed = false;
      for (const d of descriptors) {
        if (
          d.inference === "NONE" ||
          d.inference === "CORRIDOR_FROM_COUNTRIES" ||
          edited.has(d.name)
        )
          continue;
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

  // Derive the corridor "{source_country}-{destination_country}" unless the operator edited it.
  useEffect(() => {
    const corridorDesc = descriptors.find(d => d.inference === "CORRIDOR_FROM_COUNTRIES");
    if (!corridorDesc || edited.has(corridorDesc.name)) return;
    setValues(prev => {
      const sc = prev["source_country"] ?? "";
      const dc = prev["destination_country"] ?? "";
      const corridor = sc && dc ? `${sc}-${dc}` : "";
      if (prev[corridorDesc.name] === corridor) return prev;
      return { ...prev, [corridorDesc.name]: corridor };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values["source_country"], values["destination_country"], edited]);

  // Merge externally-injected values (e.g. a resolved reservation_id) over the current values.
  useEffect(() => {
    if (!injectedValues) return;
    setValues(prev => {
      let changed = false;
      const next = { ...prev };
      for (const [k, v] of Object.entries(injectedValues)) {
        if (next[k] !== v) {
          next[k] = v;
          changed = true;
        }
      }
      return changed ? next : prev;
    });
    setEdited(prev => {
      const next = new Set(prev);
      for (const k of Object.keys(injectedValues)) next.add(k);
      return next;
    });
  }, [injectedValues]);

  // Report the assembled request up on every change.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  useEffect(() => {
    onChangeRef.current(assemble(descriptors, values, fees));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values, fees]);

  const setField = useCallback((name: string, value: string) => {
    setEdited(prev => (prev.has(name) ? prev : new Set(prev).add(name)));
    setValues(prev => ({ ...prev, [name]: value }));
  }, []);

  const selectVa = useCallback((descriptor: FlowFieldDescriptor, vaId: string) => {
    setValues(prev => ({ ...prev, [descriptor.name]: vaId }));
    if (descriptor.slotName === "source") setSourceVaId(vaId);
    else if (descriptor.slotName === "destination") setDestVaId(vaId);
    else if (descriptor.slotName === null && descriptor.name === "virtual_account_id")
      setSourceVaId(vaId);
  }, []);

  const regenerate = useCallback((name: string, rule: FlowFieldDescriptor["autogen"]) => {
    setEdited(prev => {
      if (!prev.has(name)) return prev;
      const next = new Set(prev);
      next.delete(name);
      return next;
    });
    setValues(prev => ({ ...prev, [name]: rule === "ULID" ? ulid() : crypto.randomUUID() }));
  }, []);

  const required = descriptors.filter(d => !d.advanced);
  const advanced = descriptors.filter(d => d.advanced);
  const advancedFilled = advanced.filter(d => (values[d.name] ?? "").trim() !== "").length;

  function renderField(d: FlowFieldDescriptor) {
    const value = values[d.name] ?? "";

    if (d.kind === "FEE_LIST") {
      return (
        <FeeListField
          rows={fees}
          onChange={setFees}
          systemVas={systemVas}
          loading={systemVasQuery.isLoading}
          net={d.name === "fees" && flowType === "COLLECTION_COMPLETED" ? values["amount"] ?? "" : ""}
        />
      );
    }

    if (d.kind === "COUNTRY") {
      return <CountrySelect token={token} value={value} onChange={v => setField(d.name, v)} />;
    }

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
      return <Select value={value} onChange={v => setField(d.name, v)} options={options} />;
    }

    if (d.kind === "INTEGER") {
      return (
        <Input
          type="number"
          step="1"
          min="1"
          value={value}
          onChange={e => setField(d.name, e.target.value)}
          className="text-xs"
        />
      );
    }

    if (d.kind === "UUID" || d.autogen !== "NONE") {
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
            onClick={() => regenerate(d.name, d.autogen)}
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

  // FEE_LIST spans the full width; other fields share the two-column grid.
  function fieldCell(d: FlowFieldDescriptor) {
    return (
      <div key={d.name} className={`space-y-1 ${d.kind === "FEE_LIST" ? "sm:col-span-2" : ""}`}>
        {fieldLabel(d)}
        {renderField(d)}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">{required.map(fieldCell)}</div>

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
              {advanced.map(fieldCell)}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
