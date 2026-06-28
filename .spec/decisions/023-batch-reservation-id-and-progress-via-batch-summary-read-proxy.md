# ADR 023 — Sourcing the batch `reservation_id` and live progress via the ledger batch-summary read-proxy

## Status
Accepted — 2026-06-27 — Phase [016 — Batch Disbursement](../phases/016-batch-disbursement/DESIGN.md)

## Context

`disbursement.batch.item.completed` and `disbursement.batch.item.failed` both carry a
**required** `reservation_id`, but the chaos machine never mints it: the **ledger** creates
the single BATCH reservation when it consumes `BATCH_RESERVATION_REQUEST`
(`reservation_id = UUID.randomUUID()`, `transaction_ref = batch_id`). So — exactly as with
the single-disbursement lifecycle ([ADR-018](018-reservation-id-via-ledger-read-proxy-poll.md))
— the orchestrator must come up with a `reservation_id` for the item events that it did not
create.

What the ledger actually does (verified in `ss-ledger-service`):

1. On `BATCH_RESERVATION_REQUEST`, `BatchTransactionRecordingService` creates the batch
   (keyed by `transaction_request_id = batch_id`) and a `BATCH` reservation
   (`transaction_ref = batch_id`, indexed `disbursement_batch_id = batch_id`).
2. On `item.completed`/`item.failed`, the handler loads the batch by `batch_id` and uses
   `batch.getReservationId()` — it **ignores the inbound `reservation_id`** (a mismatch is
   silently overridden by the stored value). So the inbound field is **structurally required
   but functionally cosmetic**; the linkage that matters is `batch_id`, which the driver
   controls.

Two ledger read surfaces can yield the real id:

- **(a) The account-reservations endpoint** reused from ADR-018:
  `GET /api/v0/accounts/{accountId}/reservations` (the existing chaos `ReservationLookup`
  fetches the list and filters by `transactionRef` in-process; `ReservationResponse` already
  exposes `disbursementBatchId`, `type`, `status`, `amountCaptured`, `amountReleased`). With
  `accountId = source VA` and `transactionRef = batch_id` this returns the BATCH reservation.
- **(b) A purpose-built batch-summary endpoint:**
  `GET /api/v0/disbursement-batches/{batchId}` (verified: `DisbursementBatchQueryController`),
  which returns the batch's `reservation_id` **directly** plus the live
  status + `item_count`/`processed`/`failed`/`pending` counters and monetary totals.

Forces:

1. **Directness.** (b) returns `reservation_id` by `batch_id` with no per-account list scan;
   (a) requires fetching an account's full reservation list and filtering in memory (the
   `transactionRef` server-side filter is still not implemented — ADR-018).
2. **Observability is a first-class want for a chaos harness.** The manual wizard's progress
   panel and the automatic runner's run-results both benefit from the ledger's **derived
   batch status and counters** (`INITIATED`/`IN_PROGRESS`/`COMPLETED`/`FAILED`/
   `PARTIALLY_COMPLETED`). Only (b) returns these; (a) returns reservation-level
   capture/release amounts but not the batch counters.
3. **Reuse.** Both options reuse the existing `ledgerproxy` `RestClient` read-through
   (timeouts + retries + circuit breaker), add no tables, no Kafka, no persistence.

## Decision

**Source the batch `reservation_id` — and live batch progress — by polling a thin
read-proxy of the ledger's batch-summary endpoint (b), with a timeout and a fallback.**

### Read-proxy (reuse the Phase 012/014 machinery)

```
GET /api/v0/ledger/disbursement-batches/{batchId}
  → LedgerReadController → LedgerClient.getDisbursementBatch(token, batchId)
  → ledger GET /api/v0/disbursement-batches/{batchId}
  → DisbursementBatchSummaryDto { batchId, reservationId, status, currency,
        itemCount, processedCount, failedCount, pendingCount,
        totalPrincipalAmount, totalFees, totalAmount,
        amountCaptured?, amountReleased?, createdAt }
```

A small `BatchReservationLookup` service wraps "poll until the reservation id is present or a
timeout elapses", reusing the existing `chaos.ledger.reservation.poll.*` config
(interval/timeout) so **both** consumers share it:

- **Manual wizard (frontend):** after publishing the reservation, poll the proxy by
  `batch_id`; prefill the real `reservation_id` into the item forms when it arrives, and
  drive the progress panel from the same response. On timeout, surface a manual-entry field.
- **Automatic runner (backend):** call `BatchReservationLookup` server-side between the
  reservation publish and the item loop, with the same timeout; on timeout fall back to an
  **autogen placeholder** `reservation_id` (the ledger ignores it, so the run still
  completes) and record that the placeholder was used. The runner may also read the summary
  at finalize time to stamp the run with the ledger's terminal batch status.

The `ReservationResponse`/account-reservations path (a) is retained as the documented
**fallback** if the batch-summary endpoint is unavailable; the in-process filter there
already understands `disbursementBatchId`.

## Consequences

**Positive**

- Real `reservation_id` by `batch_id` in one call, no per-account scan, via the proxy
  pattern already in the codebase (Phase 004/012/014) — no new inbound Kafka, store, or
  migration.
- **Bonus observability:** the same endpoint feeds the wizard's progress panel and lets the
  automatic run-results show the **ledger-side** batch outcome (status + counters), not just
  the chaos machine's publish tallies — valuable when probing partial-completion behaviour.
- One `BatchReservationLookup` serves both the interactive and unattended paths; graceful
  degradation (placeholder / manual entry) because the field is ledger-ignored.

**Negative / trade-offs**

- A second reservation-lookup service alongside ADR-018's `ReservationLookup` (single-flow,
  keyed by `transaction_id` against the account-reservations endpoint). Mitigated by sharing
  the poll config and the same `RestClient`; the two differ only in endpoint + key, and the
  split keeps each lookup's contract clear.
- Depends on the ledger exposing `GET /api/v0/disbursement-batches/{batchId}` with the
  documented shape — **confirm path/params/response before release** (the field is isolated
  in one `LedgerClient` method, adjustable in one place; a WireMock test pins it).
- The unattended placeholder fallback can publish a `reservation_id` that matches no real
  reservation. Acceptable: the ledger keys off `batch_id`, and the run records the fallback.
