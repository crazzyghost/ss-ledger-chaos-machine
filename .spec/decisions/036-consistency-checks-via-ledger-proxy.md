# ADR 036 - Consistency checks via ledger proxy (the chaos machine owns no checks)

## Status

Accepted

## Context

Idea `017_consistency_checks.md` asks the chaos machine to surface **ledger consistency checks** —
an operator-facing capability that triggers and inspects invariant controls (balance integrity, entry
balance, sequence integrity) the ledger runs internally. The question is whether the chaos machine
should *own* consistency-check logic or *proxy* it.

The ledger already implements consistency checks across its reporting Phase (currently under
development, fully wired in the service):

- **`PUT /api/v0/consistency-checks?type=`** triggers one or all check types (`ALL` = default).
  Returns **201** with the created check ids and initial `PENDING` status. Each type runs
  independently; operator runs create new rows regardless of prior runs (no idempotency window).
- **`GET /api/v0/consistency-checks`** lists checks with optional filters (`type`, `status`,
  `initiatorType`), paged, newest-first.
- **`GET /api/v0/consistency-checks/{checkId}`** retrieves a single check run.
- **`GET /api/v0/consistency-checks/{checkId}/discrepancies`** pages findings (optional `code` filter).
- A Postgres-backed **task queue** (ledger ADR 073, shared with export jobs) asynchronously executes
  checks on virtual threads, records findings to `consistency_check_discrepancy`, and transitions the
  check to `COMPLETED` or `FAILED`.
- **`ledger.reconciliation.mismatch`** Kafka event published when a `COMPLETED` check has
  `discrepancyCount >= 1`. The event carries check metadata but never embeds findings — callers poll
  the discrepancies endpoint.

The ledger owns the journals, accounts, balances, and entries — the very things the consistency
checks verify. Only the ledger can authoritatively detect integrity violations.

Options considered:

1. **Build consistency checks in the chaos machine.** Rejected. It would duplicate the ledger's own
   logic, replicate the task queue, introduce a second verification surface that could disagree with
   the ledger's, and require the chaos machine to read ledger internals over HTTP or Kafka. A test
   harness must never compute its own notion of correctness when the system under test already has one.
2. **Proxy the ledger's consistency-check API.** Chosen.
3. **Only consume the mismatch event; do not proxy the trigger.** Rejected. The operator needs
   on-demand control ("run a check now after this chaos scenario") rather than waiting for the
   scheduled system run. The event alone would leave them unable to trigger.

## Decision

**Consistency checks are a thin proxy over the ledger's API. The chaos machine owns no check logic,
stores no findings, and computes no invariants.**

This follows the same pattern as [ADR-015](015-trial-balance-via-ledger-read-proxy.md) (trial
balance proxy), [ADR-033](033-account-statements-via-ledger-export-proxy.md) (statement exports), and
[ADR-032](032-ledger-transactions-account-scoped-view.md) (journal entries): the capability lives in
the ledger; the chaos machine gives its operator a way to reach it; the UI keeps talking only to the
chaos backend ([ADR-003](003-backend-as-single-api-gateway.md)).

Concretely:

- The proxy lives in a new **`consistencycheck`** package — the first domain package that is
  *entirely* a read-proxy with no local persistence, no Flyway migration, no service layer beyond the
  proxy client (in `ledgerproxy`), and no Kafka producer. Its single responsibility is surface the
  ledger's endpoints at `/api/v0/ledger/consistency-checks`.
- **Four endpoints are proxied at full parity**: trigger (`PUT`), list (`GET`), get single
  (`GET /{checkId}`), and list discrepancies (`GET /{checkId}/discrepancies`). The chaos machine
  forwards the operator's bearer token exactly as it does for all ledger proxies. The ledger enforces
  `ledger_consistency_check:view::allow` and `ledger_consistency_check:create::allow` (or super-user
  `*:*::allow`).
