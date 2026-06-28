# Phase 19 - Reservation Lifecycle Tracking

## Summary
Adds the chaos machine's **fourth inbound consumer** (after `ledger.account.created`,
`ledger.transaction.failed`, `ledger.balance.updated`): it consumes **both**
`ledger.reservation.created` and `ledger.reservation.released` into a single stateful
**`reservation`** table that tracks each reservation and its lifecycle
(`ACTIVE` → `PARTIALLY_RESOLVED` → `CAPTURED` | `RELEASED` | `EXPIRED`), **including batch
reservations**. A per-VA query feeds a new **Reservations tab** on the VA detail page, and —
reusing the Part 1 toaster — the **settlement and disbursement flows raise toasts when a
reservation is created and when it is released/expired/captured**. This is **Part 3 of the
four-part series** ("testing ledger Kafka events"; idea `015_reservation_created.md`), riding
the Part 1 generalized consumer ([ADR-024](../../decisions/024-multi-event-ledger-outbound-consumer.md)).
See [ADR-028](../../decisions/028-reservation-lifecycle-projection.md).

## Motivation
Disbursement and settlement initiations (and batch reservation requests) cause the ledger to
**hold funds in a reservation**, later captured (success) or released/expired (failure/lapse).
A resilience harness wants to *watch these holds appear and resolve* — especially during chaos
runs ("did my disbursement's reservation get created? did the failure release it? did the batch
reservation fully capture?"). Today the chaos machine only **pulls** a reservation's id
on-demand for the wizard form (ADR-018/023); it has no record of reservation *state over time*
and no live signal when one is created or released. This phase adds that tracking and the
push signal.

## User-Facing Changes
- **VA detail page:** a new **Reservations** tab (alongside Overview, Transactions, Balance)
  listing the account's reservations and current states (type, amount, status, batch linkage,
  timestamps), with a status filter, paginated.
- **Settlement & disbursement flows:** after publishing an *initiated* event, an **info toast**
  fires when the reservation is **created** ("Reservation created — {amount} held") and when it
  is **released / expired / captured**. Scope is **precise** (the event carries the publisher's
  request id), so the toast is an exact statement about *this* flow's reservation.
- **New API:** `GET /api/v0/virtual-accounts/{vaId}/reservations` + a flat/batch
  `GET /api/v0/reservations?transactionRef=…|batchId=…|accountId=…|status=…`.
- **New operational surface:** consumer lag / DLT for the two reservation topics.
- The existing wizard reservation_id sourcing (ADR-018/023 read-proxy poll) is **unchanged**.

## Architecture Impact
Fourth inbound consumer on the **already-generalized** ADR-024 factory — a new
`com.softspark.chaos.reservation` feature package (one listener on **both** reservation topics
+ a shared mirror record + a stateful `reservation` entity/repo/service + query controller/dto
with nested **and** flat/batch endpoints), one additive Flyway migration (`V15`), and on the
frontend a Reservations tab + a reservation toast watch reusing the Part 1 `sonner` toaster.
It **complements, not supersedes**, ADR-018/023: the wizards keep sourcing `reservation_id`
via the ledger read-proxy (which also exposes richer fields the event omits — captured/released
amounts, expiry); this projection adds push tracking + toasts
([ADR-028](../../decisions/028-reservation-lifecycle-projection.md)).

```mermaid
flowchart LR
  subgraph chaos[ss-ledger-chaos-machine]
    cons["LedgerReservationConsumer<br/>(created + released topics)"]
    va[("virtual_account<br/>(currency backfill)")]
    rv[("reservation<br/>(stateful projection)")]
    api["GET /reservations · /virtual-accounts/{vaId}/reservations"]
    tab["VA detail · Reservations tab"]
    toast["wizard toast watch<br/>(by transactionRef / batch_id)"]
    cons -->|lookup currency by va_id| va
    cons -->|upsert by reservation_id (monotonic status)| rv
    api --> rv
    tab --> api
    toast --> api
  end
  ledger["ss-ledger-service<br/>(reservation create / capture / release / expiry)"]
  ledger -->|"ledger.reservation.created (ACTIVE)"| K{{Kafka}}
  ledger -->|"ledger.reservation.released (PARTIALLY_RESOLVED|CAPTURED|RELEASED|EXPIRED)"| K
  K -->|consume method-typed| cons
  ledger -. poison .-> dlt{{ledger.reservation.*.dlt}}
```

