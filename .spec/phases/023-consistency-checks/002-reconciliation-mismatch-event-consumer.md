# Task 002 - Reconciliation mismatch event consumer (Kafka projection)

## Functional Requirements
Consume `ledger.reconciliation.mismatch` events from Kafka and project them to a local
`reconciliation_mismatch` table so the chaos UI can toast immediately when a check completes with
findings, rather than relying on client-side poll. Must deduplicate replayed events; must handle
out-of-order delivery; must survive ledger resets (event stream may reference check ids that no
longer exist in the ledger).

## Acceptance Criteria
- [ ] Kafka consumer listens on `ledger.reconciliation.mismatch` topic (default: `ledger.reconciliation.mismatch`)
- [ ] Consumer uses the existing shared method-typed container factory (`ByteArrayJsonMessageConverter`, target type `EventEnvelope<ReconciliationMismatchEventData>`)
- [ ] Consumer deserializes `ReconciliationMismatchEventData` v1 payload (`checkId`, `type`, `initiatorType`, `asOf`, `initiatedAt`, `completedAt`, `discrepancyCount`)
- [ ] Each event is projected to a `reconciliation_mismatch` row with columns: `id` (UUID), `check_id` (UUID, unique), `type` (TEXT), `initiator_type` (TEXT), `as_of` (TIMESTAMP), `initiated_at` (TIMESTAMP), `completed_at` (TIMESTAMP), `discrepancy_count` (INTEGER), `consumed_at` (TIMESTAMP)
- [ ] Consumer uses `INSERT OR IGNORE` (SQLite idempotency) to deduplicate replayed events (keyed by `check_id`)
- [ ] Consumer commits offset only after successful projection (at-least-once delivery guarantee)
- [ ] When the consumer fails to parse or project an event, the event is routed to the existing DLT (`ledger.reconciliation.mismatch.dlt`) after exhausting retries (shared `ErrorHandlingDeserializer` + `DeadLetterPublishingRecoverer` from Phase 009)
- [ ] Consumer is tagged with `@KafkaListener(groupId = "chaos-machine-consistency-mismatch-consumer")`

## Technical Design

**Consumer class (new):**
```
com.softspark.chaos.consistencycheck.consumer
└── ReconciliationMismatchEventConsumer.java
```

**Repository (new):**
```
com.softspark.chaos.consistencycheck.repository
└── ReconciliationMismatchRepository.java
```

**Entity (new):**
```
com.softspark.chaos.consistencycheck.model
└── ReconciliationMismatch.java
```

**Flyway migration (new):**
```
chaos-machine/src/main/resources/db/migration
└── V023__create_reconciliation_mismatch_table.sql
```

**Consumer implementation:**
`ReconciliationMismatchEventConsumer` is a Spring `@Component` with a single `@KafkaListener`
method. The method signature mirrors the existing `LedgerAccountCreatedConsumer` /
`TransactionFailureConsumer` / `BalanceUpdatedConsumer` / `ReservationLifecycleConsumer` pattern:

```java
@KafkaListener(
    topics = "${kafka.topics.ledger-reconciliation-mismatch:ledger.reconciliation.mismatch}",
    groupId = "chaos-machine-consistency-mismatch-consumer",
    containerFactory = "methodTypedKafkaListenerContainerFactory")
public void consume(EventEnvelope<ReconciliationMismatchEventData> envelope) {
  // extract payload
  // map to ReconciliationMismatch entity
  // save via repository (INSERT OR IGNORE)
  // log at INFO level: "Consumed ledger.reconciliation.mismatch for check {}"
}
```

