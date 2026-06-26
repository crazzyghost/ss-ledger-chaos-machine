import { InlineNotice } from "@/components/layout/state-panel";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { ChaosOptions, NTimesMode, NTimesOptions, NTimesPacing } from "@/lib/api";

export const CHAOS_LIMITS = {
  maxDuplicates: 10,
  maxBurst: 100,
  maxRatePerSecond: 1000,
  maxDelayMs: 30_000,
  maxNTimes: 250,
  maxNTimesSync: 25
} as const;

export type ChaosStrategy =
  | ""
  | "duplicate"
  | "outOfOrder"
  | "malformed"
  | "unbalanced"
  | "burst"
  | "delay"
  | "nTimes";

export type ChaosFormState = {
  strategy: ChaosStrategy;
  duplicateCount: number;
  malformedMutations: string;
  unbalancedDelta: number;
  burstCount: number;
  burstRate: number;
  delayMs: number;
  delayJitterMs: number;
  nTimesCount: number;
  nTimesPacing: NTimesPacing;
  nTimesMode: NTimesMode;
  nTimesFixedDelayMs: number;
  nTimesMinDelayMs: number;
  nTimesMaxDelayMs: number;
};

export const INITIAL_CHAOS: ChaosFormState = {
  strategy: "",
  duplicateCount: 2,
  malformedMutations: "dropField:amount",
  unbalancedDelta: 1,
  burstCount: 5,
  burstRate: 10,
  delayMs: 1000,
  delayJitterMs: 0,
  nTimesCount: 5,
  nTimesPacing: "BURST",
  nTimesMode: "SYNC",
  nTimesFixedDelayMs: 1000,
  nTimesMinDelayMs: 100,
  nTimesMaxDelayMs: 1000
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
    case "nTimes": {
      const maxCount =
        f.nTimesMode === "SYNC" ? CHAOS_LIMITS.maxNTimesSync : CHAOS_LIMITS.maxNTimes;
      const nTimes: NTimesOptions = {
        count: Math.min(Math.max(f.nTimesCount, 1), maxCount),
        pacing: f.nTimesPacing,
        mode: f.nTimesMode
      };
      if (f.nTimesPacing === "LINEAR") {
        nTimes.fixedDelayMs = Math.min(Math.max(f.nTimesFixedDelayMs, 0), CHAOS_LIMITS.maxDelayMs);
      } else if (f.nTimesPacing === "RANDOM") {
        nTimes.minDelayMs = Math.min(Math.max(f.nTimesMinDelayMs, 0), CHAOS_LIMITS.maxDelayMs);
        nTimes.maxDelayMs = Math.min(Math.max(f.nTimesMaxDelayMs, 0), CHAOS_LIMITS.maxDelayMs);
      }
      return { nTimes };
    }
    default:
      return null;
  }
}

export const isDestructive = (strategy: ChaosStrategy) =>
  ["malformed", "unbalanced", "burst"].includes(strategy);

/** Strategies that should surface a confirmation dialog before sending. */
export const needsConfirm = (strategy: ChaosStrategy) =>
  isDestructive(strategy) || strategy === "nTimes";

const STRATEGY_OPTIONS = [
  { value: "", label: "None (normal flow)" },
  { value: "duplicate", label: "Duplicate" },
  { value: "outOfOrder", label: "Out of Order" },
  { value: "malformed", label: "Malformed ⚠" },
  { value: "unbalanced", label: "Unbalanced ⚠" },
  { value: "burst", label: "Burst ⚠" },
  { value: "delay", label: "Delay" },
  { value: "nTimes", label: "N Times" }
] as const;

const PACING_OPTIONS = [
  { value: "BURST", label: "Burst (no delay)" },
  { value: "LINEAR", label: "Linear (fixed gap)" },
  { value: "RANDOM", label: "Random (random gap)" }
] as const;

const MODE_OPTIONS = [
  { value: "SYNC", label: "Sync (inline)" },
  { value: "ASYNC", label: "Async (tracked run)" }
] as const;

/**
 * The chaos-injection options widget. Rendered in the right column of the Single Flow Run page,
 * separate from the transaction form. Behaviour is unchanged from the previous inline panel.
 */
export function ChaosOptionsPanel({
  value,
  onChange,
  hideNTimes = false
}: {
  value: ChaosFormState;
  onChange: (next: ChaosFormState) => void;
  /** Hide the N-Times strategy — it does not apply to interactive Succeed/Fail lifecycle outcomes. */
  hideNTimes?: boolean;
}) {
  const update = (patch: Partial<ChaosFormState>) => onChange({ ...value, ...patch });
  const strategyOptions = hideNTimes
    ? STRATEGY_OPTIONS.filter(o => o.value !== "nTimes")
    : STRATEGY_OPTIONS;

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <label className="text-xs font-medium">Strategy</label>
        <Select
          value={value.strategy}
          onChange={v => update({ strategy: v as ChaosStrategy })}
          options={strategyOptions as readonly { value: ChaosStrategy; label: string }[]}
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

      {value.strategy === "nTimes" && (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium">
                Count (max {value.nTimesMode === "SYNC" ? CHAOS_LIMITS.maxNTimesSync : CHAOS_LIMITS.maxNTimes})
              </label>
              <Input
                type="number"
                min={1}
                max={value.nTimesMode === "SYNC" ? CHAOS_LIMITS.maxNTimesSync : CHAOS_LIMITS.maxNTimes}
                value={value.nTimesCount}
                onChange={e => update({ nTimesCount: parseInt(e.target.value) || 1 })}
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Mode</label>
              <Select
                value={value.nTimesMode}
                onChange={v => update({ nTimesMode: v as NTimesMode })}
                options={MODE_OPTIONS as readonly { value: NTimesMode; label: string }[]}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Pacing</label>
            <Select
              value={value.nTimesPacing}
              onChange={v => update({ nTimesPacing: v as NTimesPacing })}
              options={PACING_OPTIONS as readonly { value: NTimesPacing; label: string }[]}
            />
          </div>

          {value.nTimesPacing === "LINEAR" && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium">
                Fixed delay ms (max {CHAOS_LIMITS.maxDelayMs})
              </label>
              <Input
                type="number"
                min={0}
                max={CHAOS_LIMITS.maxDelayMs}
                value={value.nTimesFixedDelayMs}
                onChange={e => update({ nTimesFixedDelayMs: parseInt(e.target.value) || 0 })}
              />
            </div>
          )}

          {value.nTimesPacing === "RANDOM" && (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label className="text-xs font-medium">Min delay ms</label>
                <Input
                  type="number"
                  min={0}
                  max={CHAOS_LIMITS.maxDelayMs}
                  value={value.nTimesMinDelayMs}
                  onChange={e => update({ nTimesMinDelayMs: parseInt(e.target.value) || 0 })}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium">
                  Max delay ms (max {CHAOS_LIMITS.maxDelayMs})
                </label>
                <Input
                  type="number"
                  min={0}
                  max={CHAOS_LIMITS.maxDelayMs}
                  value={value.nTimesMaxDelayMs}
                  onChange={e => update({ nTimesMaxDelayMs: parseInt(e.target.value) || 0 })}
                />
              </div>
            </div>
          )}

          <InlineNotice
            title="N Times = N distinct transactions"
            description="Runs this flow the chosen number of times against the same accounts, each a real, independent transaction (fresh request id). Unlike Burst — which re-sends one duplicate-keyed event — these are not deduplicated by the ledger."
            tone="default"
          />
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
