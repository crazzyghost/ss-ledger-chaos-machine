import { InlineNotice } from "@/components/layout/state-panel";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { ChaosOptions } from "@/lib/api";

export const CHAOS_LIMITS = {
  maxDuplicates: 10,
  maxBurst: 100,
  maxRatePerSecond: 1000,
  maxDelayMs: 30_000
} as const;

export type ChaosStrategy =
  | ""
  | "duplicate"
  | "outOfOrder"
  | "malformed"
  | "unbalanced"
  | "burst"
  | "delay";

export type ChaosFormState = {
  strategy: ChaosStrategy;
  duplicateCount: number;
  malformedMutations: string;
  unbalancedDelta: number;
  burstCount: number;
  burstRate: number;
  delayMs: number;
  delayJitterMs: number;
};

export const INITIAL_CHAOS: ChaosFormState = {
  strategy: "",
  duplicateCount: 2,
  malformedMutations: "dropField:amount",
  unbalancedDelta: 1,
  burstCount: 5,
  burstRate: 10,
  delayMs: 1000,
  delayJitterMs: 0
};

export function buildChaosOptions(f: ChaosFormState): ChaosOptions | null {
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

export const isDestructive = (strategy: ChaosStrategy) =>
  ["malformed", "unbalanced", "burst"].includes(strategy);

const STRATEGY_OPTIONS = [
  { value: "", label: "None (normal flow)" },
  { value: "duplicate", label: "Duplicate" },
  { value: "outOfOrder", label: "Out of Order" },
  { value: "malformed", label: "Malformed ⚠" },
  { value: "unbalanced", label: "Unbalanced ⚠" },
  { value: "burst", label: "Burst ⚠" },
  { value: "delay", label: "Delay" }
] as const;

/**
 * The chaos-injection options widget. Rendered in the right column of the Single Flow Run page,
 * separate from the transaction form. Behaviour is unchanged from the previous inline panel.
 */
export function ChaosOptionsPanel({
  value,
  onChange
}: {
  value: ChaosFormState;
  onChange: (next: ChaosFormState) => void;
}) {
  const update = (patch: Partial<ChaosFormState>) => onChange({ ...value, ...patch });

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <label className="text-xs font-medium">Strategy</label>
        <Select
          value={value.strategy}
          onChange={v => update({ strategy: v as ChaosStrategy })}
          options={STRATEGY_OPTIONS as readonly { value: ChaosStrategy; label: string }[]}
          placeholder="Select strategy…"
        />
      </div>

      {value.strategy === "duplicate" && (
        <div className="space-y-1.5">
          <label className="text-xs font-medium">
            Duplicate count (max {CHAOS_LIMITS.maxDuplicates})
          </label>
          <Input
            type="number"
            min={1}
            max={CHAOS_LIMITS.maxDuplicates}
            value={value.duplicateCount}
            onChange={e => update({ duplicateCount: parseInt(e.target.value) || 2 })}
          />
        </div>
      )}

      {value.strategy === "malformed" && (
        <div className="space-y-1.5">
          <label className="text-xs font-medium">Mutations (comma-separated)</label>
          <Input
            value={value.malformedMutations}
            onChange={e => update({ malformedMutations: e.target.value })}
            placeholder="dropField:amount,negativeAmount"
          />
        </div>
      )}

      {value.strategy === "unbalanced" && (
        <div className="space-y-1.5">
          <label className="text-xs font-medium">Delta (subtracted from net_amount)</label>
          <Input
            type="number"
            step="0.01"
            value={value.unbalancedDelta}
            onChange={e => update({ unbalancedDelta: parseFloat(e.target.value) || 1 })}
          />
        </div>
      )}

      {value.strategy === "burst" && (
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Count (max {CHAOS_LIMITS.maxBurst})</label>
            <Input
              type="number"
              min={1}
              max={CHAOS_LIMITS.maxBurst}
              value={value.burstCount}
              onChange={e => update({ burstCount: parseInt(e.target.value) || 5 })}
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
              value={value.burstRate}
              onChange={e => update({ burstRate: parseInt(e.target.value) || 10 })}
            />
          </div>
        </div>
      )}

      {value.strategy === "delay" && (
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Delay ms (max {CHAOS_LIMITS.maxDelayMs})</label>
            <Input
              type="number"
              min={0}
              max={CHAOS_LIMITS.maxDelayMs}
              value={value.delayMs}
              onChange={e => update({ delayMs: parseInt(e.target.value) || 0 })}
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Jitter ms</label>
            <Input
              type="number"
              min={0}
              value={value.delayJitterMs}
              onChange={e => update({ delayJitterMs: parseInt(e.target.value) || 0 })}
            />
          </div>
        </div>
      )}

      {isDestructive(value.strategy) && (
        <InlineNotice
          title="Destructive option selected"
          description="This strategy will intentionally produce malformed or unbalanced events. A confirmation dialog will appear before sending."
          tone="warning"
        />
      )}
    </div>
  );
}
