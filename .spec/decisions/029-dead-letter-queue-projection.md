# ADR 029 — Unified dead-letter-queue projection from the ledger's inbound DLTs

## Status
Accepted — 2026-06-27 — Phase [020 — Dead Letter Queue Views](../phases/020-dead-letter-queue-views/DESIGN.md)

Builds on [ADR-024](024-multi-event-ledger-outbound-consumer.md) (the inbound-consumer
infrastructure). **Part 4 of 4** ("testing ledger Kafka events"; Parts 1–3 =
[ADR-025](025-transaction-failure-projection-and-request-id-correlation.md) /
[ADR-027](027-balance-history-projection-from-ledger-balance-updated.md) /
[ADR-028](028-reservation-lifecycle-projection.md)).

## Context

The chaos machine's whole purpose is to drive the ledger with well-formed **and deliberately
malformed / duplicated / out-of-order** traffic and observe how it copes. The ledger's answer
to "I can't process this after N retries" is to **dead-letter** the record. Idea
`014_dlt_views.md` asks to surface those dead letters: **one `dlq` table** for all DLTs, an
endpoint filterable by **dlt domain / transaction id / transaction type**, and a **"Dead
Letter Queue"** nav page (under *Operate*) with a list → tabbed detail (Overview: domain topic,
retry info, error reason/code; Message: the raw payload that was sent).

Verified DLT topology in `ss-ledger-service`:

- **Inbound-consumer DLTs** — when the ledger fails to consume an inbound flow event, its
  `LedgerDeadLetterPublishingRecoverer` publishes to `ledger.<original-topic>.dlt` (17 topics:
  `ledger.collection.completed.dlt`, `ledger.disbursement.{initiated,completed,failed}.dlt`,
  `ledger.disbursement.batch.{initiated,item.completed,item.failed}.dlt`,
  `ledger.organization.va.settlement.{initiated,completed,failed}.dlt`,
  `ledger.organization.{onboarded,va.updated,topup.confirmed,transfer.requested}.dlt`,
  `ledger.organization.treasury.{prefund,sweep,transfer}.completed.dlt`). The DLT **value is a
  structured JSON record** (snake_case):

  ```java
  // ledger: kafka/bootstrap/DeadLetterTopicRecord.java
  public record DeadLetterTopicRecord(
      UUID deadLetterId, Instant deadLetteredAt,
      String originalTopic, int originalPartition, long originalOffset, String originalKey,
      Failure failure,                       // {classification, exceptionType, message, retryCount}
      EventEnvelope<JsonNode> originalEvent) // the original payload; null if it couldn't deserialize
  // Failure.classification ∈ { PROCESSING, DESERIALIZATION, VERSION_RESOLUTION }
  // retry policy: max-attempts=4 (3 retries), exponential 1s×2 → DLT
  ```

  This is exactly the chaos-testing payoff — **the bad traffic the chaos machine published and
  the ledger rejected** — and it carries everything the idea's UI wants in one clean shape.

- **Outbound-event DLTs** — `ledger.account.created.dlt`, `ledger.transaction.failed.dlt`,
  `ledger.balance.updated.dlt`, `ledger.reservation.{created,released}.dlt`, etc. are declared
  by the ledger but populated only by a failed **consumer** — i.e. the chaos machine's own
  Parts 1–3 consumers, via Spring's stock `DeadLetterPublishingRecoverer` (original record
  value + `kafka_dlt-*` headers — a **different** format).

**Scope decision (confirmed with product): ingest the ledger inbound DLTs only.** Those are
the chaos-testing observations and a single rich uniform format. The chaos-machine's own
consumer DLTs (a second, Spring-standard format) are consumer-health, secondary, and would
require normalizing two formats; the table and consumer are designed so they can be added later
without a schema change.

## Decision

**Consume the ledger inbound `.dlt` topics with a dedicated tolerant DLT consumer and project
each dead letter into a single `dlq` table; expose a filterable query API and a "Dead Letter
Queue" page (list + tabbed detail).**

### Consumer

- A new listener subscribed to a **configurable explicit list** of the 17 inbound DLT topics
  (`chaos.topics.ledger-dlts`, defaulting to the full set). An explicit list — **not** a
  `ledger\..*\.dlt` pattern — is used on purpose: a blanket pattern would also pull the
  chaos-own outbound DLTs (different format), so the list keeps the format uniform and the
  scope intentional (and is the extension point for adding chaos-own DLTs later).
- The value deserializes cleanly into a mirror `LedgerDeadLetterRecord` (the ledger always
  produces a valid `DeadLetterTopicRecord` wrapper, even when its `originalEvent` is null
  because the *original* payload was unparseable). `originalEvent` is held as a `JsonNode`.
- **The DLT consumer is terminal and must NEVER re-dead-letter.** It runs on its own container
  factory with a **log-and-skip** error handler (no `DeadLetterPublishingRecoverer`), so a
  failure to project a DLT record can't spawn a `*.dlt.dlt`. Gated by
  `chaos.kafka.consumer.enabled`; its own consumer group.

### `dlq` table (one table, all DLTs — domain-tagged)

