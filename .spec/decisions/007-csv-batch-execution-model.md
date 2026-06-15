# ADR 007 - CSV batch execution model

## Status
Accepted

## Context
Flows can be issued "single or via csv files". A CSV may contain thousands of rows that each
expand into one (or several, for chaos) Kafka events. We need progress, partial-failure
isolation, backpressure (so we don't overrun the broker or the ledger when load-testing on
purpose), and a durable record of what was sent.

## Decision
Model a batch as a persisted **`batch_run`** with child **`batch_row`** records. Execution:
1. Upload + parse + per-row validate up front; reject the file only on structural errors,
   otherwise mark individual rows `INVALID` and continue.
2. Enqueue valid rows to a **bounded** in-process queue; a small pool of **virtual-thread**
   workers (size and target rate configurable) publishes each row's envelope(s), honoring an
   optional rate limiter for controlled load.
3. Each row transitions `PENDING → PUBLISHED | FAILED`; failures are isolated and recorded
   with the error; the run aggregates counts and a terminal status
   (`COMPLETED | COMPLETED_WITH_FAILURES | FAILED`).
4. Runs are queryable; the UI polls `GET /api/v0/batches/{id}` for progress.

Backpressure: the bounded queue + fixed worker count + optional `maxRatePerSecond` cap the
in-flight load. SQLite history writes are serialized through a single writer to respect the
single-writer model (ADR-002).

## Consequences
- (+) Deterministic, observable, resumable-by-design batch semantics; safe for deliberate load tests.
- (+) Virtual threads make per-row concurrency cheap without pool tuning.
- (−) Large files are streamed/persisted, not held wholly in memory; we cap max file size and
  row count via config.
- (−) Progress is poll-based (no websockets) to stay within swift-admin's react-query
  conventions; acceptable for an operator tool.
