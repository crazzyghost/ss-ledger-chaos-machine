# Phase 010 - Currencies & Supported Countries

## Summary
Expands organization reference data: introduces a managed **`currency`** table (UUID id + ISO-4217
`code`), gives **`country` a `primary_currency`**, and **pre-seeds countries *and* currencies at
startup from the [restcountries.com](https://restcountries.com/) API**
([ADR-013](../../decisions/013-seed-countries-and-currencies-from-restcountries-api.md)) while
keeping manual entries. It adds a **separate `supported_country`** table that drives the
organization onboarding form. Onboarding then resolves the country's primary currency and emits it
as a top-level **`currency { id, code }`** object in `organization.onboarded`. See
[ADR-012](../../decisions/012-currency-and-supported-country-reference-model.md) +
[ADR-013](../../decisions/013-seed-countries-and-currencies-from-restcountries-api.md).

> **Out of scope (deferred):** *deletion of organizations* (idea bullet) is intentionally **not**
> in this phase. It is deferred to a later phase that handles virtual-account statuses/lifecycle,
> per the product decision recorded in [ARCHITECTURE §10](../../ARCHITECTURE.md).

## Motivation
Phase 008 gave the chaos machine countries and organization types but no currency concept, no
seeded country list, and no curated "which countries can I onboard into" subset. The idea
(`002_countries_va_via_kafka.md`) asks for all three, plus making the onboarded event carry the
country's primary currency rather than the implied `GHS` the ledger defaults to. This is the
reference-data half of that idea (the VA-ownership half is
[Phase 009](../009-ledger-owned-virtual-accounts/DESIGN.md)).

## User-Facing Changes
New / changed REST resources under `/api/v0` (Bearer-authenticated):

- `…/currencies` — create / list / get / update (`id`, `code`, `name`, `symbol`, `status`).
  Seeded at startup from the restcountries.com fetch (dedup by code); manual entries supported.
- `…/countries` — now carries `primary_currency_id`; **pre-seeded at startup from restcountries.com**
  (manual entries still allowed). Update accepts `primary_currency_id`. `POST …/countries/refresh`
  forces a re-seed.
- `…/supported-countries` — create (mark a country supported) / list / get / delete-or-disable. The
  onboarding form lists **only** supported countries.
- `…/organizations` — onboarding now resolves the country's primary currency, snapshots it, and the
  emitted `organization.onboarded` includes top-level `currency { id, code }`.

New admin UI pages: **Currencies**, **Supported Countries**; the **Organizations** onboarding form
restricts country choice to supported countries and shows the resolved currency.

## Architecture Impact
- New `currency` table + `country.primary_currency_id` FK + `supported_country` table (Flyway
  `V6`, shared with Phase 009). New entities/services/controllers in
  `com.softspark.chaos.organization` (`currency` + `supportedcountry` subpackages or peers of
  `country`) plus a `seed` subpackage. UUID v4 ids per
  [ADR-010](../../decisions/010-uuid-v4-ids-for-organization-domain.md).
- A **`RestCountriesClient` + `ReferenceDataSeeder`** fetch
  `GET https://restcountries.com/v3.1/all?fields=name,cca2,cca3,currencies` **at startup** (async,
  off the boot thread, seed-if-empty by default) and idempotently upsert currencies (dedup by code)
  + countries (with primary currency). A manual `POST /api/v0/countries/refresh` re-syncs. The app
  boots even when the API is down ([ADR-013](../../decisions/013-seed-countries-and-currencies-from-restcountries-api.md)).
- The chaos-side `OrganizationOnboardedEventData` is extended with a top-level `Currency(id, code)`;
  the outbox envelope factory populates it from the org's snapshot. Extends
  [ADR-008](../../decisions/008-organization-onboarding-domain-model.md) /
  [ADR-009](../../decisions/009-transactional-outbox-for-organization-onboarded.md).

```mermaid
erDiagram
    currency ||--o{ country : "primary_currency_id"
    country ||--o{ supported_country : "country_id"
    country ||--o{ organization : "country_id"
    currency {
        TEXT currency_id PK "UUID v4"
        TEXT code UK "ISO-4217, upper"
        TEXT name
        TEXT symbol "nullable"
        TEXT status "ACTIVE|INACTIVE"
    }
    country {
        TEXT country_id PK
        TEXT iso_code UK
        TEXT primary_currency_id FK "nullable"
        TEXT status
    }
    supported_country {
        TEXT supported_country_id PK "UUID v4"
        TEXT country_id FK UK
        TEXT status
    }
```

## Edge Cases
- **Duplicate currency `code` / duplicate `supported_country.country_id`** → `409 Conflict`
  (unique at DB + checked in-service).
- **Country with no `primary_currency`** at onboard → reject (`400/409`) or fall back to a
  configured default; **decision:** require a primary currency on supported countries (validated at
  onboard).
- **Onboarding a non-supported country** → `400/409` (the form prevents it, the API enforces it).
- **Seed idempotency** → re-running seed must not duplicate currencies/countries (seed-if-absent by
  `code` / `iso_code`) and must not clobber manual edits.
- **restcountries.com unreachable / rate-limited / changes schema** → the app still boots; seeding
  degrades, is logged/metered, and is retried (scheduled or `POST /api/v0/countries/refresh`). On a
  cold first boot with no network the country list is empty until connectivity returns (surfaced in
  the onboarding UI).
- **Multi-currency countries** → the first currency in the API `currencies` map is taken as primary;
  manual CRUD corrects exceptions.
- **Inactive currency** referenced as a country's primary → onboarding rejects (currency must be
  `ACTIVE`).
