# ADR 024 — Generalize the ledger-outbound Kafka consumer to method-typed, multi-event deserialization

## Status
Accepted — 2026-06-27 — Phase [017 — Ledger Transaction-Failure Events & Correlation](../phases/017-ledger-transaction-failure-events/DESIGN.md)

Extends [ADR-011](011-ledger-owned-virtual-accounts-via-kafka-consumer.md) (the first
Kafka consumer). Does **not** supersede it — the account-created projection and its
contract are unchanged; only the deserialization wiring underneath it is generalized.

## Context

[Phase 009](../phases/009-ledger-owned-virtual-accounts/DESIGN.md) introduced the chaos
machine's **first** Kafka consumer to project `ledger.account.created`. The wiring it
established (`com.softspark.chaos.kafka.ConsumerConfiguration`) pins the value
deserializer to a **single** target type:

```java
// today: one factory, one fixed payload type
var jsonDeserializer = new JsonDeserializer<EventEnvelope<LedgerAccountCreatedEventData>>(...);
jsonDeserializer.setUseTypeHeaders(false);   // ledger publishes addTypeInfo(false)
ConsumerFactory<String, Object> f = new DefaultKafkaConsumerFactory<>(props,
    new ErrorHandlingDeserializer<>(new StringDeserializer()),
    new ErrorHandlingDeserializer<>(jsonDeserializer));
```

Because the ledger publishes **without** JSON type headers (`setAddTypeInfo(false)`,
verified in `ss-ledger-service` `ProducerConfiguration`), every record on this factory
deserializes to the one pinned type **regardless of the listener method's parameter
type**. A second `@KafkaListener` for `ledger.transaction.failed` that declares
`EventEnvelope<TransactionFailedEventData>` would silently receive a mis-deserialized
`EventEnvelope<LedgerAccountCreatedEventData>`.

This is **Part 1 of a four-part series** that consumes the ledger's outbound event
surface (verified against `ss-ledger-service`
`ledger.kafka.{account,transaction,reservation}.topics`):

| Part | Idea | Ledger event(s) | Companion DLT |
|---|---|---|---|
| 1 (this phase) | `012_transaction_failure.md` | `ledger.transaction.failed` | `…​.failed.dlt` |
| 2 | `013_balance_history.md` | `ledger.balance.updated` | `…​.updated.dlt` |
| 3 | `015_reservation_created.md` | `ledger.reservation.created` (+ `.released`) | `…​.dlt` |
| 4 | `014_dlt_views.md` | the `.dlt` topics themselves | — |

A type-pinned factory forces a **new factory + new config block per event**. Across four
parts that is four near-identical factories and a growing maintenance surface. We want
one place to add a ledger-outbound listener: declare the listener method with its
`EventEnvelope<T>` and write a mirror record.

Options considered:

- **(a) One typed factory per event.** Additive, zero blast radius on Phase 009, but
  N copies of the same deserializer/error-handler wiring and the boilerplate compounds
  every part. Rejected as the long-term shape.
- **(b) Method-typed deserialization via a message converter.** Use a raw
  `ByteArrayDeserializer` value deserializer and a `ByteArrayJsonMessageConverter`
  (Jackson) on the listener container factory. Spring Kafka then resolves the **target
  type from each `@KafkaListener` method's generic parameter** (`EventEnvelope<T>`), so
  one factory serves every ledger-outbound listener. This is Spring Kafka's documented
  multi-type-listener pattern and does not depend on type headers (which the ledger does
  not send).

## Decision

**Adopt (b): a single `ledgerEventListenerContainerFactory` driven by a Jackson message
converter, with deserialization target resolved per listener method.** Migrate the
existing `ledger.account.created` listener onto it; new parts add only a listener method
+ a mirror record.

### Wiring (in `com.softspark.chaos.kafka.ConsumerConfiguration`)

- **Value deserializer:** `ErrorHandlingDeserializer(ByteArrayDeserializer)`. Raw bytes
  never fail to deserialize; the JSON→object step moves into the converter, where a
  malformed payload surfaces as a `ConversionException` during listener invocation.
- **Record converter:** `ByteArrayJsonMessageConverter(kafkaObjectMapper)` set on the
  container factory (`factory.setRecordMessageConverter(...)`). The shared
  `kafkaObjectMapper` keeps `SnakeCaseStrategy` + JavaTime, matching the producer.
- **Target type:** taken from the listener method signature — e.g.
  `onLedgerTransactionFailed(EventEnvelope<LedgerTransactionFailedEventData> envelope)`.
  No pinned default type, no `JsonDeserializer.trustedPackages("*")` (the declared method
  type is the only type instantiated — strictly safer than trusting a package glob).
- **Error handling (unchanged intent):** keep the `DefaultErrorHandler` +
  `ExponentialBackOff` (`maxAttempts`, `backoffInitialMs`, `backoffMultiplier` from
  `chaos.kafka.consumer.*`) and the `DeadLetterPublishingRecoverer`. The recoverer
  already derives `<topic>.dlt` from the failed record's topic, so it routes
  `ledger.transaction.failed` → `ledger.transaction.failed.dlt` with **no per-event
  config**. Add `MessageConversionException` (and Jackson's `ConversionException`) to the
  handler's **non-retryable** list alongside the existing `DeserializationException`, so a
  poison/malformed JSON payload dead-letters **immediately** rather than retrying a body
  that can never parse.
- **Topics as config:** each ledger-outbound topic is a `chaos.topics.*` property
  (existing convention); this phase adds `chaos.topics.ledger-transaction-failed`
  (default `ledger.transaction.failed`). The DLT is derived, not configured.
- **Gating preserved:** the `chaos.kafka.consumer.enabled` toggle and configurable
  `group-id` continue to gate every listener via `@ConditionalOnProperty`.

## Consequences

**Positive**
- One factory, one error handler, one DLT rule for the whole ledger-outbound surface.
  Parts 2–4 become *a listener method + a mirror record* with no Kafka-config churn.
- Per-listener type safety: each method gets exactly the payload type it declares;
  adding an event can't accidentally collide with another's deserialization.
- Drops the `trustedPackages("*")` glob — the converter only instantiates the method's
  declared type, a small security/robustness win.
- DLT routing is topic-derived, so every new event automatically dead-letters to its own
  `<topic>.dlt` (which the ledger already declares), exactly as Part 4 will want.

**Negative / trade-offs**
- **Touches Phase 009's working consumer.** The account-created listener now
  deserializes via the converter rather than the pinned `JsonDeserializer`. Mitigation:
  its Phase 006/009 Testcontainers tests (happy-path projection, redelivery idempotency,
  poison→DLT) must pass unchanged and are the acceptance gate for the migration; the
  externally observable behaviour (same payload, same DLT) is identical.
- **Failure mode moves.** Malformed JSON now throws at *conversion* (inside listener
  invocation) instead of at *deserialization*. The behaviour is preserved only because
  `ConversionException`/`MessageConversionException` are explicitly added to the
  non-retryable set — an easy detail to miss, so it is called out here and in the task.
- A message converter is marginally more machinery than a fixed deserializer for a
  service that had exactly one consumer; justified only by the four-part series. For a
  single consumer, (a) would have been simpler.
