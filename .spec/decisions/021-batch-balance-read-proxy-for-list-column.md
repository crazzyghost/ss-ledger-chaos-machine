# ADR 021 - VA list total-balance column via a batch-balance read-proxy

## Status

Accepted

## Context

Phase 015 (idea `008_balance_display.md`) adds a **Total balance** column to the virtual-account
**list** views — both list tabs: the **Ledger** tab (backed by `GET /api/v0/ledger/accounts`) and
the **Chaos Machine** tab (backed by `GET /api/v0/virtual-accounts`). Neither listing endpoint
returns a balance: the chaos `VirtualAccountResponse` is metadata-only (the VA table is a
projection of `ledger.account.created`, with no balance fields), and the ledger's
`GET /api/v0/accounts` list is verified to return account metadata only. Balance is always a
**separate** read in the ledger.

A page shows up to `perPage = 20` rows. To render a balance per row we need balances for the page's
account ids. Options considered:

1. **Per-row single-balance calls from the UI** (`GET /ledger/accounts/{id}/balance` × N). Rejected
   — N+1 fan-out per page (20 requests), bursty load on the ledger during chaos runs, and 20
   independent loading/error states to manage.
2. **Backend enriches the list response with balance.** Rejected — it couples a metadata listing to
   volatile ledger balance reads, forces server-side fan-out (or a batch call) inside the listing
   path, pollutes the VA projection / list DTO with non-owned data, and breaks the transparent
   read-through posture ([ADR-003](003-backend-as-single-api-gateway.md)). Balance and metadata also
   have different freshness, so welding them into one DTO is misleading.
3. **Proxy the ledger's batch-balance endpoint; the UI issues one batch call per page and merges
   client-side.** (chosen) The ledger already exposes
   `GET /api/v0/balances?accountId=<uuid>&accountId=<uuid>…` (1–100 ids, configurable cap), returning
   one item per id with an explicit per-item status (`FOUND` / `NOT_FOUND` / `FORBIDDEN`) plus
   `availableBalance`/`pendingBalance`/`reservedBalance`/`totalBalance`/`currency`/`balanceAsOf`.
   Page size (20) is comfortably under the 100 cap, so one batch call covers a full page.

## Decision

Add a thin `GET /api/v0/ledger/balances` read-proxy that mirrors the ledger's batch-balance
endpoint — accepting repeated `accountId` params and forwarding them verbatim — reusing the existing
`ledgerProxyRestClient`, per-request bearer-token forwarding, the `CircuitBreaker`, and the standard
`4xx/5xx` translation. It returns the ledger's per-account balance items (a list of
`BatchBalanceItemDto`, each carrying `accountId`, `status`, `currency`, the four buckets, and
`balanceAsOf`).

The **list endpoints stay unchanged** (metadata-only). The frontend collects the current page's
account ids (works for both tabs — a chaos VA's `vaId` **is** its ledger account id, since the ledger
owns VAs, [ADR-011](011-ledger-owned-virtual-accounts-via-kafka-consumer.md)), issues **one** batch
call, builds an `accountId → totalBalance` map, and renders the column. The column shows
**current** total balance (the batch endpoint is current-only; no `asOf`) — point-in-time stays a
detail-page concern ([ADR-020](020-as-of-balance-via-ledger-read-proxy.md)), matching the idea, which
only asks for PIT on the detail view.

## Consequences

**Positive**
- One extra round-trip per page instead of N — bounded, predictable load on the ledger.
- List endpoints stay metadata-only and transparent; the VA projection is not polluted with
  non-owned, volatile balance data ([ADR-003](003-backend-as-single-api-gateway.md)).
- Reuses all existing proxy machinery — no new RestClient, circuit breaker, table, or Kafka surface.

**Negative / trade-offs**
- One additional request per page and a client-side merge; a row's metadata and its balance are two
  reads with independent freshness (eventual visual consistency — acceptable for an ops console).
- Per-item `NOT_FOUND` / `FORBIDDEN` (e.g. a freshly-requested chaos VA not yet created in the
  ledger, within the post-create poll window) must render gracefully as `—`, not an error.
- The contract assumes `perPage ≤` the ledger batch cap (100). The current cap (20) is safe; if a
  caller raises `perPage` above the ledger cap the frontend must chunk the ids — noted as a bounded
  assumption.
- The batch endpoint returns the ledger's paged `ApiResponse` envelope (`{data, page, pageSize,
  total, pages}`); the proxy unwraps `data[]` to a flat list for a trivial client merge — a small,
  documented transform rather than a verbatim envelope passthrough.
