# Phase 3 - Transaction Flow Engine

## Summary
The heart of the chaos machine: a flow engine that turns operator requests into the exact
ledger Kafka events, **single or via CSV**, resolving system VAs from the chart of accounts,
applying optional **chaos strategies** (duplicate / out-of-order / malformed / burst /
unbalanced / delayed), and recording **every** publication in a queryable history with batch
run tracking.

## Motivation
This realizes the MANIFEST's core: "received requests from a UI app and formulates
transaction messages to publish over kafka … single or via csv files", and the objective —
"testing the ledger's resilience in a controlled way". The bin scripts under
`ss-ledger-service/bin` are the behavioral oracle; this phase makes them a UI-driven,
auditable, controllable service.

## User-Facing Changes
- `POST /api/v0/flows/{flowType}` — publish one flow (well-formed or with chaos options).
- `POST /api/v0/batches` (multipart CSV) — upload + run a batch; `GET /api/v0/batches/{id}`
  for progress; `GET /api/v0/batches` to list runs.
- `GET /api/v0/flows/catalog` — describe each flow's fields, slots, and CSV columns.
- `GET /api/v0/history` — query published events (by flow, VA, correlation id, status, time).

## Architecture Impact
Adds `com.softspark.chaos.flow`, `.batch`, `.history`. Defines payload records
`flow/model/v1/*` (the 11 schemas), the `FlowType`/`SlotName` enums (shared with Phase 002),
SQLite tables `publish_record`, `batch_run`, `batch_row`. Consumes Phase 001 publisher +
Phase 002 resolution. Surfaced by Phase 005 transactions + chaos pages.

## Edge Cases
- Slot resolution failures (missing required client/org VA) → `400` before publish.
- Chaos "malformed" must still be *sendable* bytes (the point is the ledger rejects them).
- Out-of-order requires multi-event flows (e.g. settlement initiated→completed) reordered.
- CSV partial failure isolation; oversized files; duplicate idempotency keys (intentional in chaos).
- SQLite single writer under burst → history writes serialized via a single-writer queue.
- Amount precision/currency (`@ISO4217`, `BigDecimal`, scale per currency).

## Testing Strategy
- Contract tests: each flow's envelope equals the corresponding `bin/kafka-payload-samples.md`
  fixture for identical inputs.
- Engine unit tests for slot resolution + each chaos strategy's transformation.
- CSV tests: valid/invalid rows, partial failure, header mapping, large-file streaming.
- Integration (Testcontainers Kafka): publish single + batch; consume; assert payloads,
  ordering (and deliberate disorder), duplicates.
- History/run query tests.

## Deployment Strategy
Chaos endpoints behind auth + bounded by config (`maxRatePerSecond`, `maxBatchRows`). The
targeted Kafka cluster label is surfaced in health/logs as a guard rail. No special flag, but
"destructive" chaos strategies are opt-in per request.

## Tasks
- [001 - Flow engine framework](001-flow-engine-framework.md)
- [002 - Single transaction publishing API (full schemas)](002-single-transaction-publishing-api.md)
- [003 - CSV batch publishing](003-csv-batch-publishing.md)
- [004 - Chaos injection strategies](004-chaos-injection-strategies.md)
- [005 - Publish history & run tracking](005-publish-history-and-run-tracking.md)

## Parallel Tasks
001 + 002 first (engine + schemas). Then **003**, **004**, **005** can proceed in parallel
(batch, chaos, history are independent given the engine + history-writer seam from 001).
