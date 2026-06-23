# Task 004 - Frontend: N Times Option & Sync/Async Result Handling

## Functional Requirements
- Add an **"N Times"** strategy to the Single Flow Run chaos-options widget, with inputs for
  **Count**, **Pacing** (Burst / Linear / Random), **Mode** (Sync / Async), and pacing delays
  (Linear → fixed delay ms; Random → min/max delay ms).
- On publish, route N-Times to the dedicated `POST /api/v0/flows/{flowType}/n-times` endpoint and
  handle the two responses: **SYNC** → show an aggregate result; **ASYNC** → hand off to the
  existing run-results view.
- Make the **distinction from Burst explicit** in copy ("N Times = N distinct transactions
  against the same accounts; Burst = duplicate event"), and confirm before high-volume/async runs.

## Acceptance Criteria
- [ ] Selecting **N Times** reveals Count, Pacing, Mode, and pacing-delay inputs; Linear shows a
      fixed-delay input, Random shows min/max inputs, Burst shows none.
- [ ] `buildChaosOptions` emits `{ nTimes: { count, pacing, mode, fixedDelayMs?, minDelayMs?,
      maxDelayMs? } }` clamped to the client mirror of `chaos.limits` (count ≤ maxNTimes, and ≤
      maxNTimesSync when Sync; delays ≤ maxDelayMs).
- [ ] Publishing with N Times calls the **/n-times** endpoint (not the plain publish path).
- [ ] SYNC response renders an aggregate (count, succeeded, failed, correlation id, event ids);
      ASYNC response navigates to / opens the run-results view for the returned run id.
- [ ] A confirmation dialog appears before sending (reusing the existing destructive-confirm
      pattern) for N Times, surfacing count + mode.
- [ ] The run-results pages show N-Times runs (labelled by `kind`/`pacing`) alongside CSV batches.

## Technical Design
React 19 + Vite + react-query, matching the existing `features/chaos` modules.

- **`chaos-options-panel.tsx`**: extend `ChaosStrategy` union with `"nTimes"`; add
  `nTimes*` fields to `ChaosFormState` (`nTimesCount`, `nTimesPacing`, `nTimesMode`,
  `nTimesFixedDelayMs`, `nTimesMinDelayMs`, `nTimesMaxDelayMs`) with sensible defaults
  (count 5, pacing `BURST`, mode `SYNC`); add the strategy to `STRATEGY_OPTIONS` ("N Times");
  render conditional inputs; extend `buildChaosOptions`; treat N Times as confirm-worthy
  (extend `isDestructive` or add an `isHighVolume`/`needsConfirm` predicate so the existing
  confirm dialog triggers).
- **`single-flow-page.tsx`**: when `chaos.strategy === "nTimes"`, submit via a new
  `publishNTimes(flowType, request)` API call instead of `publishFlow`; branch on the response:
  HTTP `200` → render an aggregate result panel; HTTP `202` → treat the body as a run handle and
  route to the run-results page (reuse `batch-run-page.tsx`) or open it inline.
- **`src/lib/api.ts`**: add the wire types and call:
  ```ts
  export type NTimesPacing = "BURST" | "LINEAR" | "RANDOM";
  export type NTimesMode = "SYNC" | "ASYNC";
  export type NTimesOptions = {
    count: number; pacing: NTimesPacing; mode: NTimesMode;
    fixedDelayMs?: number | null; minDelayMs?: number | null; maxDelayMs?: number | null;
  };
  // extend ChaosOptions with `nTimes?: NTimesOptions | null;`
  export type NTimesSyncResult = {
    flowType: string; count: number; succeeded: number; failed: number;
    correlationId: string; eventIds: string[]; historyIds: string[];
  };
  // publishNTimes returns { kind: "sync"; result: NTimesSyncResult } | { kind: "async"; run: BatchRunResponse }
  ```
  Implement `publishNTimes` to POST `/api/v0/flows/{flowType}/n-times` and discriminate on the
  HTTP status (200 vs 202).
- **Run-results reuse**: `batches-page.tsx` / `batch-run-page.tsx` show N-Times runs; surface the
  `kind`/`pacing`/`mode` columns added in Task 003 so a run reads as "N Times · Burst · Async".

```mermaid
flowchart LR
  panel["ChaosOptionsPanel<br/>N Times: count/pacing/mode/delays"] --> form[single-flow-page]
  form -->|strategy=nTimes| confirm{confirm}
  confirm -->|ok| call["publishNTimes(flowType, req)"]
  call -->|200 SYNC| agg["aggregate result panel"]
  call -->|202 ASYNC| run["run-results view (batch-run-page)"]
```

## Implementation Notes
Files to modify:
- `chaos-admin/src/features/chaos/chaos-options-panel.tsx` — union, form state, options list,
  conditional inputs, `buildChaosOptions`, confirm predicate, client `CHAOS_LIMITS` additions
  (`maxNTimes`, `maxNTimesSync`).
- `chaos-admin/src/features/chaos/single-flow-page.tsx` — N-Times submit routing + sync/async
  result handling; reuse the existing confirmation dialog.
- `chaos-admin/src/lib/api.ts` — `NTimesOptions`/`NTimesPacing`/`NTimesMode`/`NTimesSyncResult`
  types, `ChaosOptions.nTimes`, `publishNTimes()`; extend `BatchRunResponse` with
  `kind`/`pacing`/`mode`.
- `chaos-admin/src/features/chaos/batches-page.tsx` / `batch-run-page.tsx` — render the new run
  metadata so N-Times runs are legible.

Notes:
- Keep wire enum casing aligned with the backend (`BURST`/`LINEAR`/`RANDOM`, `SYNC`/`ASYNC`).
- Default Mode = Sync so the common quick case stays inline; large counts nudge toward Async via
  the confirm copy.
- Do not change the plain publish path; N-Times is a separate call.

## Non-Functional Requirements
- No layout regression to the two-column Single Flow Run shell (Phase 011); the N-Times inputs
  live in the right-column chaos widget.
- Client clamps mirror server caps so the UI can't submit a request the server will 400 (server
  remains authoritative).
