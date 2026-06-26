import { cn } from "@/lib/utils";

export type Outcome = "SUCCEED" | "FAIL" | "RANDOM";

const OPTIONS: { value: Outcome; label: string; hint: string }[] = [
  { value: "SUCCEED", label: "Succeed", hint: "confirm, then publish completed" },
  { value: "FAIL", label: "Fail", hint: "confirm, then publish failed" },
  { value: "RANDOM", label: "Random", hint: "unattended — system decides per lifecycle" }
];

/**
 * The lifecycle outcome selector (Succeed / Fail / Random). Succeed/Fail drive the interactive
 * two-step wizard; Random delegates to the unattended bulk runner. Each option shows a one-line hint.
 */
export function OutcomeSelector({
  value,
  onChange
}: {
  value: Outcome;
  onChange: (outcome: Outcome) => void;
}) {
  return (
    <div role="radiogroup" aria-label="Outcome" className="flex flex-wrap gap-2">
      {OPTIONS.map(o => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            title={o.hint}
            onClick={() => onChange(o.value)}
            className={cn(
              "rounded-md border px-3 py-1.5 text-xs transition-colors",
              active
                ? "border-primary bg-primary/10 font-medium text-primary"
                : "border-border text-muted-foreground hover:bg-muted/50"
            )}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
