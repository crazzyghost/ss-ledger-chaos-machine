# ADR 025 — Project `ledger.transaction.failed` into a `transaction_failure` table, correlated to publishes by `transaction_request_id`

## Status
Accepted — 2026-06-27 — Phase [017 — Ledger Transaction-Failure Events & Correlation](../phases/017-ledger-transaction-failure-events/DESIGN.md)

Builds on [ADR-024](024-multi-event-ledger-outbound-consumer.md) (the multi-event
consumer this projection rides on).

## Context

The ledger emits `ledger.transaction.failed` whenever a transaction recording transitions
to `FAILED` (and the transition is not an idempotent no-op). Verified against
`ss-ledger-service`:

```java
// transaction/recording/outbound/TransactionFailedEventData.java  (snake_case)
public record TransactionFailedEventData(
    UUID   transactionId,          // the ledger's own recording id  (data.transaction_id)
    String transactionRequestId,   // the id the PUBLISHER supplied   (data.transaction_request_id)
    String transactionType,        // COLLECTION | DISBURSEMENT | SETTLEMENT | TRANSFER | …
    String failureCode,
    String failureReason) {}
// envelope: EventEnvelope<TransactionFailedEventData>, source="ledger-service", version="1.0"
// metadata: correlation_id = recording id (NOT the chaos correlation id),
//           idempotency_key = "{transaction_request_id}:failed", tenant_id
```

The chaos machine needs to (1) **store** these failures and (2) **correlate** each one
back to the publish that triggered it, for two surfaces: a run-page failure toast and an
outcome badge on the "Sent" history tab.

**The correlation key is `transaction_request_id` — and only it.** Two facts pin this:

1. **The failure's `metadata.correlation_id` is the ledger's recording id, not the chaos
   machine's outbound `correlation_id`.** The ledger does not echo the publisher's
   correlation id, so correlating by envelope correlation id is impossible.
2. **On the ledger, `transactionRequestId` is a *payload* field of the inbound event** —
   `unique, nullable=false`, the "canonical aggregate identity key … [that] drives the
   idempotency contract" (`TransactionRecording`). Its source field per inbound event
   (verified in the ledger handlers):

   | Inbound event the chaos machine publishes | Ledger reads `transactionRequestId` from | Chaos catalog field (Phase 011/014) |
   |---|---|---|
   | `collection.completed` | `transaction_id` | `transaction_id` |
   | `disbursement.{initiated,completed,failed}` | `transaction_id` | `transaction_id` |
   | `organization.va.settlement.{initiated,completed,failed}` | `settlement_request_id` | `settlement_request_id` |
   | `organization.transfer.requested` | `transfer_request_id` | `transfer_request_id` |
   | `organization.topup.confirmed` | `topup_request_id` | `topup_request_id` |
   | `organization.treasury.{prefund,sweep,transfer}.completed` | `{prefund,sweep,transfer}_request_id` | same |
   | `disbursement.batch.initiated` | `batch_id` | `batch_id` |
   | `disbursement.batch.item.{completed,failed}` | `item_id` | `item_id` |

   These are **exactly** the fields the chaos catalog autogenerates client-side
   (`crypto.randomUUID()` per Phase 011, ADR-014). **So the chaos machine already knows,
   at publish time, the precise string the ledger will store as `transactionRequestId`** —
   it is the value of the flow's designated request-id field. No guessing, no echo
   required.

   > **Naming trap (document loudly):** for collection/disbursement the chaos *payload
   > field is named* `transaction_id`, but the ledger files it under
   > `transactionRequestId`. In the failure event, `data.transaction_id` is the ledger's
   > **recording UUID** (a *different* value), and `data.transaction_request_id` is the
   > chaos-supplied id. Correlation matches the latter.

Two sub-decisions follow: **where to store failures**, and **how to make the publish
side carry the key**.

### Where to store — new table vs. extend `publish_record`

`publish_record` (Phase 003) is an **append-only, write-at-publish** log of *outbound*
events; it has only `created_at` (no `updated_at`) and a `PUBLISHED|FAILED` status that
means "did the **publish** to Kafka succeed", not "did the ledger accept the
transaction". A `transaction.failed` is an **inbound** event that arrives *asynchronously,
later*, with a different field set (`failure_code`, `failure_reason`, the ledger recording
id). Folding it into `publish_record` would overload one row with two lifecycles and turn
an immutable log into a mutable one.

### How the publish side carries the key

