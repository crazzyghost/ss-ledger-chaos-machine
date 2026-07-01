# ADR 032 - Transactions "Ledger" view re-pointed to the ledger journal-entries export

> Note: the filename slug says "account-scoped"; the accepted decision is a **global,
> time-windowed** browse over the ledger's `reporting/journal-entries` export (operator's "for now"
> choice). The account-scoped proxy is retained only for the per-VA detail view. Title/body are
> authoritative; the slug is kept as a stable identifier.

## Status
Accepted

## Context
Idea `016_unified_scenario_runner.md` states that on the *Transactions* page "the **Ledger tab**
should be the main content (it is currently broken)". This corresponds to idea
`009_transactions_ledger_tab.md`, which was filed but **never designed** (the idea file is an empty
placeholder) — so the Ledger tab was scaffolded in Phase 005 but never made to work.

**Why it is broken (verified against `ss-ledger-service` source):** the chaos backend exposes
`GET /api/v0/ledger/transactions` (a flat, page/size, `vaId`/`eventType`/`correlationId`/time
global list) in `LedgerReadController`, which proxies the ledger's `GET /api/v0/transactions`.
**That ledger endpoint does not exist.** The ledger's `TransactionController` exposes only:

| Ledger endpoint | Shape | Already proxied by chaos as |
|---|---|---|
| `GET /api/v0/transactions/{ref}` | `TransactionReferenceHistoryResponse` (all legs of one txn) | `GET /api/v0/ledger/transactions/{ref}` |
| `GET /api/v0/accounts/{id}/transactions` | `JournalEntryHistoryListResponse` (cursor-paged, rich per-line history) | `GET /api/v0/ledger/accounts/{id}/transactions` → `CursorPageResponse<LedgerTransactionHistoryDto>` |
| `GET /api/v0/disbursement-batches/{batchId}/transactions` | batch-scoped | (out of scope) |

There is **no global "all transactions" list** in the ledger by design — its authoritative
transaction read is **account-scoped** (cursor-paginated journal-entry-line history, witnessing
running balances) or **by-reference**. The flat global proxy was a hand-guessed contract; it
returns errors/empties at runtime. The operator answered the breakage question with **"wrong
ledger endpoint."**

The chaos side **already** has the account-scoped machinery: `LedgerReadController.getAccountTransactionHistory`
(`/ledger/accounts/{id}/transactions`, cursor-paged) returning `LedgerTransactionHistoryDto`
(journal line + parent-entry header + running balances + counterparty legs), plus the by-reference
proxy. The VA detail page's Transactions tab already consumes the account-scoped proxy.

**There is, however, a global endpoint suitable for the standalone page (operator-chosen, "for
now").** The ledger's `ReportingController` exposes `GET /api/v0/reporting/journal-entries` (RPT-003
reconciliation export, paged-JSON mode) → `ReconciliationEntryListResponse` — an offset-paginated
(`data`/`page`/`pageSize`/`total`/`pages`) list of journal-entry **lines** with full multi-leg
context (`ReconciliationEntryRecord`: `lineId`, `journalEntryId`, `postedAt`, `accountId`,
`accountCode`, `organizationId`, `currency`, `direction`, `amount`, running balances,
`transactionRef`, `entryType`, `narrative`, `memo`, `sourceService`, `sourceEventId`, `metadata`,
`siblingLines[]`). It is **not VA-scoped**, so it gives the operator a real cross-account browse.
Its constraints (verified): `from`/`to` are **required** `Instant`s and the window span is **capped**
(default **7 days**) — the ledger `400`s a wider span; optional filters are repeatable `accountId`,
`entryType` (comma-separated), `transactionRef`, `sourceService`; `page`/`size` (max 100). (An
`?format=ndjson` streaming companion exists; the chaos view uses the default paged JSON.) The chaos
side does **not** yet proxy it — the only `/reporting` proxy today is `/reporting/trial-balance`.

## Decision
- **Remove** the phantom global proxy: delete `GET /api/v0/ledger/transactions` from
  `LedgerReadController`, `LedgerClient.listTransactions`, and the `LedgerTransactionDto` it
  returned (it has no real ledger backing).
- **Add a real global proxy** of the ledger's reconciliation export: a new
  `GET /api/v0/ledger/reporting/journal-entries` in `LedgerReadController` that read-proxies the
  ledger's `GET /api/v0/reporting/journal-entries` (paged-JSON mode), mirroring the existing
  `/reporting/trial-balance` proxy machinery. It returns a chaos `PageResponse<ReconciliationEntryDto>`
  (a forward-compatible mirror of `ReconciliationEntryRecord` + its sibling legs), passing through
  the required `from`/`to`, optional `accountId`/`entryType`/`transactionRef`/`sourceService`, and
  `page`/`size`.
- **Re-point** the *Transactions* page's Ledger view to this **global, time-windowed** browse
  (the operator's "for now" choice):
  - it is **not VA-scoped** — it lists journal-entry lines across accounts for a chosen
    **date-range window** (defaulting to a span within the ledger's cap, e.g. the last 7 days),
    with optional account/entry-type/transaction-ref filters;
  - a row click opens the **by-reference** detail (`/ledger/transactions/{ref}` via
    `/transactions/:ref`), reusing the existing transaction detail page.
- **Keep** `/ledger/transactions/{ref}` and `/ledger/accounts/{id}/transactions` (the latter still
  backs the per-VA transactions view on the VA detail page).
- **Make Ledger the main content** of the *Transactions* page: the *Sent (Chaos History)* tab is
  removed here (it moves into the Scenario Runner's Run History,
  [ADR-031](031-run-grouped-history-and-csv-retirement.md)), leaving the page as a single
  global ledger browser rather than a two-tab control.

## Consequences
- **Positive:** the view is backed by a **real, authoritative** ledger endpoint and is a genuine
  **cross-account** browse (no VA required); it surfaces rich journal-line detail (running
  available/reserved/pending balances, multi-leg sibling lines, source service/event) that the flat
  list never could; it reuses the existing `/reporting` proxy machinery; the dead phantom proxy is
  deleted.
- **Negative / trade-offs:**
  - The browse is **time-windowed and span-capped** (required `from`/`to`, default ≤ 7 days) — it
    is **not** an unbounded "all transactions ever" list. The UI must default to and enforce a
    window within the cap (and surface the ledger's `400` if exceeded). This is a reconciliation-
    export endpoint repurposed as a browse ("for now"), so its ergonomics are date-range-first.
  - Results are journal-entry **lines** (one row per leg, with sibling legs attached), not one row
    per transaction — the table groups/links by `transactionRef` and defers the full picture to the
    by-reference detail.
  - It is **offset-paginated** (unlike the account-scoped cursor history), so the two ledger views
    use different pagination models; acceptable, but the table components are not shared.
  - A new chaos proxy endpoint + DTO are added (small, mirrors trial-balance), rather than the pure
    deletion the VA-scoped alternative would have been.
