# ADR 017 — Lifecycle transaction flows & outcome orchestration (Collection / Settlement / Disbursement)

## Status
Accepted — 2026-06-25 — Phase [014 — Collection, Settlement & Disbursement Flows](../phases/014-collection-settlement-disbursement-flows/DESIGN.md)

## Context

Idea `007_collection_settlement_disbursement_flow.md` adds the three transaction
types Phase 011 deferred — **Collection**, **Settlement**, **Disbursement** — to the
Single Flow Run console. Two of them are not single events but **multi-step
lifecycles** the ledger consumes as a sequence:

- **Disbursement:** `disbursement.initiated` → then `disbursement.completed` **or**
  `disbursement.failed`.
- **Settlement:** `organization.va.settlement.initiated` → then
  `organization.va.settlement.completed` **or** `organization.va.settlement.failed`.
- **Collection:** a single `collection.completed` (no lifecycle).

The idea adds an operator-chosen **outcome** for the two lifecycle types:

> The operator can decide whether it should **fail**, **succeed**, or be **random**
> (system decides). If the operator selects fail or success, after the *initiated*
> event is successful the operator **confirms** the completion/failure form before
> submission. All chaos options apply.

Several forces shaped this decision:

1. **The ledger links the lifecycle by `transaction_id`, not `reservation_id`.**
   Verified against `ss-ledger-service`: on `disbursement.initiated` the ledger
   creates a reservation keyed by `transactionRef = transaction_id`; on
   `disbursement.completed`/`failed` it looks the reservation up **by
   `transaction_id`** (`findByAccount…AndTransactionRef`) and **ignores the inbound
   `reservation_id`** (mismatches silently use the stored reservation). Settlement
   uses the same internal reservation mechanism, but its events carry **no**
   `reservation_id` at all. So the only value that *must* be carried consistently
   across a lifecycle is **`transaction_id`** (and `settlement_request_id` for
   settlement). See [ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md) for
   how the (cosmetic-but-required) `reservation_id` field is sourced.

2. **The "confirm before submission" gate is a human-in-the-loop step.** A headless
   server orchestration cannot pause mid-run for an operator to inspect and edit the
   completion form. The interactive outcomes (SUCCEED/FAIL) are inherently a UI
   concern; only RANDOM is unattended.

3. **The existing publish path is single-event and unchanged.** `POST
   /api/v0/flows/{flowType}` builds and publishes exactly one `EventEnvelope<T>`
   through the chaos pipeline. Each lifecycle phase is already (or will be) its own
   `FlowType` with its own builder — settlement already splits into
   `SETTLEMENT_INITIATED`/`_COMPLETED`/`_FAILED`. The cheapest, most consistent way
   to drive a lifecycle is therefore **two ordinary single-event publishes**, not a
   new bespoke transport.

4. **ADR-014 put inference/prefill client-side.** The Single Flow Run already infers
   org/currency/tenant in the browser off the loaded VA object and assembles the
   request client-side. Carrying initiated-phase values forward into the
   completed/failed form is the same kind of client-side, pure-data carry-over.