```
dlq
  id                      TEXT PK            -- Ids.generate()
  dlt_topic               TEXT NOT NULL       -- the .dlt topic consumed from
  dlt_partition           INTEGER NOT NULL    -- ┐ natural dedup key
  dlt_offset              INTEGER NOT NULL    -- ┘ UNIQUE(dlt_topic, dlt_partition, dlt_offset)
  dead_letter_id          TEXT                -- DeadLetterTopicRecord.deadLetterId
  original_topic          TEXT NOT NULL       -- e.g. collection.completed
  domain                  TEXT NOT NULL       -- derived from original_topic (see below)
  source                  TEXT NOT NULL       -- LEDGER_INBOUND (discriminator; future: CHAOS_CONSUMER)
  event_type              TEXT                -- originalEvent.event_type (best-effort)
  event_id                TEXT                -- originalEvent.event_id (best-effort)
  transaction_id          TEXT                -- best-effort from originalEvent.data (indexed)
  transaction_type        TEXT                -- best-effort (indexed)
  failure_classification  TEXT                -- Failure.classification
  error_type              TEXT                -- Failure.exceptionType  (the "error code")
  error_message           TEXT                -- Failure.message        (the "error reason")
  retry_count             INTEGER             -- Failure.retryCount     (the "retry info")
  original_partition      INTEGER
  original_offset         INTEGER
  original_key            TEXT
  dead_lettered_at        TEXT                -- DeadLetterTopicRecord.deadLetteredAt
  original_payload_json   TEXT                -- originalEvent serialized → the Message tab
  raw_dlt_json            TEXT                -- the full DLT record as received
  received_at             TEXT NOT NULL
  -- indexes: domain; transaction_id; transaction_type; original_topic; received_at
```

- **Dedup by `(dlt_topic, dlt_partition, dlt_offset)`** — always present, even when the
  original payload is unparseable (so it works where `event_id`/`deadLetterId` might be null).
  Upsert/no-op on conflict → at-least-once safe.
- **`domain`** is derived from `original_topic` into a coarse bucket — `COLLECTION`,
  `DISBURSEMENT`, `BATCH_DISBURSEMENT` (`disbursement.batch.*`), `SETTLEMENT`
  (`organization.va.settlement.*`), `TREASURY` (`organization.treasury.*`), `ORGANIZATION`
  (other `organization.*`) — the value the "dlt domain" filter offers.
- **`transaction_id` / `transaction_type`** are extracted **best-effort** from
  `originalEvent.data` (try `transaction_id`, `transaction_request_id`, `settlement_request_id`,
  `transfer_request_id`, `topup_request_id`, `batch_id`, `item_id`; type from
  `data.transaction_type` or derived from `event_type`). Null when the original couldn't be
  parsed (a `DESERIALIZATION` failure) — which is itself meaningful and filterable by
  `failure_classification`.

### Query API + UI

- `GET /api/v0/dlq` — `PageResponse<DeadLetterRecordResponse>`, filters
  `domain`, `transactionId`, `transactionType` (+ `originalTopic`, `failureClassification`,
  `from`/`to`), newest-first by `received_at`; `GET /api/v0/dlq/{id}` for the detail.
- Frontend: a **"Dead Letter Queue"** item under *Operate* (`/chaos/dlq`), a filterable list,
  and a tabbed detail (`/chaos/dlq/:id`): **Overview** (domain + original topic, `retry_count`,
  `error_message`, `error_type`/`failure_classification`, dead-lettered-at, original
  coordinates) and **Message** (the `original_payload_json` via the existing `JsonPanel`).

## Consequences

**Positive**
- Closes the chaos-testing loop: the operator can finally **see what the ledger rejected** from
  their published (often deliberately-broken) traffic, with the error class/reason, retry count,
  and the exact payload — the core observability the harness was missing.
- One rich uniform format (`DeadLetterTopicRecord`) → a clean projection, exact mapping to the
  Overview/Message tabs, and reliable domain/transaction filters.
- One `dlq` table for all DLTs (domain-tagged) per the idea — no table-per-topic sprawl — and
  designed so the chaos-own consumer DLTs can be folded in later behind the `source`
  discriminator without a migration.
- Tolerant, terminal consumer (no re-dead-lettering) — a DLT viewer that can't itself create
  dead letters.

**Negative / trade-offs**
- **Chaos-own consumer DLTs are out of scope** (consumer health) — a deliberate cut; they're a
  second format and secondary to the testing payoff. Folding them in later means a second
  format-mapping (Spring `kafka_dlt-*` raw) and adding those topics to the list.
- **Best-effort transaction extraction** — the request-id field differs per event type, and a
  `DESERIALIZATION`-class dead letter has no parseable `originalEvent` at all, so
  `transaction_id`/`type` can be null. Acceptable: the row is still captured, filterable by
  `domain`/`originalTopic`/`failureClassification`, and the raw payload is shown.
- **Eventually-consistent mirror** of the ledger's DLTs (at-least-once; consumer lag). It's an
  observation log, not the DLT itself; the Kafka DLT topics (14-day retention) remain the
  source of truth, and reprocessing/replay is **not** in scope (view-only).
- **Coupling to the ledger's DLT contract** (`DeadLetterTopicRecord` shape + the 17 topic
  names). If the ledger renames topics or changes the record, the consumer's list/mirror must
  follow; pinned by a contract test and an explicit configurable topic list.
- **No precise per-message retry timeline** beyond `Failure.retryCount` + the configured policy
  (max-attempts=4, 1s×2 backoff); the Overview shows those, not a per-attempt log.
