# Task 001 - `ledger.balance.updated` consumer + `balance_history` projection

> Java 25 · Spring Boot 4 / Spring Kafka · new package `com.softspark.chaos.balance`
> Implements the persistence half of [ADR-027](../../decisions/027-balance-history-projection-from-ledger-balance-updated.md).
> **Depends on Phase 017 Task 001** (the ADR-024 method-typed multi-event container factory).

## Functional Requirements

1. A `LedgerBalanceUpdatedConsumer` consumes `ledger.balance.updated` on the shared
   `LEDGER_EVENT_CONTAINER_FACTORY` and persists each event into a new `balance_history` table.
2. Consumption is **idempotent** by envelope `event_id` (at-least-once safe → one row).
3. Two *distinct* events for the same account in one transaction (e.g. funding entry +
   reservation release) are stored as **separate** rows (distinct `event_id`) — not deduped.
4. `currency` is **backfilled best-effort** from the `virtual_account` projection
   (`account_id` = `va_id`); null when the VA isn't projected yet.
5. Gated by `chaos.kafka.consumer.enabled`; empty/partial envelopes are logged and skipped
   (no row, no DLT).

## Acceptance Criteria

- [ ] `LedgerBalanceUpdatedEventData` is a snake_case mirror record:
      `(UUID accountId, BigDecimal availableBalance, BigDecimal pendingBalance, BigDecimal reservedBalance, BigDecimal totalBalance, BigDecimal totalDebits, BigDecimal totalCredits, long lastEntrySequence, LocalDateTime balanceAsOf)`.
- [ ] `@KafkaListener(topics = "${chaos.topics.ledger-balance-updated}", groupId = …, containerFactory = LEDGER_EVENT_CONTAINER_FACTORY)`
      receives `EventEnvelope<LedgerBalanceUpdatedEventData>`.
- [ ] `chaos.topics.ledger-balance-updated` (default `ledger.balance.updated`) is configured;
      DLT derives as `ledger.balance.updated.dlt` (ADR-024 recoverer).
- [ ] Flyway `V14` creates `balance_history` with UNIQUE(`event_id`) and indexes on
      `account_id`, `(account_id, last_entry_sequence)`, `(account_id, occurred_at)`.
- [ ] Publishing one balance event creates one row with every field mapped; republishing the
      same `event_id` leaves exactly one row.
- [ ] A transfer producing 3 per-account events yields 3 rows (one per `account_id`).
- [ ] Balance buckets persist as exact decimal strings (TEXT) and round-trip without precision
      loss.
- [ ] `currency` is populated from the VA registry when present, else null.

## Technical Design

### Field mapping (envelope → row)

| Column | Source |
|---|---|
| `id` | `Ids.generate()` |
| `event_id` | envelope `event_id` *(unique)* |
| `account_id` | `data.account_id` (= `va_id`) |
| `available_balance` / `pending_balance` / `reserved_balance` / `total_balance` | `data.*_balance` |
| `total_debits` / `total_credits` | `data.total_debits` / `data.total_credits` |
| `last_entry_sequence` | `data.last_entry_sequence` |
| `balance_as_of` | `data.balance_as_of` (LocalDateTime, zoneless) |
| `currency` | `virtual_account.currency` where `va_id = account_id` (best-effort) |
| `idempotency_key` | `metadata.idempotency_key` |
| `ledger_correlation_id` | `metadata.correlation_id` (random; stored for completeness) |
| `tenant_id` | `metadata.tenant_id` |
| `occurred_at` | envelope `timestamp` |
| `received_at` | `Instant.now()` |
| `payload_json` | raw envelope |

```mermaid
sequenceDiagram
  participant L as ss-ledger-service
  participant K as Kafka (ledger.balance.updated)
  participant C as LedgerBalanceUpdatedConsumer
  participant VA as virtual_account (registry)
  participant DB as balance_history
  L->>K: EventEnvelope<AccountBalanceUpdatedEventData> (one per affected account)
  K->>C: poll (method-typed deserialize, ADR-024)
  C->>C: envelope/data null? log + skip
  C->>VA: lookup currency by account_id (best-effort)
  C->>DB: upsert by event_id (insert | no-op)
  Note over C,DB: at-least-once → one row; distinct events → distinct rows
```

### Flyway `V14__balance_history.sql`

