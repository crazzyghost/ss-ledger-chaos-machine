# ADR 033 - Account statements via a ledger export proxy (the chaos machine renders nothing)

## Status

Accepted

## Context

Idea `010_account_statements.md` asks for **downloadable account statements** — a per-virtual-account
statement of ledger movements over a date range, exported as a file.

The obvious reading is that the chaos machine builds statements: walk
`GET /accounts/{id}/transactions` (cursor-paginated), fetch an opening balance via
`GET /accounts/{id}/balance?asOf=`, render CSV and PDF, stream the bytes back. That would mean a new
`statement` package, an OpenPDF (or iText) dependency, a `CSVPrinter` writer, layout code, a
page-walking assembler, and — because a wide window cannot be rendered inside the 30 s ledger read
timeout — an async job table and a runner. It is the largest thing the chaos machine would own that
has nothing to do with chaos.

It is also **already built, in the right place.** `ss-ledger-service`
(branch `feature/account-statement-exports`, worktree `ss-ledger-service-beta`) ships account
statement exports across its Phases 030 / 031:

- `PUT /api/v0/accounts/{accountId}/transaction-exports?format=&rangeType=&from=&to=` creates an
  export job (**201**), or joins the one already active for the same resolved window and format
  (**200** — idempotency window, ledger ADR 074).
- A Postgres-backed task queue (ledger ADR 073) claims the job on a virtual thread, assembles the
  statement from the journal history repository, renders it (**OpenPDF** for PDF,
  **Jackson CSV** for CSV — ledger ADR 035), and stream-uploads it to **S3** (ledger ADR 071).
- `GET .../{exportId}` returns the export's status and, once `COMPLETED`, a **freshly presigned S3
  download URL** (default TTL `PT15M`, minted per read, never persisted).
- `GET .../` lists an account's exports (offset-paged, `status`/`format` filters).
- `DELETE .../{exportId}` cancels a `PENDING`/`IN_PROGRESS` export (**409** once terminal).

The ledger owns the journal, the running-balance witnesses (`total_balance_before` — ledger ADR 060)
that a statement's brought-forward column needs, and the account. It is the only component that can
produce an authoritative statement. The chaos machine is, by
[ADR-003](003-backend-as-single-api-gateway.md), a **driver + gateway** — it owns no journals and no
balances.

Options considered:

1. **Build statements in the chaos machine.** Rejected. It duplicates a capability the ledger
   already has, requires a PDF dependency + renderer + async job substrate the chaos machine does
   not otherwise need, and would produce statements that could silently *disagree* with the
   ledger's own — the exact class of bug a ledger test harness must not introduce. It also has to
   reconstruct opening balances from the running-balance witnesses, reimplementing ledger logic
   against a contract that is not its to own.
2. **Proxy the ledger's export API.** Chosen.
3. **Skip the feature.** Rejected — the operator needs statements to eyeball what a chaos run did to
   an account, and the ledger's export path is itself a surface worth exercising (it is a real
   consumer of the journal the chaos machine has been filling with deliberately hostile traffic).

## Decision

**Account statements are a thin proxy over the ledger's export API. The chaos machine renders no
statements, stores no exports, and adds no PDF/CSV dependency.**

This is the [ADR-015](015-trial-balance-via-ledger-read-proxy.md) pattern applied again: the
capability lives in the ledger; the chaos machine gives its operator a way to reach it, and the UI
keeps talking only to the chaos backend ([ADR-003](003-backend-as-single-api-gateway.md)).

Concretely:

- The proxy lives in the existing **`ledgerproxy`** package — no new backend package, no new table,
  no Flyway migration, no Kafka.
- It is exposed under `/api/v0/ledger/accounts/{accountId}/transaction-exports`, mirroring the
  ledger's own path so the mapping stays obvious. Because it carries **`PUT` and `DELETE`** — the
  first *commands* the proxy has ever issued — the endpoints live in a **new
  `LedgerExportController`** rather than in `LedgerReadController`, which stays read-only and
  honestly named.
