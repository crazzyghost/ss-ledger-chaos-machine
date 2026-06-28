# ADR 028 — Reservation lifecycle projection from `ledger.reservation.created` / `.released`

## Status
Accepted — 2026-06-27 — Phase [019 — Reservation Lifecycle Tracking](../phases/019-reservation-lifecycle-tracking/DESIGN.md)

Builds on [ADR-024](024-multi-event-ledger-outbound-consumer.md) (the multi-event consumer
this rides on). **Complements — does not supersede —**
[ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md) /
[ADR-023](023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md): the wizards
keep sourcing `reservation_id` via the ledger read-proxy poll; this ADR adds a *push-fed
state projection* alongside it. **Part 3 of 4** ("testing ledger Kafka events"; Part 1 =
[ADR-025](025-transaction-failure-projection-and-request-id-correlation.md), Part 2 =
[ADR-027](027-balance-history-projection-from-ledger-balance-updated.md)).

## Context

The ledger emits two reservation lifecycle events as side effects. Verified against
`ss-ledger-service`:

```java
// reservation/events/v1/ReservationLifecycleEventData.java  (snake_case, addTypeInfo(false))
public record ReservationLifecycleEventData(
    UUID       reservationId,
    UUID       accountId,
    String     transactionId,          // = the INBOUND transactionRef the publisher supplied
    ReservationTypeEnum reservationType,   // SINGLE | BATCH
    BigDecimal amount,                  // total held
    ReservationStatusEnum status,       // ACTIVE | PARTIALLY_RESOLVED | CAPTURED | RELEASED | EXPIRED
    String     disbursementBatchId) {}  // nullable; set for BATCH
// ONE record backs both topics; event_type + status disambiguate.
// event_type ∈ { ledger.reservation.created, ledger.reservation.released }, source="ledger-service", v="1.0"
// metadata.correlation_id = fresh random UUID; idempotency_key = "{reservationId}:created" | "{reservationId}:{ref}"
```

Idea `015_reservation_created.md` asks to: track reservations **and their states** (created →
released/expired) in a table, **factor in batch reservations**, and **toast in the settlement
and disbursement flows** when reservations are created and released.

Four facts from the contract shape the design:

