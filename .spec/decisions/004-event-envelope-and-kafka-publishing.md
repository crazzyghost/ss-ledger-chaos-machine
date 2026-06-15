# ADR 004 - Event envelope and Kafka publishing

## Status
Accepted

## Context
The ledger consumes 11 inbound topics. Every payload shares one envelope shape (observed in
`ss-ledger-service/bin/*.sh` and `bin/kafka-payload-samples.md`):

```json
{ "event_id": "...", "event_type": "<topic>", "timestamp": "<ISO8601>",
  "source": "<service>", "version": "1.0",
  "data": { ... },
  "metadata": { "correlation_id": "...", "idempotency_key": "...", "tenant_id": "..." } }
```

The ledger's own producer uses `KafkaTemplate<String, EventEnvelope<?>>` with a
`JsonSerializer` configured `addTypeInfo=false`, snake_case via
`@JsonNaming(SnakeCaseStrategy.class)`, and versioned `v1` payload records.

## Decision
Reproduce the contract exactly. Define a generic, immutable
`EventEnvelope<T>` record + `EventMetadata` record (snake_case JSON), one `record` per
payload under `flow/model/v1`, and a `TopicCatalog` of typed topic constants. Publish with a
`KafkaTemplate<String, Object>` configured with `StringSerializer` key +
`JsonSerializer` value (`spring.json.add.type.headers=false`). `event_type` always equals the
destination topic name; `source` is set to the originating service the ledger expects.

Producer reliability settings (the harness must be trustworthy even while injecting chaos):
`acks=all`, `enable.idempotence=true`, `retries` high with `delivery.timeout.ms` bounded,
key = the natural aggregate id (e.g. `va_id` / `organization_id`) for per-entity ordering.

"Chaos" is applied **above** the producer (duplicate/reorder/malform/burst strategies), never
by weakening producer durability — so the harness never loses events it claims to have sent.

## Consequences
- (+) Byte-compatible with what the ledger already consumes; the bin scripts become a
  living oracle for contract tests.
- (+) Idempotent producer + entity-keyed partitioning give us *controllable* ordering, which
  is exactly what out-of-order chaos needs to subvert deliberately.
- (−) Payload records must track the ledger's schema; drift is caught by a shared
  contract-test fixture set derived from `bin/kafka-payload-samples.md`.
