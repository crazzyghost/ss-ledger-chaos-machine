# Task 003 - Bootstrap Orchestration & Persistence

## Functional Requirements
- Orchestrate startup provisioning: validate the catalog, provision each SYSTEM account in the
  ledger **in dependency order**, and persist the resulting `role → ledger accountId` mapping
  (with provisioning status) into the chaos DB — idempotently and resiliently.

## Acceptance Criteria
- [ ] On startup (when `chaos.bootstrap.provision-on-startup=true`), each catalog role is
      provisioned via Task 002 and an `account_role` row is upserted with the **ledger-returned
      `vaId`** and `provisioning_status=PROVISIONED`.
- [ ] Parents are provisioned before children; a child's `parentAccountId` is the parent's
      ledger `accountId`.
- [ ] Re-running (restart or `POST /api/v0/chart-of-accounts/bootstrap`) is a no-op for already
      `PROVISIONED` roles and retries only `PENDING`/`FAILED` ones.
- [ ] If the ledger is unreachable, startup still completes; affected roles are `PENDING` and a
      bounded scheduled retry (or manual endpoint) finishes later.
- [ ] A SYSTEM `virtual_account` row is recorded per role so the registry (Phase 002) and flow
      resolution (Phase 003) see real ledger ids.
- [ ] Bootstrap outcome (per-role status) is observable via `GET /api/v0/chart-of-accounts` and
      surfaced in `/actuator/health` (degraded while any role is `PENDING`/`FAILED`).

## Technical Design
`ChartOfAccountsBootstrapRunner` (`ApplicationRunner`, ordered after Flyway):

```mermaid
flowchart TD
  start[Startup] --> val[Validate catalog (Task 001)]
  val --> order[Topological order (parents first)]
  order --> loop{for each role}
  loop --> have{already PROVISIONED in chaos db?}
  have -->|yes| skip[skip]
  have -->|no| prov[provisioningClient.createAccount (Task 002)]
  prov -->|accountId| save[(upsert account_role + virtual_account: vaId, PROVISIONED)]
  prov -->|ledger down| pend[(mark PENDING)]
  save --> loop
  pend --> loop
  loop --> done[health: UP if all PROVISIONED else DEGRADED]
```

Persistence changes vs Phase 002:
- `account_role.default_va_id` ← **ledger `accountId`** (was config UUID).
- New column `account_role.provisioning_status` (`PENDING|PROVISIONED|FAILED`) +
  `provisioned_at`, `last_error` (Flyway migration `V2__account_role_provisioning.sql`).
- One transaction per role (commit incremental progress so a mid-run ledger outage leaves a
  consistent partial state).

Retry: a guarded `@Scheduled` reconciler (or on-demand endpoint) re-attempts non-`PROVISIONED`
roles using the same idempotent client; capped attempts + backoff; logs `role → status`.

## Implementation Notes
- Package `account/bootstrap/ChartOfAccountsBootstrapRunner`, `account/bootstrap/BootstrapReconciler`,
  `account/controller` (`POST /chart-of-accounts/bootstrap`), repository updates for the new columns.
- Reuse Phase 002 entities/repositories; only the *fill* mechanism and two columns change.
- Health contributor `ChartOfAccountsHealthIndicator` reports counts by status.
- Keep the operation idempotent end-to-end: identity is the `accountCode`; the chaos DB caches
  the resolved id.

## Non-Functional Requirements
- Startup not blocked beyond the provisioning time budget; ledger outage degrades, never hangs.
- Incremental commits so progress survives partial failure; re-run resumes cleanly.

## Dependencies
Task 001 (catalog/validation), Task 002 (provisioning client), Phase 001 (persistence),
Phase 002 (registry entities). Logically slots **between Phase 002 and Phase 003**.

## Risks & Mitigations
- *Ledger seeds its own SYSTEM accounts* → toggle `provision-on-startup=false` + adopt-existing
  via lookup-by-code, so the chaos DB still captures the right ids.
- *Schema change on a completed Phase 002 DB* → additive Flyway migration; default existing rows
  to `PENDING` and reconcile on next boot.

## Testing Strategy (intent; implemented in Phase 006)
- Runner: dependency ordering, incremental commit, idempotent re-run, ledger-down → PENDING then
  reconcile → PROVISIONED, health transitions. Integration vs WireMock ledger + temp SQLite.

## Deployment Strategy
Idempotent; runs every boot. Toggle + ledger URL via env. Additive migration. Health shows
provisioning status so deploys surface incomplete bootstraps.