```sql
CREATE TABLE IF NOT EXISTS balance_history (
    id                    TEXT PRIMARY KEY,
    event_id              TEXT NOT NULL UNIQUE,
    account_id            TEXT NOT NULL,
    available_balance     TEXT NOT NULL,
    pending_balance       TEXT NOT NULL,
    reserved_balance      TEXT NOT NULL,
    total_balance         TEXT NOT NULL,
    total_debits          TEXT,
    total_credits         TEXT,
    last_entry_sequence   INTEGER NOT NULL,
    balance_as_of         TEXT NOT NULL,
    currency              TEXT,
    idempotency_key       TEXT,
    ledger_correlation_id TEXT,
    tenant_id             TEXT,
    occurred_at           TEXT NOT NULL,
    received_at           TEXT NOT NULL,
    payload_json          TEXT
);
CREATE INDEX IF NOT EXISTS idx_bh_account        ON balance_history (account_id);
CREATE INDEX IF NOT EXISTS idx_bh_account_seq    ON balance_history (account_id, last_entry_sequence);
CREATE INDEX IF NOT EXISTS idx_bh_account_time   ON balance_history (account_id, occurred_at);
```

> **Migration number.** On-disk highest is `V11`; Phase 017 adds `V12`+`V13`. This phase is
> `V14` **assuming Phase 017 lands first** (build order 017 → 018). If built independently,
> use the next free version.

## Implementation Notes

- **New package** `com.softspark.chaos.balance` (feature-first, mirrors Phase 009 `account/consumer`
  and Phase 017 `transaction`):
  - `consumer/LedgerBalanceUpdatedConsumer.java` — `@KafkaListener` + `@ConditionalOnProperty(prefix="chaos.kafka.consumer", name="enabled", havingValue="true", matchIfMissing=true)`.
  - `consumer/LedgerBalanceUpdatedEventData.java` — `@JsonNaming(SnakeCaseStrategy.class)` mirror record.
  - `model/BalanceHistory.java` — JPA entity (immutable; `created_at`-style `received_at`; not `AuditableEntity` since rows never mutate).
  - `repository/BalanceHistoryRepository.java` — `JpaRepository<BalanceHistory, String>` + `boolean existsByEventId(String)`; query methods in Task 002.
  - `service/BalanceHistoryProjectionService.java` — `@Transactional` map + idempotent upsert + currency backfill.
  - `base/BigDecimalStringConverter.java` (new, in `base`) — `AttributeConverter<BigDecimal, String>`, mirroring `InstantStringConverter`.
- **Modify** `kafka/TopicCatalog.java`: add `ledger-balance-updated` topic.
- **New migration** `chaos-machine/src/main/resources/db/migration/V14__balance_history.sql`.
- Reuse the `VirtualAccountRepository` (by `va_id`) for the currency backfill; tolerate a
  missing VA (null currency).

## Non-Functional Requirements

- **Resilience:** at-least-once safe (UNIQUE `event_id`); null/partial envelopes never DLT;
  only unparseable bytes DLT (ADR-024 handler).
- **Precision:** BigDecimal persisted as exact decimal strings.
- **Observability:** debug-log each consumed update (`account_id`, `last_entry_sequence`); a
  counter for projected balance updates feeds the consumer-lag view.
- **Storage:** unbounded growth acceptable; retention/prune out of scope (noted).

## Dependencies

- **Phase 017 Task 001** (generalized factory + topic-config pattern + DLT derivation). If
  Phase 017 is not yet implemented, this task must pull in that generalization first.
- The `virtual_account` projection (Phase 009) for currency backfill.
- External ledger contract (verified): `AccountBalanceUpdatedEventData` + envelope/metadata,
  snake_case, `addTypeInfo(false)`.

## Risks & Mitigations

- **`last_entry_sequence == 0` edge case** (ledger JPA-timing quirk). → Never rely on
  sequence alone for ordering/dedup; dedup is by `event_id`, ordering is
  `(occurred_at DESC, last_entry_sequence DESC)`.
- **Currency absent from the event.** → Best-effort VA-registry backfill; UI falls back to the
  VA's currency. Documented.
- **Contract drift** (new balance field). → `fail-on-unknown-properties=false` tolerates
  additive change; a contract test pins today's field set.
- **High event volume** under chaos bursts. → Consumer keeps `concurrency=1` default; the
  projection is a single indexed insert.

## Testing Strategy

- **Unit:** envelope→entity mapping (all buckets + sequence + as-of); BigDecimal↔String
  round-trip; idempotent upsert (existsByEventId); currency backfill present/absent;
  null-data skip.
- **Integration (Testcontainers Kafka):** publish one balance event → one row; republish →
  one row; publish 3 per-account events → 3 rows; poison → `ledger.balance.updated.dlt`.
- **Contract:** `LedgerBalanceUpdatedEventData` round-trips the ledger's exact snake_case JSON
  (sample from `ss-ledger-service` / `bin/kafka-payload-samples.md`).
- Folds into [Phase 006](../006-testing-and-verification/DESIGN.md).

## Deployment Strategy

- Additive Flyway `V14`; no backfill (history accrues from rollout). Consumer gated by
  `chaos.kafka.consumer.enabled`; topic + group id configurable; DLT derived. Shippable once
  Phase 017's consumer generalization is in place.
