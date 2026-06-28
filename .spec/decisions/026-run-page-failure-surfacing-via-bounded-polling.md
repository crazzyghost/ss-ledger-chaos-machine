# ADR 026 — Surface ledger transaction failures on the run page via bounded scoped polling + toast

## Status
Accepted — 2026-06-27 — Phase [017 — Ledger Transaction-Failure Events & Correlation](../phases/017-ledger-transaction-failure-events/DESIGN.md)

Depends on the failures query API and the publish-response request id from
[ADR-025](025-transaction-failure-projection-and-request-id-correlation.md).

## Context

A transaction the operator publishes from **Single Flow Run** can be rejected by the
ledger *after* the publish call returns `200 OK` — the failure travels back over Kafka
(`ledger.transaction.failed`) and is projected asynchronously. The operator, still on the
run page admiring a green "published" result, gets no signal that the ledger threw it out.
The idea asks to "poll … to show a toast if it fails while on the run page … or use SSE
to avoid stressing the server."

Givens that shape the choice:
- The failures query API (`GET /api/v0/transaction-failures`, ADR-025) exists regardless —
  the "Sent" tab needs it. Polling reuses it; SSE would be *additional* surface.
- After publish, the client holds the emitted `transaction_request_id`(s): the
  single-flow value is client-generated, and N-Times values are returned in the response
  (ADR-025). So a poll can be **scoped to exactly those ids** — a tiny, indexed query.
- The app already polls in three places via react-query `refetchInterval` with a stop
  predicate: batch-run status (`batch-run-page`, 1.5 s until terminal), reservation
  lookup (`lifecycle-wizard`, 1.5 s / 15 s timeout), and VA list (`virtual-accounts-page`,
  time-bounded). There is an established, idiomatic pattern to mirror.
- It is a **single-operator** harness, not a fan-out of many concurrent browser clients.
- There is **no toast library** installed (verified `package.json`); only an inline
  `InlineNotice` banner and dialogs.

Options:

- **(a) Bounded scoped polling.** After publish, poll the failures endpoint by the
  emitted request id(s) on a ~1.5 s interval within a bounded window; stop on the first
  hit (→ toast) or when the window elapses. Active only while the run page is mounted.
- **(b) Server-Sent Events.** A streaming endpoint pushes a failure the moment the
  consumer projects one. Needs a server-side event bus from `LedgerTransactionFailedConsumer`
  → per-request `SseEmitter`s, subscription keyed by request id, and connection lifecycle
  (timeouts, cleanup, back-pressure, reconnect).

## Decision

**Adopt (a): bounded, request-id-scoped polling with a toast on hit.** Reject (b) for now
as disproportionate to a single-operator tool — the "server stress" SSE would avoid does
not exist at this scale, and a scoped poll over an indexed key for a bounded window is
cheap. Keep the door open: the failures API is plain REST, so an SSE/WebSocket layer can
be added later **without changing** the query contract or the persistence.

### Behaviour

- **Toaster:** add `sonner` (≈5 kB, the shadcn-convention toaster) and mount `<Toaster/>`
  in the app shell. Chosen over hand-rolling on `InlineNotice` because failures surface
  from a page the operator may have navigated within; a global, non-blocking, stacking
  toast is the right primitive and is one small, well-supported dependency.
- **Single flow:** on a successful publish of a transaction-bearing flow, start a
  react-query poll `GET /transaction-failures?transactionRequestId={id}`
  (`refetchInterval` ≈ 1500 ms, `enabled` while mounted, stop on first result). On a hit:
  fire a **danger toast** (`failure_code` + `failure_reason`, deep-link to the failure
  detail) and reflect the outcome on the `FlowResultCard` ("Failed at ledger"). On window
  elapse (default ≈ 20–30 s, configurable): stop silently.
- **N-Times sync:** poll the batch variant
  (`?transactionRequestIds=…` over the returned id list); toast a summary
  ("k of N failed at ledger") and stop when all are resolved or the window elapses.
- **Scope guard:** only flows that mint a transaction request id arm the poll (the
  catalog label from ADR-025 decides); onboarding / va-updated / other non-transactional
  flows never poll.
- **Honesty about absence:** the toast (and any inline status) must frame a clean window
  as *"no failure observed"*, never *"succeeded"* — failures are asynchronous and a clean
  poll window is not a success proof (see ADR-025; definitive success is Part 2's
  `ledger.balance.updated`).

### Tuning

Interval, window length, and a master enable flag are front-end constants/config
(mirroring `RESERVATION_POLL_INTERVAL_MS` / `_TIMEOUT_MS`), so the cadence is tunable
without a redeploy of the contract.

## Consequences

**Positive**
- Reuses the exact query API the "Sent" tab needs and the exact polling idiom three other
  pages already use — minimal new concepts, no new backend endpoint.
- Scoped to specific request ids over an indexed column for a bounded window → negligible
  load even though it is "polling"; the SSE motivation (server stress) doesn't bite at
  single-operator scale.
- A real, timely signal that a published transaction was rejected, where the operator is
  already looking.
- Reversible: SSE can be layered on later behind the same REST contract if scale ever
  changes.

**Negative / trade-offs**
- Polling has inherent latency (≤ one interval) and a finite window — a failure projected
  *after* the window closes won't toast; it is still visible on the "Sent" tab and the
  failures view. Acceptable; the window is tunable.
- Adds a front-end dependency (`sonner`) and a global `<Toaster/>` mount — small, but a
  new third-party surface in a UI that had none.
- A poll that finds nothing is genuinely inconclusive (in-flight vs succeeded vs
  not-yet-consumed); the UI must avoid implying success, which is a copy/UX discipline
  rather than a guarantee the system can enforce.