- The chaos machine forwards the operator's bearer token as it does for every other proxied call
  (`ledger.proxy.forward-token`, default `true`). The ledger enforces
  `ledger_account_transactions:export::allow` (or super-user `*:*::allow`) and its own org scope.
- The four ledger endpoints are proxied at **full parity** (create / poll / list / cancel), plus one
  **chaos-only** endpoint — `GET .../{exportId}/download` — which streams the artifact bytes so the
  presigned S3 URL never reaches the browser. That download endpoint is the subject of
  [ADR-034](034-gateway-proxied-artifact-download.md).
- Statement content, formats (`CSV`/`PDF`), range semantics (`DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY`/
  `CUSTOM`, half-open `[from, to)`, UTC-calendar-snapped, capped at 366 days), the idempotency
  window, and every validation rule are the **ledger's** contract. The chaos machine re-validates
  none of it and re-implements none of it — it forwards, and it surfaces what comes back
  (see [ADR-035](035-faithful-status-propagation-on-ledger-command-proxy.md)).

## Consequences

**Positive**

- The feature is small: a client, DTOs, one controller, one streaming endpoint, one UI tab. No
  migration, no table, no consumer, no runner, no new backend dependency.
- Statements are **authoritative by construction** — they are the ledger's own bytes. The chaos
  machine cannot drift from the ledger, because it never computes anything.
- Exercising the export path is itself chaos value: the operator can run a hostile flow and then
  ask the ledger to produce a statement over the same window, which drives the ledger's journal
  reads, renderers, task queue, and S3 upload with the traffic the harness just injected.
- Format/range/validation changes on the ledger side arrive for free.

**Negative / accepted trade-offs**

- **A hard runtime dependency on a ledger build that has the export API.** The API is currently on
  an **unmerged branch** (`feature/account-statement-exports`). Against a ledger without it, every
  statement call 404s. The UI degrades to an explicit "statement exports are unavailable on the
  connected ledger" state rather than pretending; the deployment dependency is called out in the
  phase DESIGN. This is a real coupling and the main cost of the decision.
- **The export only completes if the ledger's task worker is running.** With
  `ledger.tasks.worker.enabled=false` (its default), a `PUT` succeeds and the export sits `PENDING`
  **forever** — no error, no timeout. The chaos UI must say so rather than spin indefinitely.
- Statements on **SYSTEM accounts** (the whole chart of accounts — `PLATFORM_FLOAT`,
  `SETTLEMENT_ACCOUNT`, …) resolve to **super-user-only** through the ledger's org-scope seam. An
  operator whose token is merely org-scoped gets a 403 on exactly the accounts a chaos operator most
  wants to inspect. Nothing the chaos machine can fix — it must surface the 403 legibly.
- The chaos machine has **no local history** of exports: the list is the ledger's, per account.
  There is no cross-account "all my statements" view, and no record survives a ledger reset. Judged
  correct — the export *is* a ledger resource — but it is a real difference from every other chaos
  list view, which is backed by a local projection.
- Statement **content is not customizable** by the chaos machine (no branding, no chaos-specific
  columns like the originating run or correlation id). If that is ever wanted, it belongs in the
  ledger's renderer, not here.
- No XLSX. The ledger renders `CSV` and `PDF` only (no Apache POI anywhere), so neither does the
  chaos machine.

**Left open (deliberately)**

- The ledger's **`ledger.statement.export.completed`** Kafka event (its Phase 031.5) is **not
  implemented** — its contract with the Notification Service is unfrozen and its flag defaults off.
  When it lands, the chaos machine can consume it as a **fifth inbound consumer** (a natural Part 5
  of the [Phase 017](../phases/017-ledger-transaction-failure-events/DESIGN.md)–
  [020](../phases/020-dead-letter-queue-views/DESIGN.md) series), replacing this phase's client-side
  poll with a pushed toast. This phase's poll-and-download path stays the primary contract either
  way — that is the ledger's own stance — so nothing here is wasted when the event arrives.
