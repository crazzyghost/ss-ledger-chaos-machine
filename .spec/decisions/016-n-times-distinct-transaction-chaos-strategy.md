# ADR 016 - N-Times Distinct-Transaction Chaos Strategy (dual sync/async execution)

## Status
Accepted

## Context
Idea `006_N_TIMES_chaos_strategy.md` asks for an `NTimesOptions` chaos strategy that
**runs a flow N times against the same source/destination accounts**, explicitly
contrasted with the existing **Burst** strategy:

> different from burst in that it is **not a duplicate event** — it runs the flow N times
> against the same source destination accounts

…with three timing sub-configs: **burst** (no delay), **linear** (fixed delay between
events), and **random** (random delay between events).

The forces at play, read from the current code:

1. **Idempotency is keyed on identifiers, so "distinct" is a precise technical requirement.**
   - The envelope idempotency key is derived per builder as `"<event-type>:" + ctx.eventId()`
     (e.g. `TopUpConfirmedFlowBuilder:59`, `TransferRequestedFlowBuilder:60`).
   - The **business** transaction id (`topup_request_id`, `transfer_request_id`,
     `sweep_request_id`, …) is read from client-supplied `flowFields` and is marked
     `autogen = UUID_V4` in the Phase 011 field descriptors
     ([ADR-014](014-flow-catalog-field-descriptors-and-client-side-inference.md)).
   - The existing **Burst** (`ChaosPlan.applyBurst`) rebuilds each copy with a *new event id*
     but **preserves the base idempotency key** (`ChaosPlan.rebuildWithNewEventId` copies
     `base.metadata().idempotencyKey()`), and never re-rolls the payload `*_request_id`. So a
     downstream ledger that dedups on idempotency key / business request id collapses a burst to
     **one** logical effect — exactly the "duplicate event" the idea describes.
   - Therefore N-Times must re-roll **both** the event id (→ a fresh derived idempotency key)
     **and** the payload `*_request_id` per iteration to produce N genuinely-distinct,
     non-deduped transactions. Holding the **slots, amounts, currency and organization ids
     constant** is what makes them "the same transfer."