**Contract notes (verified against `ss-ledger-service`).** One record
`ReservationLifecycleEventData(reservationId, accountId, transactionId, reservationType[SINGLE|
BATCH], amount, status, disbursementBatchId)` backs both topics; `event_type` + `status`
disambiguate. Decisive properties:
- **`transaction_id` = the inbound `transactionRef` = the chaos request id** (disbursement
  `transaction_id`, settlement `settlement_request_id`, batch `batch_id`) → precise,
  request-id-scoped correlation (unlike Part 2's heuristic balance toast).
- **Batch = one aggregate reservation** that emits **one created + multiple released** events
  (partial resolutions) → a multi-transition lifecycle.
- **Settlement also creates a reservation** internally (even though settlement chaos events
  carry no `reservation_id`) → its create toast is valid.
- Events **keyed by `reservation_id`** (ordered per reservation); **no currency / no expiry** in
  the payload; dedupe by envelope `event_id`; `metadata.correlation_id` is random.

## Edge Cases
- **At-least-once redelivery / reorder** → upsert keyed by `reservation_id` with a
  **monotonic-status** guard (never regress; terminal sticky) + `event_id` dedupe; a `created`
  arriving after a `released` fills base fields without regressing status.
- **Batch partial resolutions** → multiple `released` events accumulate (`PARTIALLY_RESOLVED` …
  → terminal); tracked via `status` + `release_event_count` (best-effort; exact per-item
  amounts stay in the Phase 016 batch summary read-proxy, since the event omits them).
- **Currency null** (VA not yet projected) → stored null; UI falls back to the VA's currency.
- **No expiry / no captured-released amounts** in the event → not stored; available via the
  ADR-018 read-proxy.
- **Malformed/poison** → `ledger.reservation.{created,released}.dlt`; null/partial envelope →
  logged + skipped (no row, no DLT).
- **Toast window/late transition** → a release after the window isn't toasted (visible on the
  Reservations tab); batch fan-out toasts are deduped/capped.
- **Settlement reservation** → create toast armed by `settlement_request_id`; times out silently
  if no reservation appears.
- **Two reservation mechanisms coexist** (read-proxy sourcing + projection) → intentional and
  additive; the sourcing poll is untouched.

## Testing Strategy
- **Unit:** envelope→entity mapping; monotonic-status state machine (advance / no-regress /
  terminal-sticky); `event_id` dedupe; created-after-released reorder; batch multi-release
  accumulation; BigDecimal round-trip; currency backfill; null-data skip; query-service filter
  dispatch.
- **Integration (Testcontainers Kafka):** created → ACTIVE; created+released → terminal; batch
  created + N releases → terminal + count; redelivery → no-op; poison → the reservation DLTs.
- **Slice (`@WebMvcTest`/`@DataJpaTest`):** nested + flat endpoints (`transactionRef`/`batchId`/
  multi-`accountId`/`status`), ordering, paging/clamp, 404, AUTH.
- **Frontend (Vitest + Testing Library + MSW):** Reservations tab (rows, badges, status filter,
  paging, batch vs single, empty/error); toast watch (create + release/expire/capture toasts,
  batch dedupe/cap, settlement create, window stop, coexists with sourcing poll).
- **Contract:** mirror record round-trips the ledger's exact JSON for both events.
- Consolidated into [Phase 006](../006-testing-and-verification/DESIGN.md).

## Deployment Strategy
- One additive Flyway migration (`reservation`), numbered **`V15`** assuming Parts 1–2's
  `V12`–`V14` land first (build order 017 → 018 → 019); otherwise the next free version. No
  backfill.
- Consumer gated by `chaos.kafka.consumer.enabled`; topics + group id configurable; DLTs
  derived. Backend and frontend ship independently and additively; the ADR-018/023 read-proxy
  sourcing is untouched.

## Tasks
- [001 - Reservation lifecycle consumer + `reservation` projection](./001-reservation-lifecycle-consumer-projection.md) — one listener on both topics, shared mirror record, stateful entity/repo/service, Flyway `V15`, monotonic-status idempotent upsert, currency backfill. *(ADR-028)*
- [002 - Reservation query API](./002-reservation-query-api.md) — per-VA `GET /api/v0/virtual-accounts/{vaId}/reservations` + flat/batch `GET /api/v0/reservations` (by transactionRef / batchId / accountId / status) + `/{id}`. *(ADR-028)*
- [003 - Reservation create/release toasts in the flows](./003-reservation-toasts-in-flows.md) — bounded poll scoped by the flow's request id; info toast on create + on release/expire/capture; reuses the Part 1 toaster; settlement + disbursement + batch. *(ADR-028)*
- [004 - VA detail: Reservations tab](./004-va-detail-reservations-tab.md) — new tab listing the account's reservations and states (type, amount, status, batch, timestamps) with a status filter. *(ADR-028)*

## Parallel Tasks
- **001** is the foundation and blocks **002** (table) and ultimately **003**/**004**.
- **002** depends on 001; **003** (flat endpoint + toaster) and **004** (nested endpoint) both
  depend on 002 and are independent of each other — both buildable against MSW fixtures in
  parallel, wired live once 002 lands.
- Cross-phase: **001 depends on Phase 017 Task 001** (the ADR-024 generalized consumer factory);
  **003 depends on Phase 017 Task 005** (the `sonner` toaster + watch-hook pattern). If Phase 017
  hasn't landed, pull those in first.

Recommended order: **(Phase 017 consumer generalization + toaster) → 001 → 002 → (003 ‖ 004)**.

Part 4 (`014_dlt_views` → the `.dlt` dead-letter topics) is the remaining series member and
follows the same shape on the same factory, reading the `<topic>.dlt` streams this and the prior
parts already route poison records to.
