# ADR 008 - Organization Onboarding Domain Model

## Status
Accepted

## Context
Phase 002 modeled an organization as a single denormalized `organization` table
(`organization_id`, `name`, `type_name`, `country_name`, `country_iso_code`, `status`). Rows were
created opportunistically as a side effect of virtual-account creation
(`VirtualAccountService.createVirtualAccount`), and the `type_*`/`country_*` fields were plain
strings with no backing reference data — populated from hardcoded defaults (`MERCHANT`/`Merchant`,
`GHA`/`Ghana`) in `VirtualAccountAnnouncer`. There were **no** Country or OrganizationType tables.

Idea `001_country_org_type_org_config.md` requires first-class onboarding master data:

- **Country** — `id`, `name`, `iso_code`, `status`, `modified_date`.
- **Organization Type** — `id`, `name`.
- **Organization** — `id`, `name`, `organization_type_id` (FK), `country_id` (FK),
  `primary_contact_email`, `phone_numbers` (jsonb), `status`, `created_at`, `updated_at`.

The `organization.onboarded` event the ledger consumes (authoritative sample:
`../ss-ledger-service/bin/publish-organization-onboarded.sh`) embeds the **country and type by
value** — `data.type = {id, name}`, `data.country = {id, name, iso_code, status, modified_date}`.
So onboarding needs both relational integrity (real FKs to real reference data) *and* a faithful
point-in-time value snapshot at event time.

Forces:
- The event embeds names/iso/status that could later drift if a country is renamed or deactivated.
- SQLite (the persistence engine, [ADR-002](002-sqlite-persistence-with-jpa-and-flyway.md)) has no
  native `jsonb`; the existing `payload_json` precedent stores JSON as `TEXT`.
- The current opportunistic org-creation path (during VA creation) must keep working.

## Decision
Introduce **`country` and `organization_type` as first-class tables** and **refactor `organization`
to reference them by FK while retaining denormalized snapshot columns**:

- New `country` table: `country_id` (PK), `name`, `iso_code`, `status`, `modified_date`,
  `created_at`, `updated_at`. `iso_code` is **not** constrained to 3 chars (the ledger sample uses
  ISO 3166-1 **alpha-2**, `GH`); store as `TEXT` and validate length 2–3 at the API.
- New `organization_type` table: `organization_type_id` (PK), `name`, `created_at`, `updated_at`.
- `organization` gains: `organization_type_id` (FK → `organization_type`), `country_id`
  (FK → `country`), `primary_contact_email`, `phone_numbers` (`TEXT`, JSON array). It **keeps**
  `type_name`, `country_name`, `country_iso_code` as **snapshot** columns and adds
  `country_status` + `country_modified_date` snapshots so the emitted event reflects the
  reference-data state *at onboarding time*, independent of later edits.
- `phone_numbers` (a `List<String>`) is persisted as a JSON string via a JPA `AttributeConverter`
  (`JsonStringListConverter`), not a native JSON column — consistent with SQLite and the existing
  `InstantStringConverter` / `payload_json` patterns. No new Hibernate JSON dependency.

The org create path validates that the referenced `country_id` and `organization_type_id` exist,
copies their current values into the snapshot columns, and persists the org. The legacy
VA-driven create-on-demand path remains (FKs and contact fields nullable for those rows).

Identifier strategy for these three tables is **UUID v4** — recorded separately in
[ADR-010](010-uuid-v4-ids-for-organization-domain.md). Event publication is via a transactional
outbox — [ADR-009](009-transactional-outbox-for-organization-onboarded.md).

## Consequences
**Positive**
- Reference data is managed, deduplicated, and referentially enforced rather than free-typed.
- The emitted event is a faithful point-in-time snapshot; renaming a country later does not
  retroactively rewrite already-onboarded orgs' events.
- No new dependency: JSON list storage reuses the established `AttributeConverter` pattern.
- The `country.status` + `country.modified_date` snapshots make the event match the ledger
  contract exactly (closing the gap where the current `Country` event record omits them).

**Negative / trade-offs**
- Snapshot columns duplicate reference data — the org row and the live `country`/`type` rows can
  diverge by design; consumers must understand "snapshot at onboarding," not "current value."
- A schema migration (`V5`) alters the shipped `organization` table (adds nullable columns) and
  adds two tables; existing rows have null FKs/contact fields until backfilled.
- JSON-in-`TEXT` is opaque to SQL queries; phone numbers are not independently indexable/queryable
  (acceptable — they are event payload, not a query dimension).
- Two write paths now create organizations (onboarding API + VA announcer); their invariants must
  stay compatible (nullable FKs tolerated).