1. **`transaction_id` is the publisher's inbound id** (the `transactionRef` the reservation
   was created with) — *not* the ledger recording UUID. Per flow it is: disbursement
   `transaction_id`, settlement `settlement_request_id`, batch `batch_id` — **exactly the
   request-id fields the chaos catalog autogenerates** (cf. ADR-025). So reservations are
   **precisely correlatable to a chaos publish** by a key the client already knows — the
   toasts can be request-id-scoped and exact (like Part 1's failures, *not* heuristic like
   Part 2's balances).

2. **Batch = one aggregate reservation** (`reservationType=BATCH`, `disbursement_batch_id`
   set, `amount`=batch total, `transaction_id`=`batch_id`) that emits **one** `created` and
   then **multiple** `released` events as items resolve (`PARTIALLY_RESOLVED` → `CAPTURED` /
   `RELEASED`). So a reservation is a **multi-transition lifecycle**, not a one-shot release.

3. **Settlement also creates a ledger reservation** (verified: `settlement.initiated` →
   `BalanceReservationService.create()`) even though the chaos settlement *events* carry no
   `reservation_id` on the wire (ADR-018) — so a `reservation.created` fires for settlement
   too, and the "settlement + disbursement" toast scope is valid.

4. Events are **keyed by `reservation_id`** (ordered per reservation); the payload has **no
   currency** and **no `expires_at`**; the globally unique dedupe key is the envelope
   `event_id`.

### Why a projection when ADR-018/023 already read reservations live?

The existing read-proxy (`GET /ledger/accounts/{id}/reservations`, `ReservationResponse`) is a
*pull* that, notably, returns **richer** fields than the Kafka event — `amountCaptured`,
`amountReleased`, `expiresAt`, `resolvedAt`. It answers "what is this reservation right now"
on demand. It cannot *push* "a reservation was just created/released" — which is exactly what
a toast needs and what a state-tracking table is. The two are complementary, the same way
Part 2's `balance_history` complements Phase 015's live balance read-proxy. **Decision
(confirmed with product): keep the read-proxy for `reservation_id` sourcing and rich
on-demand detail; add the projection for push tracking + toasts. Additive, not superseding.**

## Decision

**Consume both `ledger.reservation.created` and `ledger.reservation.released` on the ADR-024
factory into a single stateful `reservation` table — one row per `reservation_id`, upserted
as events arrive — and surface it via a per-VA query + a per-VA Reservations tab + run-page
toasts scoped by the flow's request id.**

```
reservation
  reservation_id          TEXT PRIMARY KEY     -- natural key; one row per reservation
  account_id              TEXT NOT NULL         -- = virtual_account.va_id
  transaction_id          TEXT NOT NULL         -- inbound transactionRef = chaos request id (correlation key)
  reservation_type        TEXT NOT NULL         -- SINGLE | BATCH
  disbursement_batch_id   TEXT                   -- nullable; = batch_id for BATCH
  amount                  TEXT NOT NULL          -- BigDecimal as string (total held)
  currency                TEXT                   -- NOT in event; best-effort from VA registry
  status                  TEXT NOT NULL          -- ACTIVE | PARTIALLY_RESOLVED | CAPTURED | RELEASED | EXPIRED
  created_event_id        TEXT                   -- envelope event_id of the created event
  last_event_id           TEXT NOT NULL          -- latest applied envelope event_id (dedupe)
  release_event_count     INTEGER NOT NULL DEFAULT 0
  tenant_id               TEXT
  created_at              TEXT                   -- envelope timestamp of the ACTIVE (created) event
  updated_at              TEXT NOT NULL          -- envelope timestamp of the latest applied event
  terminal_at             TEXT                   -- when status first became terminal
  payload_json            TEXT                   -- latest envelope
  -- indexes: transaction_id; account_id; disbursement_batch_id; status
```

State machine for the upsert (events are keyed by `reservation_id` → ordered per reservation;
the guards handle only redelivery / rare reorder):

- **`created`** (status `ACTIVE`): insert the row, or — if a `released` arrived first
  (reorder) — fill the base fields (`amount`, `account_id`, `reservation_type`,
  `disbursement_batch_id`, `created_at`, `created_event_id`) **without regressing** `status`.
- **`released`** (status `PARTIALLY_RESOLVED` | `CAPTURED` | `RELEASED` | `EXPIRED`): advance
  `status` by a fixed ordinal (`ACTIVE`<`PARTIALLY_RESOLVED`<terminal); never regress a
  terminal status; `release_event_count++`; set `terminal_at` on first terminal.
- **Dedupe:** skip when the incoming envelope `event_id == last_event_id` (immediate
  redelivery). Always update `last_event_id`/`updated_at` when an event is applied.

Other decisions:

- **One listener subscribed to both topics**, dispatching by `status` (and topic header) —
  same mirror record, minimal code (rides ADR-024's single factory; DLT derived per topic).
- **BigDecimal `amount` stored as TEXT** (the Part 2 `BigDecimalStringConverter`), currency
  **backfilled best-effort** from the VA registry (`account_id` = `va_id`), `null` if absent.
- **Event-faithful only.** `amountCaptured` / `amountReleased` / `expiresAt` are **not**
  stored — they aren't in the event; the ADR-018 read-proxy remains the source for those.
- **Query API:** per-VA `GET /api/v0/virtual-accounts/{vaId}/reservations` (for the tab) +
  flat/batch `GET /api/v0/reservations?transactionRef=…|batchId=…|accountId=…` (for the toast
  watch and tracking). Distinct from the read-proxy's `/ledger/accounts/{id}/reservations`.
- **Toasts (interactive flows only):** after publishing a reservation-creating *initiated*
  event (disbursement.initiated, settlement.initiated, batch reservation request), poll the
  projection scoped by that flow's request id (`transactionRef` = the value the client
  generated) within a bounded window; toast on **created** ("Reservation created: {amount}
  held") and on **released/expired/captured** state changes. Reuses the Part 1 `sonner`
  toaster + bounded-poll pattern ([ADR-026](026-run-page-failure-surfacing-via-bounded-polling.md)).
  Batch dedupes/caps the multiple release toasts. The RANDOM unattended runner is server-side
  and is **not** toasted.

## Consequences

**Positive**
- Delivers the idea: a stateful, batch-aware reservation tracking table, a per-VA Reservations
  tab, and create/release toasts — a listener + mirror record + one migration on the
  already-generalized consumer.
- **Precise, request-id-scoped toasts** (the event carries the publisher's `transactionRef`),
  unlike Part 2's heuristic balance toast — no false attribution.
- Push tracking of reservation state (incl. batch partial resolutions) the pull read-proxy
  can't give, while the read-proxy keeps serving `reservation_id` sourcing + rich on-demand
  detail. Clean complement, no rework of the Phase 014/016 wizards.

**Negative / trade-offs**
- **Two reservation mechanisms coexist** — the ADR-018/023 read-proxy poll (sourcing/detail)
  and this projection (tracking/toasts). Mild duplication (both can poll for the same
  reservation by `transactionRef`); accepted to avoid reworking the wizards. A future cleanup
  could unify the wizard onto the projection (and retire the read-proxy poll), which would
  supersede ADR-018/023 — explicitly out of scope here.
- **Event-faithful fields only** — no captured/released amounts or expiry in the projection
  (not in the event); operators needing those use the read-proxy / Phase 016 batch summary.
- **Batch partial-resolution tracking is best-effort** — the event has no per-event remaining
  amount, so the table tracks `status` + a `release_event_count`, not an exact running hold.
  Authoritative per-item progress stays in the Phase 016 batch summary read-proxy.
- **Eventually-consistent mirror** (at-least-once, possible lag, the ledger's
  `last_entry_sequence`-style timing caveats); the ledger remains the source of truth.
- **Toast latency/window** — a release landing after the poll window won't toast (still on the
  Reservations tab); batch fan-out is deduped/capped to avoid spam.
- Stores reservation data the chaos machine previously only read — justified as an
  observational lifecycle log, the same rationale as Parts 1–2.
