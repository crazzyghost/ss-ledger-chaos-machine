# ADR 027 — Event-sourced `balance_history` projection from `ledger.balance.updated`

## Status
Accepted — 2026-06-27 — Phase [018 — Balance History](../phases/018-balance-history/DESIGN.md)

Builds on [ADR-024](024-multi-event-ledger-outbound-consumer.md) (the multi-event consumer
this projection rides on). **Complements — does not supersede —**
[ADR-020](020-as-of-balance-via-ledger-read-proxy.md) /
[ADR-021](021-batch-balance-read-proxy-for-list-column.md): live current/point-in-time
balance stays a transparent ledger read-through; this ADR adds a *stored historical log* of
balance-mutation events. **Part 2 of 4** ("testing ledger Kafka events"; Part 1 =
[ADR-025](025-transaction-failure-projection-and-request-id-correlation.md)).

## Context

The ledger emits `ledger.balance.updated` as a side effect whenever an account's balance
projection mutates. Verified against `ss-ledger-service`:

```java
// account/events/v1/AccountBalanceUpdatedEventData.java   (snake_case, addTypeInfo(false))
public record AccountBalanceUpdatedEventData(
    UUID       accountId,
    BigDecimal availableBalance,
    BigDecimal pendingBalance,
    BigDecimal reservedBalance,
    BigDecimal totalBalance,
    BigDecimal totalDebits,
    BigDecimal totalCredits,
    long       lastEntrySequence,
    LocalDateTime balanceAsOf) {}
// EventEnvelope<…>, event_type="ledger.balance.updated", source="ledger-service", version="1.0"
// metadata.correlation_id = fresh random UUID (NOT transaction-linked)
// metadata.idempotency_key = "{journalEntryId}:{accountId}" | "{reservationId}:{ref}" | "{reservationId}:created"
```

Idea `013_balance_history.md` asks to: store these events in a `balance_history` table,
expose an endpoint for a VA's balance history, and add a **Balance tab** to the VA detail
page. Three properties of the contract decide the design:

1. **One event per affected account.** A single transaction touching source + destination +
   fee accounts produces *multiple* `ledger.balance.updated` events — one per account — each
   carrying that account's **post-mutation snapshot**. Reservation RELEASE/EXPIRY also emit
   one (CAPTURE is balance-neutral and skipped). So per account this is naturally a *stream
   of discrete update events* — exactly a history.

2. **No transaction linkage in the payload.** There is **no** `transaction_id` /
   `transaction_request_id` / `entry_id` field, and `metadata.correlation_id` is a fresh
   random UUID. The only causal hint is `metadata.idempotency_key`
   (`{journalEntryId}:{accountId}` etc.) — a *journal/reservation* id, **not** the
   `transaction_request_id` the chaos machine controls. **Therefore a balance update cannot
   be cleanly keyed back to a specific chaos publish** the way a `transaction.failed` can
   (Part 1). This corrects the optimistic note in Part 1 that called `ledger.balance.updated`
   a "definitive per-transaction success signal": it confirms *an account's* balance moved
   (and the post-state), but does not name the causing transaction.

