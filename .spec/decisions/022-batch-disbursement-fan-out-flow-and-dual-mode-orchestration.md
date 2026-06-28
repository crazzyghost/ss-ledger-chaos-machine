# ADR 022 — Batch disbursement as a fan-out flow family with dual-mode (interactive / unattended) orchestration

## Status
Accepted — 2026-06-27 — Phase [016 — Batch Disbursement](../phases/016-batch-disbursement/DESIGN.md)

## Context

Idea `011_batch_disbursement.md` adds **batch disbursement** to the Single Flow Run
console — explicitly **"different from [the] csv upload flow"**. The operator:

> - first form creates a **batch reservation** — accepts source and destination and a
>   number **N** of items;
> - then **cycles through N "disbursement" forms deciding which pass or fail**;
> - or runs **automatic flows** — split amount and fees by N, and select **how many pass
>   or fail, or whether all should pass or fail**.

Verified against `ss-ledger-service` source + `bin/kafka-payload-samples.md`, batch
disbursement is a **real ledger fan-out lifecycle**, not "the single disbursement flow
run N times". It is a sequence of four logical events over three topics:

1. **`disbursement.batch.initiated`** with `operation = BATCH_RESERVATION_REQUEST` — the
   ledger creates **one BATCH reservation** for `total_amount` (= `total_principal_amount
   + total_fees`) against the **source (ORGANIZATION) VA**, declares `item_count = N`, and
   keys the batch by `batch_id` (stored as `transaction_request_id`; reservation
   `transaction_ref = batch_id`). Validates `total_amount == total_principal + total_fees`
   exactly, funds ≥ total, and source VA ORGANIZATION-owned; a violation persists the batch
   as `FAILED` (it does not throw).
2. **`disbursement.batch.initiated`** with `operation = BATCH_ITEM_REQUEST` — **inert**:
   the ledger records the event for idempotency but performs **no aggregate side-effects**.
   Published once per item for downstream intent tracking.
3. **`disbursement.batch.item.completed`** — captures the item's gross (`principal_amount`
   + Σ`fees`) from the BATCH reservation and posts a journal crediting the destination VA
   with the principal and each fee VA with its fee; increments `processed_count`.
4. **`disbursement.batch.item.failed`** — **partially releases** the item's gross back to
   `AVAILABLE`; **no journal**; increments `failed_count`.

The ledger derives batch status from counters (`BatchStatusResolver`): `INITIATED` →
`IN_PROGRESS` → (`COMPLETED` | `FAILED` | `PARTIALLY_COMPLETED`) once
`processed + failed == item_count`. Exceeding `item_count` throws.

Forces shaping the decision:

1. **This is a fan-out, not a linear lifecycle.** ADR-017's `FlowLifecycle` models one
   `initiated → completed | failed` *single* transaction. A batch is **one reservation →
   N items, each item itself a request → completed | failed**. The existing
   `DISBURSEMENT_INITIATED/COMPLETED/FAILED` flow types and the `FlowLifecycle`/wizard
   built in Phase 014 describe a **different** event family (single disbursement) and must
   **not** be reused for the wire payloads — the batch events have their own field sets,
   topics, and an `operation` discriminator.

2. **The existing single-event publish path is unchanged and sufficient.** Every chaos
   flow already publishes exactly one `EventEnvelope<T>` through `POST
   /api/v0/flows/{flowType}` → `FlowEngine.execute(...)`. The cheapest, most consistent way
   to drive a batch is **a sequence of ordinary single-event publishes** (reservation, then
   per item: request + terminal), each a real `FlowType` with its own builder — exactly how
   Phase 014 drove its lifecycle.

3. **"Cycle through N forms" is a human-in-the-loop gate; "automatic" is headless.** Like
   Phase 014's Succeed/Fail-vs-Random split (ADR-017), the manual per-item pass/fail
   decision is inherently a **UI** concern (the operator inspects and edits each item
   before publishing), while the automatic mode (even split + outcome policy) is
   **unattended, run-tracked** server work — a natural fit for the Phase 003/013 batch
   runner.

