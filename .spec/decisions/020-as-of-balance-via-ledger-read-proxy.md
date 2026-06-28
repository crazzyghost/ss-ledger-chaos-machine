# ADR 020 - Point-in-time ("Balance As Of") balance via the ledger read-proxy

## Status

Accepted

## Context

Phase 015 (idea `008_balance_display.md`) adds a **"Balance As Of"** datetime picker to the
virtual-account detail page and surfaces **all four balance buckets** (Total, Available, Reserved,
Pending). The chaos backend already proxies the ledger's single-account balance at
`GET /api/v0/ledger/accounts/{id}/balance` → `LedgerBalanceDto`
([Phase 004](../phases/004-gateway-auth-ledger-proxy/DESIGN.md)), but two gaps block the feature:

1. **No point-in-time passthrough.** The current proxy reads *current* balance only.
2. **The DTO is drifted from the ledger contract.** `LedgerBalanceDto` carries
   `@JsonNaming(SnakeCaseStrategy)` and an `updatedAt` field. Verified against
   `ss-ledger-service`: its REST DTOs serialize **camelCase** (only its Kafka *event* payloads use
   `@JsonNaming(SnakeCase)`; there is no global snake-case strategy). The ledger's `BalanceResponse`
   is `record BalanceResponse(UUID accountId, String currency, BigDecimal available, BigDecimal
   pending, BigDecimal reserved, BigDecimal total, long lastEntrySequence, LocalDateTime
   balanceAsOf)`. So today the chaos DTO's snake-case mapping makes `accountId` (expects
   `account_id`, ledger sends `accountId`) and `updatedAt` (expects `updated_at`, ledger sends
   `balanceAsOf`) **both silently null** — unnoticed only because neither is rendered. The
   single-word buckets (`available`/`total`/`pending`/`reserved`/`currency`) map under either
   convention, which is why the panel "works."

The ledger **natively supports** as-of queries:
`GET /api/v0/accounts/{accountId}/balance?asOf=<ISO-8601 LocalDateTime>`, where `asOf` is a
`@DateTimeFormat(iso = DATE_TIME) LocalDateTime` validated **not to be in the future**
(`@ValidAsOf`). It reconstructs the snapshot authoritatively from `journal_entry_line`
running-balance witness columns (`running_balance`, `running_reserved_balance`,
`running_pending_balance`) at-or-before the cutoff, falling back to a zero snapshot for a pre-genesis
cutoff. Crucially, `asOf` is a **zoneless `LocalDateTime`**, not an `Instant`.

Options considered:

1. **Thin passthrough of `asOf` + align the DTO to the ledger contract.** (chosen)
2. **Derive PIT inside the chaos machine** from the transaction-history running-balance witnesses it
   already proxies (`runningBalance`/`runningReservedBalance`/`runningPendingBalance`). Rejected —
   it duplicates logic the ledger owns and already exposes, and would drift from the authoritative
   computation.
3. **Snapshot/store balances in the chaos DB.** Rejected outright — the chaos machine owns no
   balances; it is a driver + transparent gateway ([ADR-003](003-backend-as-single-api-gateway.md)).

## Decision

Extend the **existing** single-account balance read-proxy (no new package, RestClient, or circuit
breaker) to:

- Accept an optional `asOf` query param typed as **`LocalDateTime`** with
  `@DateTimeFormat(iso = DATE_TIME)`, and forward it **verbatim** to the ledger's `asOf` param when
  present, omit it when absent. We deliberately use `LocalDateTime` (zoneless wall-clock), **not**
  `Instant` — to match the ledger contract exactly and avoid a UTC conversion that would silently
  shift the operator's chosen wall-clock. The operator's picked datetime is interpreted in the
  **ledger's local zone**. (This differs from Phase 012's trial-balance `from`/`to`, which are
  `Instant`; balance as-of follows the ledger's own typing.)
- **Align `LedgerBalanceDto` to the ledger's camelCase `BalanceResponse`:** remove
  `@JsonNaming(SnakeCaseStrategy)`, rename `updatedAt` → `balanceAsOf`, and add
  `lastEntrySequence`. This also fixes the currently-null `accountId`.

The chaos machine validates nothing about `asOf` semantics and computes no balance — the ledger is
authoritative for both the not-future rule and the historical reconstruction. A future-dated or
otherwise invalid `asOf` surfaces as the ledger's `400`, translated by the existing
`4xx → NotFoundException` proxy path (consistent with [ADR-015](015-trial-balance-via-ledger-read-proxy.md)).

## Consequences

**Positive**
- Point-in-time balance for free and authoritative — no chaos-side computation or storage.
- Fixes a latent contract bug: `accountId` and the as-of timestamp were silently null; after
  alignment both populate (the frontend `LedgerBalanceDto` type already declares `balanceAsOf`).
- Stays within the established read-proxy machinery and the single-gateway topology
  ([ADR-003](003-backend-as-single-api-gateway.md), [ADR-015](015-trial-balance-via-ledger-read-proxy.md)).

**Negative / trade-offs**
- Introduces a **zoneless `LocalDateTime`** contract on the chaos API that differs from the
  trial-balance `Instant` contract. Operators and the frontend must treat "Balance As Of" as a
  wall-clock in the ledger's zone; this is documented as an edge case and covered by tests. If the
  ledger and chaos hosts run different zones, the cutoff is the ledger's — acceptable for an ops
  console, but called out so it is a known, not a surprise.
- The DTO mirrors the ledger field set tightly, so a ledger `BalanceResponse` change can drift the
  mapping — mitigated by a deserialization contract test pinned to a captured camelCase sample.
- Removing `@JsonNaming(SnakeCase)` is a behavior change; verified that no consumer depended on the
  (already-broken) snake fields, since only single-word buckets ever bound.