2. **Two execution shapes already exist in the codebase**, and the user confirmed *both* are
   wanted, with concurrency tied to the mode (*"both work"; "both depending on if async or
   sync"*):
   - **Synchronous, in-line** — how every other chaos strategy runs today
     (`FlowEngine.execute` loops `ChaosPlan.expand(...)` results sequentially, sleeping
     `send.delay()` on the request thread, which is a virtual thread).
   - **Asynchronous, run-tracked** — the Phase 003 CSV batch runner
     (`batch.service.BatchRunner`) already executes `flowEngine.execute(request)` per item on a
     bounded virtual-thread pool (`Executors.newVirtualThreadPerTaskExecutor()` + a
     `Semaphore(maxWorkers)`), with optional rate delay, persisting `BatchRun`/`BatchRow`
     status. N-Times "run a flow N times" is structurally **a batch of N identical rows of one
     flow, each with a fresh transaction id**.

3. **Long delayed runs do not fit a synchronous HTTP request.** `count × maxDelayMs` can reach
   tens of minutes (e.g. 100 × 30 s), which would block the request thread and risk gateway
   timeouts. Concurrency ("fire together") is also unsafe to do on the caller's request thread.

Options considered:
- **(A) Sync-only chaos strategy.** Smallest change, faithful to "chaos strategy" framing, but
  cannot satisfy long delayed runs or true concurrency.
- **(B) Async-only run subsystem.** Robust, reuses batch infra, but over-builds the common
  small/fast case and moves N-Times out of the inline chaos-options widget.
- **(C) Dual-mode strategy: SYNC in-line + ASYNC run-tracked, sharing one fan-out core.**
  Honours the user's "both" answer and the idea's per-config concurrency intent.

## Decision
Adopt **(C)**. N-Times is a new **mutually-exclusive `ChaosOptions` field** (consistent with the
existing "first non-null strategy wins" model) with the following shape and rules.

**Contract (`flow.chaos`):**
```java
record NTimesOptions(
    int count,
    Pacing pacing,          // BURST | LINEAR | RANDOM
    ExecutionMode mode,     // SYNC  | ASYNC
    @Nullable Long fixedDelayMs,  // required for LINEAR
    @Nullable Long minDelayMs,    // required for RANDOM
    @Nullable Long maxDelayMs)    // required for RANDOM
{}
enum Pacing { BURST, LINEAR, RANDOM }
enum ExecutionMode { SYNC, ASYNC }
```

1. **Distinctness via re-roll, not duplication.** A shared `NTimesExpander` fans one
   `FlowRequest` into N per-iteration `FlowRequest`s. For each iteration it re-rolls every
   `flowFields` key whose descriptor carries `autogen = UUID_V4` (the `*_request_id`; falling
   back to a `*_request_id`-suffix convention if no descriptor marks one) to a fresh
   `UUID.randomUUID()`. `FlowEngine.execute` then derives a fresh event id (→ fresh
   `"<event-type>:<eventId>"` idempotency key) per iteration. **Slots, amounts, currency,
   channel and organization ids are held constant.** All N share **one correlation id**
   (generated once, or the caller's override) so the run is groupable in history by
   `correlation_id`; correlation id is a tracing field, not an idempotency key, so sharing it
   does not undermine distinctness.

2. **Three pacings.** `BURST` = no inter-event delay; `LINEAR` = `fixedDelayMs` between
   consecutive events; `RANDOM` = a fresh delay in `[minDelayMs, maxDelayMs]` between events.

3. **Dual execution, concurrency tied to mode:**
   - **SYNC** runs in-line on the request thread, **sequentially**, applying the pacing delay
     before each iteration after the first; `BURST` is sequential-zero-delay ("fast", not
     parallel). Returns an aggregate summary (200).
   - **ASYNC** submits the N expanded requests to the **reused batch runner**, returns a run
     handle (202), and is polled via the existing run endpoints. `BURST` async fans the N
     events out **concurrently** across virtual threads (bounded by the existing
     `chaos.batch.workers` semaphore) to create a genuine contention spike on the account pair;
     `LINEAR`/`RANDOM` async run sequentially with the configured gap.

4. **Async reuses the batch run tables**, not new ones: `batch_run`/`batch_row` gain a nullable
   `kind` discriminator (`CSV` default | `N_TIMES`), `filename` becomes nullable, and the run
   records the pacing/mode. N-Times runs therefore appear in the existing run-results UI.

5. **Caps & guards** (extend `ChaosLimits`, `chaos.limits.*`): `maxNTimes` (100), `maxNTimesSync`
   (25), `maxSyncDurationMs` (60 000 — reject a SYNC run whose projected `count × max-gap`
   exceeds it, with a 400 pointing at ASYNC); per-gap delays reuse `maxDelayMs` (30 000); async
   concurrency reuses `chaos.batch.workers` (20). Over-cap requests are rejected `400`, matching
   the other strategies.

6. **A dedicated endpoint** `POST /api/v0/flows/{flowType}/n-times` owns N-Times (its
   request/response differ from single publish: aggregate summary on SYNC `200`, run handle on
   ASYNC `202`). The plain `POST /api/v0/flows/{flowType}` rejects a body carrying
   `chaos.nTimes` with a `400` that points at the dedicated route, so there is exactly one way
   in.

7. **Burst stays.** The existing top-level `Burst` strategy is retained unchanged as the
   *duplicate-keyed* volume probe; N-Times is the *distinct-transaction* probe. The naming
   overlap (N-Times' `BURST` **pacing** vs the `Burst` **strategy**) is resolved by docs and UI
   copy, not by renaming the ledger-facing or existing contract.

## Consequences
**Positive**
- Faithful to the idea: N-Times is a selectable chaos strategy producing N real, distinct
  transactions between the same accounts, with the three requested pacings.
- One `NTimesExpander` core feeds both paths, so SYNC and ASYNC cannot drift in how distinctness
  is produced; the ASYNC path reuses proven batch machinery (virtual-thread pool, semaphore,
  run/row tracking, run-results UI) with only a discriminator-column migration.
- Reuses the Phase 011 `autogen` descriptors as the single source of truth for "which field is
  the business transaction id," so adding a flow needs no N-Times-specific wiring.
- The SYNC duration guard and ASYNC offload keep the harness itself healthy (its own resilience
  posture, ARCHITECTURE §9) while it stresses the ledger.

**Negative / trade-offs**
- **Naming overlap** between the existing `Burst` strategy and N-Times' `BURST` pacing is a
  documented, permanent foot-gun in the UI; mitigated by copy ("Burst = duplicate event" vs
  "N Times = distinct transactions") but not eliminated.
- **Coupling to "batch" naming.** N-Times async runs live in `batch_run`/`batch_row` behind a
  `kind` discriminator rather than a freshly-named `flow_run` table; this avoids a migration +
  UI refactor now at the cost of a slightly misleading table name. A future "flow runs"
  generalization can rename without changing N-Times semantics.
- **Two execution code paths** to test and maintain (in-line loop vs runner), versus a single
  path — accepted because the user explicitly wants both and they serve different load shapes.
- **Depends on `autogen` descriptors being accurate.** A flow whose request-id field is not
  marked `UUID_V4` falls back to the `*_request_id` convention; if a flow used a differently
  named business key the expander would not re-roll it and the ledger could dedup — flagged as a
  per-flow check in the task specs.
- The existing `Burst` cumulative-delay quirk (`delay = delayBetweenMs * i` applied before each
  sequential send) is **not** inherited: N-Times pacing uses the *inter-event gap* directly,
  which the engine's pre-send sleep already realises as true linear spacing. This is called out
  so the two strategies' timing is understood to differ deliberately.