5. **The user wants volume for the unattended case.** RANDOM is explicitly
   "unattended", and the user confirmed **N-Times applies to RANDOM lifecycles** —
   "run this whole lifecycle N times" — which needs background, run-tracked execution
   (Phase 013's model), *not* an interactive wizard.

## Decision

### 1. Each lifecycle **phase** is its own `FlowType`; lifecycles are a catalog grouping over them

Add `DISBURSEMENT_INITIATED` and `DISBURSEMENT_FAILED` to `FlowType` (joining the
existing `DISBURSEMENT_COMPLETED`); settlement already has its three. Every phase has
its own builder and its own field-descriptor list, so each renders a real form and
publishes through the **unchanged** `POST /flows/{flowType}` path.

The catalog gains a **`FlowLifecycle`** descriptor that groups the phases of one
transaction type and declares the carry-over from `initiated` into the secondary
phases:

```java
@RecordBuilder
public record FlowLifecycle(
    String label,                 // radio label, e.g. "Disbursement"
    FlowType initiated,           // DISBURSEMENT_INITIATED
    FlowType completed,           // DISBURSEMENT_COMPLETED
    FlowType failed,              // DISBURSEMENT_FAILED
    List<CarryOver> carryOver) {} // initiated-field -> secondary-field copies

public record CarryOver(String fromField, String toField) {}
```

- The **initiated** entry is `runnerVisible = true` and carries `lifecycle`; its
  `lifecycle.label` is what the radio shows ("Settlement", "Disbursement").
- The **completed**/**failed** entries are `runnerVisible = false` (not standalone
  radio choices) but keep full descriptors — the wizard renders them.
- **Collection** is `runnerVisible = true` with `lifecycle = null` (single-shot).

This keeps the catalog the single source of truth (ADR-014): adding/altering a
lifecycle is a server-side descriptor change, and the radio logic ("show
`runnerVisible` entries") is unchanged — it just shows three more types.

### 2. SUCCEED / FAIL run as a **client-side, two-step wizard** over the unchanged publish path

- **Step 1:** render the `initiated` descriptors (+ a chaos-options panel) and
  publish via `POST /flows/{initiatedType}`. Hold the submitted values + the minted
  `transaction_id`/`settlement_request_id` in the browser.
- **Step 2:** render **both** forms — the initiated form **read-only** (a summary of
  what was published) above the **editable, prepopulated** completed/failed form (its
  own chaos panel). Carry-over fields (`transaction_id`, principal/amount, VA ids,
  `disbursement_subtype`, …) are copied per the `carryOver` map and are
  **overridable** (deliberately sending a mismatch is a valid chaos scenario). The
  operator **confirms**, and the wizard publishes via
  `POST /flows/{completedType|failedType}`.
- Each publish independently carries its own `ChaosOptions`, so **chaos applies
  per event**.
- **N-Times does not apply** to the interactive outcomes (the confirm gate is
  single-shot per lifecycle).

For Disbursement, between the two steps the wizard resolves `reservation_id` by
polling the ledger read-proxy ([ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md)).
Settlement needs no such step (its events carry no `reservation_id`).

### 3. RANDOM is **unattended**, server-orchestrated, and **N-Times-capable**, reusing the batch-run infrastructure

RANDOM has no human gate, so it runs on the backend through a dedicated route
(`POST /api/v0/flows/{lifecycleType}/random-lifecycle`) that, for a requested
**count** N (default 1), executes N **distinct** lifecycles:

```
for each of N lifecycles (distinct transaction_id, shared correlation_id optional):
  publish initiated
  (disbursement only) resolve reservation_id  [ADR-018]
  decide SUCCEED|FAIL at random
  publish completed | failed
```

Execution **reuses the Phase 013 / Phase 003 async runner**: a `BatchRun` with a new
`RunKind.LIFECYCLE` discriminator tracks the run; each lifecycle is tracked as
`BatchRow`(s); status rolls up (`COMPLETED` / `COMPLETED_WITH_FAILURES` / `FAILED`)
exactly as N-Times/CSV do, and is polled in the existing run-results view. `count = 1`
is just a run of one. Per-event chaos may still be supplied and applies to each
published event. The N-Times **count** here means *number of lifecycles*, not the
per-event N-Times of Phase 013.

### 4. `transaction_id` is the carry-over linkage; `reservation_id` is sourced separately

Within one lifecycle, the `initiated` phase **mints** the `transaction_id`
(`autogen = UUID_V4`) and every subsequent phase **reuses it** — this is what the
ledger matches on. `reservation_id` is required on the wire but ledger-ignored; it is
sourced per [ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md). Distinct
lifecycles (N-Times RANDOM) each get a fresh `transaction_id`.

## Consequences

**Positive**
- Reuses the unchanged single-event publish path and the Phase 013 run infrastructure;
  the only genuinely new backend surface is the RANDOM lifecycle runner + endpoint and
  the catalog's `FlowLifecycle` grouping.
- The human confirmation gate the idea asks for lives where it can actually
  work — the browser — consistent with ADR-014's client-side ethos.
- Per-event chaos falls out for free: each phase is an ordinary publish with its own
  `ChaosOptions`.
- Adding/altering a lifecycle is mostly descriptor data; the renderer and runner are
  generic over the `FlowLifecycle` shape.

**Negative / trade-offs**
- **Two execution paths** for the lifecycle (client wizard for SUCCEED/FAIL, server
  runner for RANDOM). Mitigated by both publishing the *same* per-phase `FlowType`
  events through the *same* builders — only the orchestration differs.
- A partially-completed interactive lifecycle is possible (initiated published, step 2
  abandoned). That is acceptable — and realistic — for a chaos harness; the ledger
  parks the orphaned reservation, which is itself an observable condition.
- The RANDOM runner must replicate the wizard's carry-over + reservation resolution
  server-side. Mitigated by extracting the carry-over map (catalog) and the reservation
  lookup ([ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md)) as shared
  services consumed by both paths.

**Open items (flagged, not blocking)**
- **`disbursement.completed` destination role.** Idea says "System Settlement
  Account"; the ledger sample comment says platform float. Both are SYSTEM accounts;
  the slot defaults to `SETTLEMENT_ACCOUNT` and is operator-overridable. Confirm with
  product.
- **Cross-border subtype.** `disbursement_subtype = CROSS_BORDER` makes
  `destination_country`/`corridor`/`applied_fx_rate` required on completed. The idea
  targets `DOMESTIC`; the subtype select defaults `DOMESTIC` and the cross-border
  fields stay advanced/optional. Validate when cross-border chaos is exercised.
