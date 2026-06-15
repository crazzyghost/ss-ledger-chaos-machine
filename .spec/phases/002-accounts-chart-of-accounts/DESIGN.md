# Phase 2 - Accounts & Chart of Accounts

## Summary
Give the chaos machine its account model: a **chart of accounts** of friendly system
**account roles** (bootstrapped on startup), a **virtual-account registry** of all VAs it
knows about (system + organization), **flow-slot configuration** that decides which account
fills each slot of each transaction flow, and the ability to **create virtual accounts via
API and via Kafka**. This is the data the Phase 003 flow engine resolves against.

## Motivation
The MANIFEST requires a preconfigured chart of accounts that bootstraps on startup, the
ability to configure which accounts are used in which flows, and VA creation through both API
and Kafka. Without this, flow requests would have to hand-specify every `source_va_id` /
`destination_va_id`; with it, operators reference roles ("Platform Float", "Settlement
Account") and the engine fills in the UUIDs.

## User-Facing Changes
- `GET/PUT /api/v0/chart-of-accounts` — view/edit account roles and their default VA ids/codes.
- `GET/PUT /api/v0/flow-configs` — configure which role fills each slot of each flow.
- `GET/POST /api/v0/virtual-accounts` — list/create VAs; create can also emit the Kafka event.
- `POST /api/v0/virtual-accounts/{id}/publish` — (re)announce a VA to the ledger via Kafka.

## Architecture Impact
Adds `com.softspark.chaos.account` (controller/dto/service/repository/model/enumeration/
bootstrap). Defines SQLite tables `account_role`, `virtual_account`, `organization`,
`flow_slot_config`. Establishes the resolution contract consumed by Phase 003 and the read
shapes surfaced by Phase 005 pages.

## Edge Cases
- Bootstrap idempotency on restart (upsert by role/code; never duplicate).
- MANIFEST code fixes: `PROVIDER_FEE` → `REVENUE.PROVIDER.FEE`; `PLATFORM_FLOAT_MTN` →
  `ASSET.PLATFORM.FLOAT.MTN` (see ARCHITECTURE §10).
- Org VA referencing a non-existent organization → create-or-link organization first.
- VA "create via Kafka" has no dedicated topic → modeled via onboarded / va.updated.
- Editing a role's default VA while flows reference it (config points at role, resolved at send time).

## Testing Strategy
- Bootstrap test: fresh DB seeds exactly the six roles + their system VAs + default flow slots; rerun is a no-op.
- Service tests for CoA edit, flow-slot config, VA create (API) with/without Kafka emission.
- Integration test (Testcontainers Kafka): VA create-via-Kafka publishes a valid
  `organization.onboarded` / `organization.va.updated` envelope.

## Deployment Strategy
Bootstrap seed shipped as config (`chaos-bootstrap.yml`) + Flyway tables. Idempotent, so
redeploys/restarts are safe. No flag.

## Tasks
- [001 - Chart of accounts bootstrap](001-chart-of-accounts-bootstrap.md)
- [002 - Chart of accounts configuration API](002-chart-of-accounts-configuration-api.md)
- [003 - Virtual account registry & creation via API](003-virtual-account-registry-and-creation-via-api.md)
- [004 - Virtual account creation via Kafka](004-virtual-account-creation-via-kafka.md)

## Parallel Tasks
001 first (defines model + seed). Then **002** and **003** in parallel; **004** depends on 003
and the Phase 001 publisher.