The consumer is **stateless and transactional**: it does not fetch the check detail from the ledger
(that is the UI's job via the proxy). It only records the mismatch event as a local projection. The
`check_id` column is UNIQUE; replayed events are silently ignored by SQLite's `INSERT OR IGNORE`.

**Entity:**
`ReconciliationMismatch` is a JPA entity with `@Table(name = "reconciliation_mismatch")`. Columns:

- `id`: UUID, primary key, generated via `UlidCreator.getMonotonicUlid().toUuid()` (the existing
  pattern from Phase 001)
- `checkId`: UUID, unique, non-null
- `type`: String, non-null (enum name: `ACCOUNT_BALANCE_PROJECTION`, `ENTRY_BALANCE`, `SEQUENCE_INTEGRITY`)
- `initiatorType`: String, non-null (enum name: `SYSTEM`, `PLATFORM_OPERATOR`)
- `asOf`: Instant, non-null
- `initiatedAt`: Instant, non-null
- `completedAt`: Instant, non-null
- `discrepancyCount`: int, non-null (always >= 1 by contract)
- `consumedAt`: LocalDateTime, non-null, default `now()` (the instant the event was consumed)

The entity has standard Hibernate mappings (`@Column`, `@Id`). No `@CreatedDate` / `@LastModifiedDate` (no Spring Data JPA auditing here — `consumedAt` is set explicitly).

**Repository:**
`ReconciliationMismatchRepository extends JpaRepository<ReconciliationMismatch, UUID>`. No custom
methods needed in Task 002 (Task 003 adds a `findAllByConsumedAtAfter` method for the polling query).

**Migration:**
`V023__create_reconciliation_mismatch_table.sql` creates the table with the schema above. The
`check_id` column has a UNIQUE constraint. No foreign key to any chaos table (the check lives in the
ledger, not in the chaos DB). The table is **append-only** (no UPDATE, no DELETE) except for
operator-initiated manual cleanup (out of scope for this phase).

```sql
CREATE TABLE reconciliation_mismatch (
    id TEXT PRIMARY KEY,
    check_id TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    initiator_type TEXT NOT NULL,
    as_of TEXT NOT NULL,
    initiated_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    discrepancy_count INTEGER NOT NULL,
    consumed_at TEXT NOT NULL
);

CREATE INDEX idx_reconciliation_mismatch_consumed_at ON reconciliation_mismatch(consumed_at);
```

The `consumed_at` index supports the polling query in Task 003 (frontend polls `GET /reconciliation-mismatches?since={timestamp}`).

**Topic configuration:**
Add to `application.yaml`:
```yaml
kafka:
  topics:
    ledger-reconciliation-mismatch: ledger.reconciliation.mismatch
```

Add to `TopicCatalog` (the existing enum in `com.softspark.chaos.kafka`):
```java
LEDGER_RECONCILIATION_MISMATCH("ledger.reconciliation.mismatch");
```

**Consumer configuration:**
Reuse the existing `methodTypedKafkaListenerContainerFactory` from Phase 017. No new factory, no new
deserializer config. The consumer inherits the shared retry policy (3 attempts, exponential backoff
1s→2s→4s) and DLT routing (`ConsumerProperties.errorHandlingDeserializer()` +
`DeadLetterPublishingRecoverer` from Phase 009).

## Implementation Notes

**Event version:** The payload is v1 (`ReconciliationMismatchEventData` as of the ledger's
reporting Phase 2). The consumer does not version-negotiate — it expects v1 only. If the ledger ever
ships v2, a new consumer can be added in a future phase (the existing Phase 017 pattern:
`TransactionFailureConsumer` could become `TransactionFailureV1Consumer` +
`TransactionFailureV2Consumer` if needed).

**Kafka key:** The ledger sends the check id as the Kafka record key. The chaos consumer does not
read the key (the payload carries `checkId` anyway, and the consumer is not partitioned by key). The
key is logged for observability.

**Idempotency:** The consumer is **at-least-once**. Replayed events (Kafka rebalance, consumer
restart) are deduplicated by the `check_id UNIQUE` constraint. SQLite's `INSERT OR IGNORE` silently
ignores constraint violations; the consumer does not throw, and the offset is committed.

**Out-of-order delivery:** Not a concern. Each event is independent (no causal ordering between
check runs). The `consumed_at` timestamp is the only ordering signal, and it is the chaos machine's
local time, not the ledger's.

**Ledger reset:** If the ledger is reset (DB wipe, schema drop, etc.), its check table is empty but
the chaos `reconciliation_mismatch` table still has rows. Those rows reference check ids that no
longer exist in the ledger. The UI (Task 005) handles this: when it fetches the check detail via the
proxy and gets **404**, it shows "Check not found (ledger may have been reset)". No cleanup job is
implemented in this phase; the operator can manually `DELETE FROM reconciliation_mismatch` if needed.

**Logging:** Log each consumed event at **INFO** level:
```
Consumed ledger.reconciliation.mismatch for check {checkId}: type={type}, discrepancies={count}
```

Log DLT routing at **WARN** level (existing behaviour from `DeadLetterPublishingRecoverer`).

## Non-Functional Requirements
- **Performance:** The consumer must handle 100 events/sec without lag (each event is a simple
  INSERT OR IGNORE; SQLite can handle this easily).
- **Reliability:** The consumer must not lose events. At-least-once delivery is acceptable;
  duplicate projection is idempotent.
- **Observability:** All consumed events are logged at INFO level. DLT events are logged at WARN
  level (existing behaviour).

## Dependencies
- Existing `kafka` package (`TopicCatalog`, `ConsumerConfiguration`, `ConsumerProperties`)
- Existing `base` package (`UlidCreator` for id generation)
- Existing `ledgerproxy` package (no direct dependency; the consumer does not call the proxy)
- No new external dependencies (Spring Kafka already present)

## Risks & Mitigations
**Risk:** Ledger never publishes the event (event is currently under development, flag-controlled).  
**Mitigation:** The consumer is passive; if no events arrive, the `reconciliation_mismatch` table
stays empty and the UI never toasts. No harm done. Task 003 (polling endpoint) allows the UI to fall
back to poll-only mode if events never arrive.

**Risk:** Consumer falls behind (Kafka lag) during chaos testing (high event volume).  
**Mitigation:** The consumer is fast (one INSERT OR IGNORE per event). If lag accumulates, it is a
sign that the chaos machine is under-provisioned for the test load. The operator can monitor lag via
Kafka metrics (out of scope for this phase).

**Risk:** DLT accumulates events the consumer cannot parse.  
**Mitigation:** The existing DLQ view (Phase 020) surfaces DLT events. The operator can inspect
failed events and, if needed, fix the consumer and replay from the DLT (manual process, out of scope).

## Testing Strategy

**Unit tests (consumer):**
- `ReconciliationMismatchEventConsumerTest`: Mock `ReconciliationMismatchRepository`, create a valid
  `EventEnvelope<ReconciliationMismatchEventData>`, call `consume()`, verify the repository's `save`
  method was called with the correct entity, verify log at INFO level.
- Test duplicate consumption: call `consume()` twice with the same payload, verify the repository is
  called twice but SQLite's `INSERT OR IGNORE` deduplicates (integration-test level, not pure unit).

**Integration tests (Kafka):**
- `ReconciliationMismatchEventConsumerIntegrationTest` (Testcontainers Kafka): Publish a
  `ledger.reconciliation.mismatch` event to the topic, verify the consumer projects it to the
  `reconciliation_mismatch` table, verify the row has the correct columns, verify replay
  deduplication (publish the same event twice, verify only one row).

**Manual verification:**
- Run the chaos machine + a ledger that publishes the event (branch to be determined).
- Trigger a consistency check via the ledger UI (or directly via the ledger's `PUT /consistency-checks`).
- Wait for the check to complete with findings (ensure the ledger's task worker is running).
- Verify the ledger publishes `ledger.reconciliation.mismatch`.
- Verify the chaos consumer logs "Consumed ledger.reconciliation.mismatch for check ...".
- Verify a row appears in the chaos `reconciliation_mismatch` table.
- Restart the chaos machine, verify the event is replayed, verify no duplicate row (idempotency).

## Deployment Strategy
Deploy Flyway migration (`V023__create_reconciliation_mismatch_table.sql`) first. Deploy backend
with the new consumer. Verify the consumer is registered in Spring's Kafka listener registry (log
line: `partitions assigned`). No frontend change in this task (Task 003 adds the polling endpoint;
Task 005 uses it).