- The chaos machine **additionally consumes `ledger.reconciliation.mismatch`** and projects it to a
  local `reconciliation_mismatch` table. This is the fifth ledger-outbound consumer (after
  `ledger.account.created`, `ledger.transaction.failed`, `ledger.balance.updated`,
  `ledger.reservation.{created,released}`). When a check completes with findings, the chaos UI
  **toasts immediately** rather than relying on client-side poll. The event is never displayed
  directly; it is a projection key that prompts a fetch of the full check detail via the proxy.
- The frontend adds a **"Consistency Checks"** item to the **Ledger** nav group (after *Transactions*
  and *Trial Balance*), pointing to `/ledger/consistency-checks`. It is a tabbed page: **All Checks**
  (list), **Detail** (single check + discrepancies), following the same modal + tabbed detail pattern
  as Phase 020 (DLQ), Phase 022 (Statements), and Phase 019 (Reservations).
- Check type, status, initiator type, and discrepancy code are the **ledger's enums**, not chaos
  enums. The chaos machine forwards them unchanged and never validates them client-side — the ledger
  returns 400 on bad input.

## Consequences

**Positive**

- The feature is small: a client, DTOs, a controller (proxy), a Kafka consumer, one table (mismatch
  projection), one UI page. No local check logic. No migration for check/discrepancy tables — those
  are the ledger's.
- Findings are **authoritative by construction** — they are the ledger's own records. The chaos
  machine cannot drift from the ledger, because it never computes anything.
- The operator can **trigger a check on demand** ("run ENTRY_BALANCE after this hostile lifecycle
  flow") and immediately see the results, which is the chaos use case. The event-driven toast ensures
  they are notified if a check finds a problem without polling.
- Exercising the check path is itself chaos value: the operator can run a hostile scenario and
  immediately trigger all checks, which drives the ledger's task queue, invariant verifiers, and
  discrepancy recorders — surface that normally only runs on schedule.
- Type/status/discrepancy code changes on the ledger side arrive for free.

**Negative / accepted trade-offs**

- **A hard runtime dependency on a ledger build that has the consistency-check API.** The API is
  currently under development. Against a ledger without it, every check call 404s. The UI degrades to
  an explicit "consistency checks are unavailable on the connected ledger" state rather than
  pretending; the deployment dependency is called out in the phase DESIGN.
- **The check only completes if the ledger's task worker is running.** With
  `ledger.tasks.worker.enabled=false` (its default), a `PUT` succeeds and the check sits `PENDING`
  **forever** — no error, no timeout. The chaos UI must say so rather than spin indefinitely. This is
  shared with Phase 022 (statement exports) and is a known operational constraint.
- The chaos machine has **no local history** of checks: the list is the ledger's, system-wide. There
  is no per-chaos-run "checks I triggered from this run" view, and no record survives a ledger reset.
  Judged correct — the check *is* a ledger resource — but it is a real difference from every other
  chaos list view, which is backed by a local projection. The `reconciliation_mismatch` table is a
  toast/notification projection only; it is not a full check history.
- **No cancel endpoint.** The ledger's consistency-check API does not currently expose
  `DELETE /{checkId}` (unlike exports). If a long-running check needs cancellation, the operator must
  stop the ledger's task worker. This is a ledger API gap, not a chaos omission.
- Findings **detail JSONB** is passed through as-is from the ledger — no re-shaping. The chaos UI
  renders it as a formatted JSON panel, which is sufficient for operator diagnosis. If
  domain-specific discrepancy renderers are ever wanted, they belong in the ledger's API, not here.

**Left open (deliberately)**

- The `ledger.reconciliation.mismatch` event is consumed as a **projection for toast notification**.
  Its purpose is to wake the operator; the actual check detail is fetched via the proxy. This
  asymmetry is intentional — the event is the trigger; the HTTP API is the source of truth. If the
  ledger adds more events (per-check-started, per-check-failed), the chaos machine can consume those
  too for richer toast narratives, but the proxy is the primary contract.
- The chaos machine does **not** render a consistency-check report or dashboard. That is the ledger's
  concern. The chaos machine only surfaces the API the ledger already has and toasts the mismatch
  event. If a cross-run summary or trend view is ever wanted, it should live in the ledger's own UI,
  not here.
