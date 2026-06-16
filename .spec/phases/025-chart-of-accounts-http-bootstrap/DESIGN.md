# Phase 025 - Chart of Accounts HTTP Bootstrap

> **Supersedes** the bootstrap mechanism originally sketched in
> [Phase 002 / Task 001](../002-accounts-chart-of-accounts/001-chart-of-accounts-bootstrap.md).
> Phase 002 shipped with VA UUIDs *seeded from config*. This phase replaces that: the chaos
> machine now **provisions system accounts in the ledger over HTTP** and stores the
> **ledger-assigned** VA ids in the chaos DB. Config remains the source of *account definitions*
> (codes, names, categories) but **not** of VA ids.

## Summary
On startup the chaos machine reads its system-account catalog from YAML (as today), validates
that all account codes are **unique**, then issues HTTP `POST /api/v0/accounts` requests to
`ss-ledger-service` to create each SYSTEM account. The ledger's response carries the
authoritative `accountId` (the virtual-account UUID); the chaos machine persists each
`role → accountId` mapping (plus code/category/currency) into the chaos DB. The result is the
same chart-of-accounts registry later phases resolve against — but its VA ids are real ledger
ids, not invented ones, so published flows reference accounts the ledger actually knows.

## Motivation
Flows must reference VA ids the ledger recognizes. Seeding random/config UUIDs (the old Phase
002 approach) produced ids the ledger had never created, so transaction flows targeting system
accounts could fail or auto-vivify inconsistent accounts. Driving account creation through the
ledger's own API makes the chaos machine's chart of accounts a faithful mirror of the ledger's
SYSTEM accounts and removes UUID drift between the two services.

## User-Facing Changes
- Bootstrap is transparent (runs at startup). New operational endpoints:
  - `POST /api/v0/chart-of-accounts/bootstrap` — re-run provisioning on demand (idempotent).
  - `GET /api/v0/chart-of-accounts` now returns ledger-assigned `vaId` per role + a
    `provisioning_status` (`PROVISIONED | PENDING | FAILED`).

## Architecture Impact
Adds `account/bootstrap` (config model + validation), a `ledgerproxy`-adjacent
`LedgerAccountProvisioningClient` (reuses the Phase 004 `RestClient`/resilience), and a
`ChartOfAccountsBootstrapRunner`. Changes the meaning of `account_role.default_va_id`: now
populated from the ledger, with a `provisioning_status`. No change to how *later* phases read
the registry — only to how it is filled.

## Edge Cases
- **Idempotent re-runs:** a role already provisioned (locally or in the ledger) must not create
  a duplicate; resolve the existing account by code and reconcile the id.
- **Ledger 409 (account code exists):** treat as "already provisioned" → look up + adopt its id.
- **Parent accounts:** hierarchical codes (e.g. `ASSET.PLATFORM.FLOAT.MTN`) may require the
  parent (`ASSET.PLATFORM.FLOAT`) to exist first → provision in dependency order and pass
  `parentAccountId`.
- **Ledger unreachable at startup:** app still boots; roles stay `PENDING`; a scheduled/manual
  retry completes provisioning. Flows that need an unprovisioned system VA fail clearly.
- **Duplicate/blank account codes in config:** startup-time validation fails fast.
- **Partial success:** some roles provisioned, others failed → per-role status; retry only the failures.

## Testing Strategy
> Test *implementation* is consolidated in [Phase 006](../006-testing-and-verification/DESIGN.md).
> This section states intent; the work items live there.
- Unit: config validation (unique codes, required fields), dependency ordering, response mapping.
- Integration: WireMock ledger — happy path, 409-adopt-existing, 5xx/timeout retry, partial
  failure + reconcile; verify chaos DB holds ledger-returned ids.

## Deployment Strategy
`ledger.base-url` + account-creation path via env (shared with Phase 004). Bootstrap is
idempotent and safe to re-run on every deploy. A feature toggle
`chaos.bootstrap.provision-on-startup` (default `true`) allows disabling auto-provisioning in
environments where the ledger seeds its own accounts.

## Tasks
- [001 - System account catalog & validation](001-system-account-catalog-and-validation.md)
- [002 - Ledger account provisioning client](002-ledger-account-provisioning-client.md)
- [003 - Bootstrap orchestration & persistence](003-bootstrap-orchestration-and-persistence.md)

## Parallel Tasks
001 first (defines the config model). **002** (HTTP client) and **003** (runner + persistence)
can then proceed largely in parallel, integrating at the runner.