- **Manual edits after onboarding** → org snapshot (`primary_currency_id`/`code`) and the emitted
  event are unaffected by later currency/country edits (snapshot discipline, ADR-008).

## Testing Strategy
- **Unit:** currency CRUD + uniqueness/ISO-4217 validation; country `primary_currency` resolution;
  supported-country uniqueness; onboard snapshots the primary currency; envelope carries
  `currency { id, code }`.
- **Contract:** `organization.onboarded` serializes the extended payload (top-level `currency`)
  and remains deserializable by the ledger (unknown-field tolerant).
- **Integration:** seed-on-boot is idempotent; onboarding a supported country emits the currency;
  onboarding an unsupported / no-currency country is rejected.
- Consolidated into [Phase 006](../006-testing-and-verification/DESIGN.md).

## Deployment Strategy
- Flyway `V6` additive (new tables + nullable `country.primary_currency_id`); reference data seeded
  at startup by the async `ReferenceDataSeeder` from restcountries.com (seed-if-empty by default,
  policy `chaos.reference-data.seed-on-startup`). **Outbound network** to restcountries.com (or a
  configured mirror via `chaos.reference-data.restcountries.base-url`) must be permitted; the manual
  refresh endpoint re-syncs on demand.
- The onboarded-event currency extension ships behind no flag but is additive/forward-compatible;
  a contract test guards the ledger's tolerance of the new field.

## Tasks
- [001 - Currency master data & API](./001-currency-master-data.md) — `currency` table (UUID id + ISO-4217 code) + CRUD; seeded by Task 002's restcountries fetch.
- [002 - Country primary currency & seeding from restcountries.com](./002-country-primary-currency-and-seeding.md) — `RestCountriesClient` + async `ReferenceDataSeeder` (seeds countries **and** currencies), `primary_currency_id` FK, `POST /countries/refresh`.
- [003 - Supported countries table & API](./003-supported-countries.md) — separate `supported_country` table + endpoints; onboarding restricted to supported countries.
- [004 - Onboarded event carries primary currency](./004-onboarded-event-currency.md) — extend `OrganizationOnboardedEventData` with top-level `currency { id, code }`, snapshot + envelope factory.
- [005 - Frontend: Currencies, Supported Countries & onboarding form](./005-frontend-currencies-and-supported-countries.md) — new pages + onboarding form changes.

## Parallel Tasks
- **001** (currency) is foundational and blocks **002** (country FK to currency) and **004** (event
  currency).
- **002** depends on 001; **003** depends only on the existing `country` table (can run alongside
  001/002).
- **004** depends on 002 (primary currency resolution) and on extending the onboarded contract.
- **005** depends on the corresponding backend tasks (Currencies page → 001; Supported Countries
  page → 003; onboarding form → 002+003+004).

Recommended order: **001 → 002 → (003 ‖ 004) → 005**. Runs largely in parallel with
[Phase 009](../009-ledger-owned-virtual-accounts/DESIGN.md); they converge where the org-VA create
form (009/004–005) consumes the `currency` table.