3. **No currency** in the event; ordering is a per-account monotonic `last_entry_sequence`
   (may be `0` in edge cases per the ledger's own JPA-timing caveat); the globally unique
   key per emission is the envelope `event_id`.

### Why store it, when Phase 015 already reads balances live?

Phase 015 reads **current / point-in-time** balance on demand and stores nothing
(ADR-020/021). That answers "what is the balance now / as of T". It cannot answer "show me
the **stream of balance-mutation events** this account emitted during my chaos run" — that
is a log of discrete side effects (each with its own sequence and journal/reservation
linkage), which is precisely what a resilience harness wants to *observe*. Reconstructing it
from the read-proxy would mean hammering the ledger; the ledger's PIT endpoint gives a
snapshot at a time, not the event stream. Storing the events also keeps the history visible
for past runs and is the same projection pattern already used for VAs (Phase 009) and
failures (Part 1).

## Decision

**Consume `ledger.balance.updated` on the ADR-024 factory and project each event into a
`balance_history` table — a per-account, append-only, event-sourced log.** Correlate to a
VA by `account_id` (= chaos `va_id`, per Phase 009); do **not** attempt transaction-level
correlation (the contract can't support it).

```
balance_history
  id                     TEXT PK              -- Ids.generate()
  event_id               TEXT UNIQUE NOT NULL -- envelope event_id → idempotency
  account_id             TEXT NOT NULL        -- = virtual_account.va_id (indexed)
  available_balance      TEXT NOT NULL        -- BigDecimal as string (precision-safe)
  pending_balance        TEXT NOT NULL
  reserved_balance       TEXT NOT NULL
  total_balance          TEXT NOT NULL
  total_debits           TEXT
  total_credits          TEXT
  last_entry_sequence    INTEGER NOT NULL     -- per-account monotonic (may be 0)
  balance_as_of          TEXT NOT NULL        -- LocalDateTime (zoneless ISO local)
  currency               TEXT                 -- NOT in event; best-effort from VA registry
  idempotency_key        TEXT                 -- metadata.idempotency_key (journal/reservation linkage)
  ledger_correlation_id  TEXT                 -- metadata.correlation_id (random; stored for completeness)
  tenant_id              TEXT
  occurred_at            TEXT NOT NULL        -- envelope timestamp (Instant)
  received_at            TEXT NOT NULL        -- consume time
  payload_json           TEXT                 -- raw envelope (detail view)
  -- indexes: account_id; (account_id, last_entry_sequence); (account_id, occurred_at)
```

Decisions baked in:

- **Idempotency by envelope `event_id`** (UNIQUE + upsert/no-op-on-conflict). At-least-once
  redelivery yields one row. (Two *distinct* events for one account in one transaction —
  e.g. funding entry + reservation release — are **legitimately separate** rows with distinct
  `event_id`s; they are not duplicates.)
- **BigDecimal stored as TEXT** via a `BigDecimal`↔`String` JPA converter (mirrors the
  project's `InstantStringConverter` approach), avoiding SQLite NUMERIC precision/dialect
  surprises. Phase 015 keeps balances as `BigDecimal`/`number` on the wire; we preserve the
  exact decimal string on disk.
- **Currency backfilled best-effort** from the `virtual_account` projection at consume time
  (lookup by `account_id` = `va_id`); null if the VA isn't projected yet. The Balance tab
  lives on the VA detail page and already knows the VA's currency, so a null here is cosmetic.
- **Ordering for display:** newest first by `(occurred_at DESC, last_entry_sequence DESC)` —
  robust even when `last_entry_sequence` is `0`.
- **Offset pagination** (`PageResponse<T>`), matching `/history` (a local table we own), not
  the cursor pagination the ledger-proxied per-account transactions tab uses.

### Run-page balance-update toast (ephemeral, heuristic scope)

The Single Flow Run page should toast when a balance moves on an account the operator just
published to — the *effect* counterpart to Part 1's failure toast
([ADR-026](026-run-page-failure-surfacing-via-bounded-polling.md)), reusing the same `sonner`
toaster and bounded-poll pattern. But `ledger.balance.updated` carries **no
`transaction_request_id`**, so this watch **cannot** be scoped by the request id the way the
failure watch is. It is scoped **heuristically**:

- **By involved account id(s)** — the source / destination / fee VAs resolved from the flow's
  slots, which the client already knows at publish time (no server round-trip to discover them).
- **By a time watermark** — only balance updates with `occurred_at >=` the publish instant
  (minus a small clock-skew slack) count; the client dedupes toasts by balance-history
  `event_id`.

This is deliberately **not** the persisted heuristic correlation rejected below. The
distinction is stakes and lifetime: a *stored* transaction↔balance link would be a durable,
queryable claim that is fuzzy and could mislead later analysis, so it is not built. A
*transient* run-page toast is an ephemeral effect notification fired in the exact moment the
operator acted on those accounts — the attribution is contextually sound and disappears. The
honest framing in the copy is **"balance updated on {account}"**, never "your transaction
succeeded" or "caused by your transaction" (a transfer fans out one event per account, and
unrelated concurrent activity on the same account is possible). It signals *an account you
touched just moved*, which is what the operator wants to see — not a per-transaction ack.

Mechanism: after a successful publish, poll a **batch balance-history lookup**
(`GET /api/v0/balance-history?accountId=…&from={publishInstant}`, one call covering all
involved accounts) on the ADR-026 interval/window; toast (info/success) per involved account
that registers a fresh update, deduped by `event_id` and capped. Only flows that resolve to
ledger accounts arm it (non-transactional flows move no balances).

## Consequences

**Positive**
- Delivers exactly what the idea asks — a stored, queryable, per-account balance-mutation
  history and a Balance tab — using the established projection pattern (Phase 009 / Part 1)
  on the already-generalized consumer (ADR-024): a listener + mirror record + one migration.
- Lets the harness *observe the stream of balance side effects* of a chaos run without
  per-row ledger calls; survives past runs and ledger pruning.
- Gives the operator a live **effect signal** on the run page (balance moved on an account you
  just touched), complementing Part 1's failure toast and reusing the same toaster + bounded
  poll — without inventing a per-transaction link the contract can't support.
- Cleanly separated from Phase 015: live/PIT balance stays an authoritative read-through;
  this is an observational log. No conflict, no superseding.

**Negative / trade-offs**
- **No transaction-level correlation** (contract-forced). The Balance tab shows *what an
  account's balance did*, not *which published transaction caused each change*. Documented as
  an explicit limitation; cross-linking to publishes by `transaction_request_id` is **not**
  possible from this event (unlike Part 1's failures). A heuristic join **persisted** into the
  history (same account, time window, rising sequence) is deliberately **not** built — a stored
  wrong attribution would be fuzzy and misleading. The **ephemeral run-page toast** above does
  use an account+time heuristic, but only transiently and with honest "balance updated on
  {account}" copy — it makes no stored claim about causation.
- **The run-page balance toast can over- or under-fire.** It may toast for an unrelated
  concurrent update on an involved account, or (for a transfer) fire several per-account
  toasts; and a balance update landing after the poll window closes won't toast (still visible
  on the Balance tab). Mitigated by event-id dedupe, a per-account cap, and the honest copy;
  acceptable for a single-operator harness.
- **Stored balances are an eventually-consistent mirror**, not a source of truth — at
  least-once delivery, possible consumer lag, and the ledger's `last_entry_sequence == 0`
  edge case. The live Phase 015 panel remains the authority for "current balance"; the tab is
  framed as an event log, not a balance oracle.
- **Currency is derived, not authoritative** (absent from the event). Best-effort backfill;
  the UI falls back to the VA's currency.
- **Unbounded growth** (one row per account per posting) for a busy chaos run; acceptable for
  a test harness. A retention/prune job is noted as out of scope.
- **Storing balance data at all** is a reversal of Phase 015's "chaos stores nothing about
  balances" stance — justified because a *historical event log* is a different artifact from
  *on-demand current balance*, and the idea explicitly requires persistence.
