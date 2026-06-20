# ADR 013 - Seed countries & currencies from the restcountries.com API at startup

## Status
Accepted

## Context
The idea (`002_countries_va_via_kafka.md`) asks to "populate the countries using the list, also have
manual entries". The user clarified that "the list" / "countries API" means the public
**[restcountries.com](https://restcountries.com/)** REST API — the `country` table should be
**pre-seeded at startup from that API**, not from a bundled static file.

[ADR-012](012-currency-and-supported-country-reference-model.md) established the data model:
`currency` (UUID id + ISO-4217 `code`), `country.primary_currency_id`, and a separate
`supported_country` table. It left the *seed source* as "a bundled list". This ADR replaces that
source with the external API.

restcountries.com (`v3.1`) returns, per country, exactly the fields we need in one payload:
`name.common`, `cca2` (ISO 3166-1 alpha-2, e.g. `GH`), `cca3` (alpha-3, e.g. `GHA`), and a
`currencies` map keyed by **ISO-4217 code** with `{ name, symbol }` (e.g.
`"GHS": { "name": "Ghanaian cedi", "symbol": "₵" }`). So a single fetch can seed **both** the
`currency` table (deduplicated across all countries) **and** the `country` rows with their
`primary_currency`.

The forces: it is an **external dependency invoked at startup**. The chaos machine must boot even
when restcountries.com is slow, rate-limiting, or down; the seed must be idempotent and not re-hammer
the API on every restart; and operators need a way to force a refresh.

## Decision
Seed reference data from restcountries.com at startup, resiliently and idempotently:

1. **`RestCountriesClient`** — a dedicated `RestClient` (separate from the ledger proxy) pointed at a
   configurable base URL (`chaos.reference-data.restcountries.base-url`, default
   `https://restcountries.com`) calling **`GET /v3.1/all?fields=name,cca2,cca3,currencies`** (the
   `fields` parameter is **required** by the API). Bounded connect/read timeouts; a small retry with
   back-off; failures are caught, not fatal.

2. **`ReferenceDataSeeder`** (`ApplicationRunner`, ordered after Flyway, running **off the boot
   thread** — on a virtual-thread executor per [ADR-007](007-csv-batch-execution-model.md) — so a
   slow API never blocks or fails startup). It:
   - fetches the country list,
   - upserts the `currency` table by ISO-4217 `code` (dedup across all `currencies` maps; status
     `ACTIVE`),
   - upserts `country` rows by `iso_code` (alpha-2 from `cca2`), linking `primary_currency_id` to the
     **first** currency in the country's `currencies` map.

3. **Seed-if-needed, not every boot.** By default the seeder runs only when the `country` table is
   empty (or a `seeded` marker is absent), so normal restarts do not call the API. A configurable
   policy (`chaos.reference-data.seed-on-startup` = `IF_EMPTY` | `ALWAYS` | `NEVER`, default
   `IF_EMPTY`) and a **manual refresh endpoint** (`POST /api/v0/countries/refresh`) let operators
   force a re-seed on demand.

4. **Manual entries coexist.** CRUD on `currencies` / `countries` remains; the seeder only
   *upserts-if-absent* by natural key (`code` / `iso_code`), so operator edits and manually-added
   rows are never clobbered.

5. **Graceful degradation.** If the API is unreachable, the app boots with whatever reference data is
   already persisted (possibly none on a first boot); the failure is logged and metered, and a
   bounded scheduled retry (or the manual endpoint) completes seeding later — mirroring the
   ledger-bootstrap resilience posture ([Phase 007](../phases/007-chart-of-accounts-http-bootstrap/DESIGN.md)).

## Consequences
**Positive**
- One authoritative, maintained source for countries + ISO codes + currencies; no stale bundled
  list to hand-maintain. Primary currency falls out of the same payload for free.
- Boot is never blocked or broken by the external API (async + seed-if-empty + degrade).
- Idempotent upsert-if-absent preserves manual entries and supports an explicit refresh.

**Negative / trade-offs**
- A **runtime dependency on a third-party API** (availability, rate limits, schema changes, the
  mandatory `fields` param). Mitigated by timeouts/retry, seed-if-empty (rare calls), degradation,
  and a configurable base URL (can point at a mirror/self-host).
- **Multi-currency countries**: the API lists several currencies for some countries; we pick the
  first as "primary", which may need a manual correction (CRUD covers it).
- **Data quality / churn**: country names/currencies drift over time; a forced refresh re-syncs, but
  snapshots already taken on onboarded orgs are intentionally unaffected (ADR-008).
- First boot **with no network** yields an empty country list until connectivity returns and the
  retry/endpoint runs; the onboarding form will be empty until then (surfaced in the UI).
- Network egress to a public endpoint must be permitted in the deployment environment.