4. **`reservation_id` is ledger-assigned and the driver never mints it.** The required
   `reservation_id` on the item terminal events is **ignored** by the ledger handler (it
   uses the batch's stored reservation); the linkage that matters is `batch_id`. Sourcing
   the real id is a read-proxy concern — see
   [ADR-023](023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md).

5. **The ledger dedupes by structured idempotency keys, not by event id.** Batches dedupe
   on `batch_id` and items on `item_id` via the metadata `idempotency_key`
   (`disbursement-batch-initiated:{batch_id}`,
   `disbursement-batch-item-completed:{batch_id}:{item_id}`, …). The chaos builders'
   default key is `"<event-type>:<eventId>"` (ADR-016), which would **not** reproduce the
   ledger's dedupe semantics — so these builders must emit the **structured** keys.

Options considered:

- **(A) Reuse the Phase 014 single-disbursement lifecycle "N times" (RANDOM lifecycle
  runner).** Wrong contract: it emits `disbursement.initiated/completed/failed`, not the
  batch events; it has no shared reservation, no `item_count`, no batch close semantics. It
  would not exercise the ledger's batch code path at all.
- **(B) Model batch disbursement only via the CSV batch runner.** The idea explicitly
  contrasts this with CSV upload; CSV has no notion of a shared reservation gate, per-item
  pass/fail decisions, or auto-split. Rejected as the primary surface (batch
  disbursement/settlement via CSV remain separately out of scope per ARCHITECTURE §6).
- **(C) A new fan-out flow family + dual-mode orchestration over the unchanged publish
  path, reusing the batch-run infrastructure for the unattended path.** Faithful to the
  ledger contract and to the idea's two modes; maximal reuse of existing machinery.

## Decision

Adopt **(C)**.

### 1. Four new `FlowType`s + builders + v1 models, on the unchanged publish path

Add to `flow/model/FlowType`:

- `DISBURSEMENT_BATCH_RESERVATION_REQUEST` → topic `disbursement.batch.initiated`,
  `operation = BATCH_RESERVATION_REQUEST`.
- `DISBURSEMENT_BATCH_ITEM_REQUEST` → topic `disbursement.batch.initiated`,
  `operation = BATCH_ITEM_REQUEST`.
- `DISBURSEMENT_BATCH_ITEM_COMPLETED` → topic `disbursement.batch.item.completed`.
- `DISBURSEMENT_BATCH_ITEM_FAILED` → topic `disbursement.batch.item.failed`.

Each gets a `flow/model/v1/*EventData` record (snake_case, `@RecordBuilder`), a
`flow/builder/*FlowBuilder` registered in `FlowBuilderRegistry`, and reuses the shared
`TransactionFeeLine` + `FeeLines` helper (item terminal events carry `fees[]`) and the
`authorised_principal` assembly (reservation). `source = payment-service`. The two
`disbursement.batch.initiated` builders set the `operation` discriminator. Builders emit
the **structured ledger idempotency keys** (`disbursement-batch-initiated:{batch_id}`,
`disbursement-batch-initiated:{batch_id}:{item_id}`,
`disbursement-batch-item-{completed,failed}:{batch_id}:{item_id}`) instead of the default
`event-type:eventId`, so the ledger's batch/item dedupe and the chaos duplicate/replay
strategies behave faithfully. `transactionReference` = `batch_id` (reservation) / `item_id`
(items). All events of one batch share **one metadata `correlation_id`** plus a `data`
`batch_correlation_id`, so history groups the batch.

### 2. A new catalog grouping for the fan-out (distinct from `FlowLifecycle`)

Add a `flow/dto/BatchDisbursementGroup` catalog record that groups the four phases and
declares two carry-over maps:

```java
@RecordBuilder
public record BatchDisbursementGroup(
    String label,                       // radio label: "Batch Disbursement"
    FlowType reservation,               // DISBURSEMENT_BATCH_RESERVATION_REQUEST
    FlowType itemRequest,               // DISBURSEMENT_BATCH_ITEM_REQUEST
    FlowType itemCompleted,             // DISBURSEMENT_BATCH_ITEM_COMPLETED
    FlowType itemFailed,                // DISBURSEMENT_BATCH_ITEM_FAILED
    List<CarryOver> reservationToItem,  // batch_id, batch_correlation_id, merchant_id,
                                        //   reservation_id, source/dest VA, currency, subtype
    List<CarryOver> itemRequestToTerminal) {} // item_id, item_sequence, principal,
                                              //   item_fee, va, provider, corridor…
```

Only the **reservation** catalog entry is `runnerVisible = true` and carries the group; its
`label` is the radio choice ("Batch Disbursement"). The three other phases are
`runnerVisible = false` but keep full field descriptors so the wizard/runner can render and
assemble them. This keeps the catalog the single source of truth (ADR-014): the radio logic
("show `runnerVisible` entries") is unchanged. `FlowCatalogEntry` gains an optional
`batchGroup` field alongside the existing `lifecycle` field (a flow is single-shot,
`lifecycle`, **or** `batchGroup` — at most one is non-null).

### 3. **Manual** mode = a client-side, multi-step wizard over the unchanged publish path

- **Step 1 (reservation):** render the reservation descriptors (+ chaos panel) and publish
  `DISBURSEMENT_BATCH_RESERVATION_REQUEST`. Hold the minted `batch_id`/`batch_correlation_id`,
  source/destination VA, totals, subtype, and `item_count = N`.
- **Resolve `reservation_id`** by polling the batch-summary read-proxy
  ([ADR-023](023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md));
  timeout → manual entry.
- **Steps 2…N+1 (per item):** the operator cycles through N item forms. Each is
  prepopulated (even split by default — see §5 — and carry-over from the reservation),
  shows a **Pass / Fail** toggle, is fully editable, and carries its **own** chaos panel.
  On confirm the wizard publishes `DISBURSEMENT_BATCH_ITEM_REQUEST` then
  `DISBURSEMENT_BATCH_ITEM_COMPLETED` (Pass) or `DISBURSEMENT_BATCH_ITEM_FAILED` (Fail).
- A live progress panel reflects the ledger batch status/counters (read-proxy) and the
  client's running totals.

### 4. **Automatic** mode = an unattended, run-tracked server runner reusing the batch infra

A dedicated route `POST /api/v0/flows/disbursement-batch/run` accepts the reservation
intent, `item_count = N`, a **split mode**, an **outcome policy**, optional per-event chaos,
and optional pacing; it returns `202` with a `BatchRunResponse` run handle. Server logic:

```
publish reservation (mint batch_id, totals, item_count=N)
resolve reservation_id  [ADR-023]  (timeout → placeholder; ledger ignores it)
split principal/fees across N items  (§5)
for each of N items (tracked as a BatchRow):
  decide PASS|FAIL per outcome policy
  publish item.request
  publish item.completed | item.failed
finalizeRun → COMPLETED | COMPLETED_WITH_FAILURES | FAILED
```

Execution **reuses** `batch.service.BatchRunner` behind a new `RunKind.BATCH_DISBURSEMENT`;
each item is a `BatchRow` (a request+terminal unit), status rolls up via the existing
`finalizeRun`, and the run is polled in the existing run-results view — exactly as N-Times
(`N_TIMES`) and the RANDOM lifecycle (`LIFECYCLE`) runners do. Pacing reuses `PacingPlan`.

**Outcome policy** (`flow/dto/BatchOutcomePolicy`):

```java
public record BatchOutcomePolicy(Mode mode, @Nullable Integer passCount, @Nullable Long seed) {}
public enum Mode { ALL_PASS, ALL_FAIL, COUNT, RANDOM }
```

- `ALL_PASS` / `ALL_FAIL` — every item completes / fails.
- `COUNT` — exactly `passCount` items pass (the first `passCount` by sequence), the rest
  fail. `0 ≤ passCount ≤ N`.
- `RANDOM` — per-item outcome decided by the existing deterministic `OutcomeDecider`
  (seed + index; no `Math.random()`, so runs are resume-safe), with `passCount` interpreted
  as an optional target pass-count when present.

### 5. Even split with remainder absorption; the amount invariant is deliberately breakable

In automatic mode (and as the wizard's default prefill), `total_principal_amount` and
`total_fees` are split **evenly across N** at the currency's scale; the **last item absorbs
the rounding remainder** so `Σ item principal == total_principal` and `Σ item fee ==
total_fees` exactly, preserving the ledger invariant `total_amount == total_principal +
total_fees` and ensuring the BATCH reservation fully captures/releases. The ledger does
**not** enforce that the item sums equal the batch totals, so an operator may **deliberately
break** the invariant (manual edits, or an "unbalanced" chaos toggle) to probe how the
ledger handles an over-/under-drawn reservation — an observable chaos condition.

### 6. Caps & guards

Extend `ChaosLimits` (`chaos.limits.*`): `maxBatchItems` (e.g. 100) bounds N; automatic
runs are **ASYNC-only** (like RANDOM lifecycles), so no sync-duration guard is needed;
`passCount` is validated into `[0, N]`; oversize N or out-of-range `passCount` → `400`.
Slot resolution failures (missing source/destination/fee VA) → `400` before any publish.

## Consequences

**Positive**

- Drives the **real** ledger batch code path (shared reservation, partial capture/release,
  counter-driven batch close) — something neither the single-disbursement lifecycle nor the
  CSV runner can do.
- Maximal reuse: the unchanged `POST /flows/{flowType}` publish path, `FlowEngine`,
  `FeeLines`/`TransactionFeeLine`, the catalog/descriptor renderer, `BatchRunner` +
  `BatchRun`/`BatchRow` run tracking + run-results UI, `OutcomeDecider`, and `PacingPlan`.
  The genuinely new backend surface is the four builders/models, the `BatchDisbursementGroup`
  grouping, the automatic runner + endpoint, and the read-proxy (ADR-023).
- Both modes publish the **same** per-phase `FlowType` events through the **same** builders;
  only the orchestration (client wizard vs server runner) differs, so they cannot drift.
- Per-event chaos falls out for free (each phase is an ordinary publish with its own
  `ChaosOptions`), and the deliberately-breakable amount invariant is a first-class chaos
  surface.

**Negative / trade-offs**

- **Two orchestration paths** (client wizard for manual, server runner for automatic),
  mirroring ADR-017's accepted trade-off; mitigated by shared builders + carry-over map.
- **A fourth `RunKind`** (`BATCH_DISBURSEMENT`) further overloads the `batch_run`/`batch_row`
  tables, whose name is already slightly misleading (ADR-016). Accepted; a future "flow runs"
  generalization can rename without changing semantics. The run row models **one item** (a
  request+terminal unit), not one whole batch — documented so reviewers don't expect 1 row =
  1 batch.
- **Builder idempotency-key override.** These four builders deviate from the default
  `event-type:eventId` key to the structured ledger form; a builder test must pin it, or the
  ledger's batch/item dedupe (and the chaos replay strategies) would misbehave.
- **An abandoned manual batch** (reservation published, some items never sent) leaves the
  ledger batch `IN_PROGRESS` with a partially-held reservation — acceptable and observable
  for a chaos harness.

**Open items (flagged, not blocking)**

- **Destination role for the reservation.** The sample comments call `destination_va_id` the
  platform-float SYSTEM VA that receives principal credits as items complete; the idea says
  source = ORG, destination = system. The reservation `destination` slot defaults to
  `PLATFORM_FLOAT` (SYSTEM) and is operator-overridable. Confirm with product.
- **Per-item `virtual_account_id`.** In the samples every item's `virtual_account_id` equals
  the batch `source_va_id` (the ORG VA). The runner/wizard default to carrying it over;
  per-item override is allowed for chaos. Confirm whether items ever target a different VA.
- **`item.request` publication.** The ledger treats it as inert. Both modes publish it by
  default for production fidelity; a toggle may skip it. Confirm it should always be sent.