`publish_record` has **no `transaction_request_id` column** today — the request id lives
only inside the serialized `payload_json`, under a per-flow field name. And the request-id
field is not currently *labelled* in the catalog (it is just "an autogen UUID
descriptor"), so nothing knows *which* field is the canonical request id for a given flow.

## Decision

**(1) A dedicated `transaction_failure` projection table**, populated by a
`LedgerTransactionFailedConsumer` (idempotent upsert), mirroring the Phase 009
projection-consumer shape. The table is a faithful record of the inbound event:

```
transaction_failure
  id                      TEXT PK                -- UUID (Ids.generate)
  event_id                TEXT UNIQUE NOT NULL   -- envelope event_id  → idempotency
  transaction_request_id  TEXT NOT NULL          -- CORRELATION KEY    (indexed)
  ledger_transaction_id   TEXT NOT NULL          -- data.transaction_id (ledger recording id)
  transaction_type        TEXT NOT NULL
  failure_code            TEXT
  failure_reason          TEXT
  ledger_correlation_id   TEXT                   -- metadata.correlation_id (= recording id)
  idempotency_key         TEXT                   -- "{request_id}:failed"
  tenant_id               TEXT
  occurred_at             TEXT NOT NULL          -- envelope timestamp (ISO-8601)
  received_at             TEXT NOT NULL          -- consume time
  payload_json            TEXT                   -- raw envelope, for the detail view
  -- indexes: transaction_request_id, ledger_transaction_id, transaction_type, occurred_at
```

Idempotency: `event_id` carries a unique constraint and the consumer upserts (insert, or
no-op on conflict). `transaction_request_id` is unique *per recording* on the ledger, so a
given transaction fails at most once; redelivery of the same failure is a no-op.

**(2) Correlate at *query time* by `transaction_request_id` — do not denormalize the
chaos correlation onto the failure row.** The publish side is taught the key so the join
is a single indexed lookup:

- **Label the request-id field in the catalog.** Add a per-flow designation of *which*
  descriptor is the canonical transaction-request id (a `transactionRequestId: true` flag
  on the field descriptor, or a `transactionRequestIdField()` on the `FlowBuilder` — the
  builders already know it). This is additive to the Phase 011/014 catalog and changes no
  wire contract.
- **Persist `transaction_request_id` on `publish_record`** (new, nullable, indexed
  column; populated going forward from the labelled field at publish time; null for flows
  that mint no transaction — onboarding, va-updated — and for historical rows).
- **Return the emitted request id in the publish responses** — add
  `transactionRequestId` to `FlowResult` and the list to `NTimesSyncResult` (the server
  extracts it from the built payload via the labelled field). The single-flow client
  already knows the value it generated, but N-Times **re-mints request ids server-side**
  (`NTimesExpander`), so the client cannot poll those without the server returning them.
  Returning it also frees the client from needing catalog knowledge of the field name.

Correlation then has one rule everywhere: **`transaction_failure.transaction_request_id`
== `publish_record.transaction_request_id`** (== the value returned by the publish
response). The run page polls failures by the request id(s) it just emitted; the "Sent"
tab resolves outcomes with one batch lookup per page
(`GET /transaction-failures?transactionRequestIds=…`, the Phase 015 batch-balance shape).

**Rejected: denormalize `chaos_correlation_id`/`publish_record_id` onto the failure at
consume time.** It introduces an ordering race — the ledger's outbox can publish the
failure before the chaos `AsyncHistoryWriter` queue has flushed the `publish_record`, so
the lookup could miss and leave a permanently-null link. Query-time matching by
`transaction_request_id` is race-free (the key is intrinsic to both rows) and needs no
backfill.

## Consequences

**Positive**
- One unambiguous, verified correlation key (`transaction_request_id`), intrinsic to both
  sides — no reliance on the ledger echoing a correlation id it does not echo.
- Failures live in their own faithful projection; the immutable publish log stays
  immutable. Clean separation of outbound-publish vs inbound-outcome.
- Query-time join avoids the publish/failure ordering race entirely; no consume-time
  lookup, no backfill, no null-link reconciliation.
- The catalog request-id label + publish-response field are reusable by Part 2/3 and by
  any future "what happened to this transaction" view.

**Negative / trade-offs**
- **Touches the flow catalog and the publish/response path** (label the field; add a
  column; widen two response records) — additive but spread across the engine, history
  writer, and DTOs. Covered by task 003.
- **The outcome is best-effort and asynchronous.** A publish with no matching failure may
  mean *succeeded*, *still in flight*, or *failed-but-not-yet-consumed*. The UI must read
  "no failure" as "no failure **observed yet**", never as a positive success
  confirmation. (A definitive success signal is Part 2's `ledger.balance.updated`.)
- **Per-page batch lookup on the "Sent" tab** is one extra call per page (acceptable, and
  the established Phase 015 pattern); a hot tab with large pages does a bounded
  `IN (…)` query.
- Historical `publish_record` rows have a null `transaction_request_id` (no backfill);
  their outcome cannot be shown. Acceptable — the feature is forward-looking.
- `transaction_failure` only ever holds *failed* transactions, so the table name is
  honest; a future "all transaction outcomes" need (success + failure) would be a
  different projection (Part 2), not an extension of this one.
