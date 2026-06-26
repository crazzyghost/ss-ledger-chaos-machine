# ADR 018 — Sourcing `reservation_id` via a ledger reservations read-proxy poll

## Status
Accepted — 2026-06-25 — Phase [014 — Collection, Settlement & Disbursement Flows](../phases/014-collection-settlement-disbursement-flows/DESIGN.md)

## Context

`disbursement.completed` and `disbursement.failed` both carry a **required**
`reservation_id`, but `disbursement.initiated` does **not**. The chaos machine is a
*driver*: it publishes `disbursement.initiated`, and the **ledger** is what creates
the reservation. So the wizard has to come up with a `reservation_id` for the second
event that it never minted.

What the ledger actually does (verified in `ss-ledger-service`):

1. On `disbursement.initiated`, `DisbursementStrategy.buildReservationRequest()` calls
   `BalanceReservationService.create()`, which **generates `reservation_id` =
   `UUID.randomUUID()`** and stores the reservation with `transactionRef =
   transaction_id`.
2. The ledger then publishes `ledger.reservation.created`
   (`{ reservation_id, transactionId, … }`).
3. On `disbursement.completed`/`failed`, the ledger finds the recording **by
   `transaction_id`**, reads `recording.getReservationId()`, and
   **ignores the inbound `reservation_id`** — a mismatch is silently overridden by the
   stored value.

So the inbound `reservation_id` is **structurally required but functionally cosmetic**:
the lifecycle linkage that matters is `transaction_id`, which the driver controls.
There is also a **read endpoint** on the ledger:
`GET /api/v0/accounts/{accountId}/reservations`. It returns the account's full
reservation list; **server-side `transactionRef` filtering is not implemented yet**, so
the caller fetches the list and filters by `transactionRef` (a UUID) itself — while still
**passing the `transactionRef` query param** so the call becomes O(1) at the ledger the
moment filtering lands (no client change needed then).

The current manual workflow (`bin/complete-disbursement.sh` / `fail-disbursement.sh`)
captures the real reservation id out-of-band into `conf.sh`
(`CHAOS_MACHINE_CURRENT_RESERVATION_ID`) and passes it through. We want the UI to do
better than copy-paste, without over-building.

Options considered:

- **(a) Autogen placeholder UUID.** Mint a throwaway `reservation_id`. Works (ledger
  ignores it) but the operator never sees the real reservation, reducing observability.
- **(b) Consume `ledger.reservation.created`.** A new Kafka consumer + projection
  table (à la Phase 009). True-to-production, but heavy: a new inbound topic, a store,
  and a wizard that waits on async arrival.
- **(c) Poll the reservations read endpoint.** After publishing `initiated`, read
  `GET …/accounts/{orgVaId}/reservations?transactionRef={transaction_id}` until the
  reservation appears or a timeout elapses; on timeout the operator enters it manually.

## Decision

**Adopt (c): poll the ledger's reservations read endpoint through a thin read-proxy,
with a timeout and a manual-entry fallback.** Reject the Kafka consumer (b) as
over-built for a cosmetic-but-observable field, and reject the bare placeholder (a) as
the *default* because the operator value is seeing the real reservation. The placeholder
remains the **fallback** when the poll times out in the unattended path.

### Read-proxy (reuse the Phase 012 machinery)

Add a read-through proxy in `com.softspark.chaos.ledgerproxy`, mirroring the
trial-balance proxy ([ADR-015](015-trial-balance-via-ledger-read-proxy.md)):

```
GET /api/v0/ledger/accounts/{accountId}/reservations?transactionRef={ref}
  → LedgerReadController → LedgerClient
  → ledger GET /api/v0/accounts/{accountId}/reservations?transactionRef={ref}
       (param passed through for forward-compat; today the ledger ignores it and returns
        the full list, so LedgerClient filters the result by transactionRef in-process)
```

No new tables, no Kafka, no persistence — the existing `RestClient` read-through
(timeouts + retries + circuit breaker) applies. The `transactionRef` filter lives in
`LedgerClient` so it collapses to a no-op pass-through the day the ledger filters
server-side. A small `ReservationLookup` service wraps "poll until present or timeout"
so **both** consumers can reuse it:

- **Interactive disbursement wizard (frontend):** after step 1, poll the proxy
  (`accountId` = the org VA picked on the initiated form, `transactionRef` =
  `transaction_id`) on an interval up to a configured timeout; prefill the real
  `reservation_id` into the step-2 form when it arrives. On timeout, surface a
  manual-entry field (operator pastes the id observed from the ledger).
- **RANDOM unattended runner (backend):** call `ReservationLookup` server-side between
  the initiated and completed/failed publishes, with the same timeout; on timeout fall
  back to an **autogen placeholder** (the ledger ignores it, so the run still
  succeeds) and record that the placeholder was used.

Settlement needs none of this — its events carry no `reservation_id`.

## Consequences

**Positive**
- Real reservation id with full operator visibility, via the proxy pattern already in
  the codebase (Phase 004/012) — no new inbound Kafka surface, store, or migration.
- One `ReservationLookup` service serves both the interactive and unattended paths.
- Graceful degradation: a slow/again-unreachable ledger never hard-blocks — the
  operator types it (interactive) or a placeholder is used (unattended), because the
  field is ledger-ignored.

**Negative / trade-offs**
- A poll loop with a timeout is a small piece of stateful UX (and server logic) to get
  right; covered by tests for the found/timeout/manual paths.
- The ledger's `GET /api/v0/accounts/{accountId}/reservations` does **not** yet filter by
  `transactionRef` server-side, so `LedgerClient` fetches the full list and filters in
  memory (the `transactionRef` UUID is still sent so it becomes O(1) at the ledger once
  supported). For a hot account with many reservations this is a small per-poll scan —
  acceptable, and removed for free when server-side filtering lands. Confirm the exact
  path and response shape against the ledger before implementing.
- The unattended placeholder fallback means a RANDOM run can publish a
  `reservation_id` that never matches a real reservation. Acceptable: the ledger keys
  off `transaction_id`, and the run records the fallback for transparency.
