# ADR 012 - Currency as managed reference data; Supported Countries as a separate table

## Status
Accepted

## Context
The idea (`002_countries_va_via_kafka.md`) asks to:

- "populate the countries using the list, also have manual entries",
- "introduce concept of supported countries (what will be used on the organization form) **separate
  from** [the full country list]",
- "introduce concept [of] currencies",
- and make "creating the organization … use the **primary currency of the country** in the
  `organization.onboarded` event".

Today (Phase 008): `country` is a managed table (`country_id` UUID, `name`, `iso_code`, `status`,
`modified_date`) but has **no currency** and no notion of being "supported". **Currency exists only
as a free ISO-4217 string** column on `virtual_account` / `account_role` — there is no currency
table. The ledger stores account currency the same way (a plain ISO-4217 `String`), while its
`organization.va.updated` contract references currency by a `CurrencyRef(UUID id)`; the ledger's
`OnboardedEventData` carries **no** currency and its org-VA provisioner hardcodes `GHS`.

Two modelling questions follow: how to represent currency, and how to represent the "supported"
subset of countries shown on the onboarding form.

## Decision
**1. Currency is a first-class managed reference table** (`currency`), seeded from a bundled list
with manual CRUD on top:

- `currency_id` (**UUID v4**, server-assigned — consistent with the org-domain id convention of
  [ADR-010](010-uuid-v4-ids-for-organization-domain.md)),
- `code` (**ISO-4217**, unique, upper-cased — the natural key the ledger actually consumes),
- `name`, `symbol` (nullable), `status` (`ACTIVE | INACTIVE`), audit columns.

Keying internally by UUID (not by the ISO code) keeps currency uniform with `country` /
`organization_type` and lets the code be corrected without breaking foreign keys; the `code` column
is what crosses the wire to the ledger.

**2. `country` gains a `primary_currency_id` FK** → `currency`. The bundled country seed associates
each seeded country with its primary currency.

**3. "Supported countries" is a separate `supported_country` table**, not a boolean flag on
`country` — honouring the idea's explicit "separate from" wording. It is a curated subset that
references `country` (and can later carry its own onboarding-form configuration without polluting
the master list):

- `supported_country_id` (UUID v4), `country_id` (FK → `country`, unique), `status`/`enabled`,
  audit columns. The organization onboarding form lists **only** supported countries; the full
  `country` master list remains available for administration and seeding.

**4. The `organization.onboarded` payload gains a top-level `currency` object `{ id, code }`**,
resolved from the onboarded country's `primary_currency`. `id` is the chaos `currency_id` (UUID),
`code` is the ISO-4217 code. The chaos-side `OrganizationOnboardedEventData` is extended with this
object and the outbox envelope factory populates it. The org row snapshots its
`primary_currency_id` / `primary_currency_code` at onboard time (same snapshot discipline as the
country fields, per [ADR-008](008-organization-onboarding-domain-model.md)).

The ledger's `OnboardedEventData` does not read `currency` today (it defaults to `GHS`); adding the
field is forward-compatible because Spring Boot's Jackson defaults to
`fail-on-unknown-properties = false`. When the ledger starts honouring it, no chaos-side change is
needed.

## Consequences
**Positive**
- Currency becomes selectable, validated reference data (seeded + manual) instead of a free string;
  organization VAs can be requested in "any currency" by FK, and the onboarded event carries a real
  currency instead of an implied `GHS`.
- A separate `supported_country` table cleanly distinguishes "every country we know about" from
  "countries an operator may onboard into", and leaves room for per-country onboarding config.
- UUID ids keep the three reference tables (`country`, `organization_type`, `currency`) uniform and
  refactor-safe, while the ISO `code` column preserves ledger compatibility.

**Negative / trade-offs**
- More tables and CRUD surface (currency, supported_country) and a new FK on `country` — additional
  migration (`V6`), entities, services, and admin UI pages.
- The `organization.onboarded` contract diverges (additively) from the current ledger
  `OnboardedEventData`; correctness depends on the ledger tolerating the unknown `currency` field
  until it consumes it. Covered by a contract test and noted as an assumption.
- Two representations of currency now coexist briefly: the managed `currency` table (chaos-owned)
  and the ledger's ISO-string account currency. They are reconciled by `code`; the projection from
  `ledger.account.created` ([ADR-011](011-ledger-owned-virtual-accounts-via-kafka-consumer.md))
  stores the ledger's currency `code` as-is on the VA and does not require a currency-table row to
  exist.
- Seeding introduces bundled static data that must be kept reasonable (ISO lists drift over time);
  manual CRUD is the escape hatch.