- Accessible labels; copy clearly separates N Times from Burst to prevent operator error.

## Dependencies
- **Task 002** (SYNC `200` aggregate contract) and **Task 003** (ASYNC `202` run handle +
  `kind`/`pacing`/`mode` on the run response).
- Existing `features/chaos` components, `lib/api.ts`, run-results pages, Phase 011 Single Flow Run
  shell.
- [ADR-016](../../decisions/016-n-times-distinct-transaction-chaos-strategy.md).

## Risks & Mitigations
- *Operator confuses N Times with Burst* → explicit copy + distinct labels/help text; the two
  remain separate strategy entries.
- *Async run lost after handoff* → navigate to / link the run-results view with the returned run
  id; the run is also listed on the runs page.
- *Status-based discrimination (200 vs 202) brittle* → the API client reads the HTTP status
  explicitly and returns a discriminated union.

## Testing Strategy
- **Vitest + Testing Library + MSW**: N Times reveals count/pacing/mode/delay inputs;
  pacing-conditional inputs (Linear fixed; Random min/max; Burst none); `buildChaosOptions`
  payload matches the contract and clamps to limits; SYNC mock → aggregate panel rendered; ASYNC
  mock (`202`) → run-results handoff; confirmation dialog appears and gates the call; run-results
  page shows `kind=N_TIMES` runs.
- Regression: other chaos strategies and the CSV upload/run pages render unchanged.

## Deployment Strategy
Additive frontend change; ships with the Phase 013 backend. No flag. Backward-compatible with a
backend that already exposes `/n-times`; the page degrades gracefully (N Times option simply
calls the new endpoint).
