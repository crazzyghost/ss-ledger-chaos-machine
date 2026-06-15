# Task 001 - Flow Engine Framework

## Functional Requirements
- A pluggable framework that, given a `FlowType` + a typed request, resolves system VAs from
  the chart of accounts, builds the correct `EventEnvelope<T>`, applies optional chaos, and
  publishes — while emitting a history record. One seam shared by single (Task 002) and batch
  (Task 003) execution.

## Acceptance Criteria
- [ ] A `FlowType` enum enumerates all 11 flows; each maps to a topic, a `source`, and a payload type.
- [ ] A `FlowBuilder<T>` per flow turns a `FlowRequest` into `EventEnvelope<T>`, filling slots
      via Phase 002 resolution precedence.
- [ ] `FlowEngine.execute(FlowExecution)` resolves → builds → (chaos) → publishes → records
      history, returning a `FlowResult`.
- [ ] Adding a new flow = adding one `FlowBuilder` + payload record (open/closed).
- [ ] Envelope `metadata.correlationId` defaults to the request correlation id; `idempotencyKey`
      defaults to `<event_type>:<eventId>` (overridable).

## Technical Design
Sealed dispatch with records + pattern matching (Java 25):

```java
public enum FlowType {
  ORGANIZATION_ONBOARDED, ORGANIZATION_VA_UPDATED,
  TOPUP_CONFIRMED, TRANSFER_REQUESTED,
  TREASURY_PREFUND_COMPLETED, TREASURY_SWEEP_COMPLETED, TREASURY_TRANSFER_COMPLETED,
  SETTLEMENT_INITIATED, SETTLEMENT_COMPLETED, SETTLEMENT_FAILED,
  COLLECTION_COMPLETED;
}

public interface FlowBuilder<T> {
  FlowType type();
  EventEnvelope<T> build(FlowRequest request, FlowContext ctx);  // ctx = resolved slots, ids, clock
}
```

`FlowEngine` wiring:

```mermaid
flowchart LR
  req[FlowRequest] --> res[SlotResolver<br/>(Phase 002 precedence)]
  res --> bld[FlowBuilder.build → EventEnvelope]
  bld --> chaos[ChaosStrategy.apply*<br/>(Task 004, optional)]
  chaos --> pub[ChaosEventPublisher<br/>(Phase 001)]
  pub --> hist[HistoryWriter<br/>(Task 005)]
  pub --> out{{Kafka}}
```

Key abstractions (package `flow`):
- `FlowRequest` — base fields (`flowType`, `amount?`, `currency?`, `tenantId?`,
  `correlationId?`, explicit slot VA overrides, `chaos?`) + a typed `Map`/record per flow.
- `FlowContext` — resolved slot VA ids, generated `eventId` (ULID), `timestamp` (`ChaosClock`),
  `source`, `tenantId`.
- `FlowBuilderRegistry` — `Map<FlowType, FlowBuilder<?>>` injected from all `FlowBuilder` beans.
- `FlowEngine` — orchestrates resolve→build→chaos→publish→record; returns
  `FlowResult{eventId, topic, partition, offset, status, historyId}`.
- `HistoryWriter` (interface; impl in Task 005) — single-writer to respect SQLite.

`FlowExecution` carries one logical operation that may expand into **several** envelopes
(needed for out-of-order/duplicate chaos and multi-event settlement sequences).

## Implementation Notes
- Packages: `flow` (engine, registry, request/context/result), `flow/builder`,
  `flow/model/v1` (payload records), `flow/resolver` (`SlotResolver`).
- Define `FlowType`/`SlotName` in a neutral place shared with Phase 002 (e.g. `flow/model`)
  to avoid cycles; `account` depends on it.
- Builders are stateless `@Component`s; register by `type()`.
- Reuse Phase 001 `ChaosEventPublisher` and Phase 002 resolution; do not duplicate.

## Non-Functional Requirements
- O(1) flow dispatch; no reflection on the hot path beyond startup registry build.
- Thread-safe and side-effect-free builders (safe under virtual-thread batch fan-out).

## Dependencies
Phase 001 (publisher/envelope), Phase 002 (slot resolution, account model).

## Risks & Mitigations
- *Builder/registry omissions* → a test asserts every `FlowType` has a registered builder.
- *Hidden coupling between flows* → builders are independent; shared logic in `FlowContext`/helpers.

## Testing Strategy
- Registry completeness test (all 11 types covered).
- Per-builder unit tests of envelope construction (vs. fixtures) — bulk in Task 002.
- Engine test: resolve→build→publish→record happy path with a stub publisher + history writer.

## Deployment Strategy
No flag. Internal framework consumed by Tasks 002–005.
