# Task 003 - Supported Countries Table & API

## Functional Requirements
- Introduce a **separate `supported_country`** table (not a flag on `country`) representing the
  curated subset of countries an operator may onboard into — *"the concept of supported countries
  (what will be used on the organization form) separate from [the full list]"*.
- Provide an API to mark a country supported / list supported countries / get / unsupport (delete or
  disable). The organization onboarding form lists **only** supported countries.

## Acceptance Criteria
- [ ] `POST /api/v0/supported-countries` with `{country_id}` creates a `supported_country` row
      (UUID v4 id), validating the country exists; duplicate `country_id` → `409 Conflict`.
- [ ] `GET /api/v0/supported-countries` returns `PageResponse<SupportedCountryResponse>` with the
      resolved country (name, iso_code, primary currency); `GET …/{id}` → row or `404`.
- [ ] `DELETE /api/v0/supported-countries/{id}` (or a status flip to `INACTIVE`) removes the country
      from the supported set.
- [ ] Onboarding (Phase 008 / Phase 010 Task 004) and the onboarding form **only** accept supported
      countries; onboarding a non-supported country → `400/409`.

## Technical Design
Target **Java 25 / Spring Boot 4**. Separate table per
[ADR-012](../../decisions/012-currency-and-supported-country-reference-model.md); UUID v4 id per
[ADR-010](../../decisions/010-uuid-v4-ids-for-organization-domain.md).

```mermaid
erDiagram
    country ||--o| supported_country : "country_id (unique)"
    supported_country {
        TEXT supported_country_id PK "UUID v4"
        TEXT country_id FK UK "→ country"
        TEXT status "ACTIVE|INACTIVE"
        TEXT created_at
        TEXT updated_at
    }
```

- **Entity** `SupportedCountry extends AuditableEntity` — `@Id supported_country_id`;
  `country_id` unique FK; `@Enumerated(STRING) status`.
- **Service** validates the referenced country exists; conflict on duplicate `country_id`;
  list resolves country + primary-currency for display.
- **Onboarding enforcement**: `OrganizationService.onboard` (Phase 008 / Task 003) additionally
  checks the country is in `supported_country` (ACTIVE) — wire this guard here (or in Task 004 where
  onboarding is already being touched). Decision: add the guard in `OrganizationService` and unit
  test it.

## Implementation Notes
Files (under `chaos-machine/src/main/java/com/softspark/chaos/organization/`):
- `model/SupportedCountry.java` — `@Table(name = "supported_country")`.
- `enumeration/SupportedCountryStatus.java` (or reuse `CountryStatus`).
- `repository/SupportedCountryRepository.java` — `existsByCountryId`, `findByCountryId`, paginated
  list.
- `dto/CreateSupportedCountryRequest.java`, `dto/SupportedCountryResponse.java`.
- `service/SupportedCountryService.java` — CRUD + validation.
- `controller/SupportedCountryController.java` — `@RequestMapping("/api/v0/supported-countries")`.
- `service/OrganizationService.java` — add the "country must be supported" guard on onboard.

Migration (`db/migration/V6__...sql`, appended after the country alter):
```sql
CREATE TABLE IF NOT EXISTS supported_country (
    supported_country_id TEXT PRIMARY KEY,
    country_id TEXT NOT NULL UNIQUE REFERENCES country(country_id),
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```
No new dependencies.

## Non-Functional Requirements
- `country_id` unique at DB + checked in-service for a clean `409`.
- List endpoint paginated; resolves country/currency for the form without N+1 (batch fetch).
- AUTH-protected (inherited).

## Dependencies
- Phase 008 / Task 001 (existing `country` table).
- Task 002 (so the resolved country can show its primary currency) — soft.
- Shares the `V6` migration.

## Risks & Mitigations
- **Onboarding guard placement** (here vs Task 004) → put it in `OrganizationService` once; both
  tasks reference the same guard to avoid divergence.
- **Unsupport semantics** (hard delete vs disable) → support both: `DELETE` removes the membership;
  a status flip disables without losing history. Default the UI to `DELETE`.

## Testing Strategy
Service tests (duplicate country → 409, unknown country → 404, onboarding rejects non-supported
country), `@WebMvcTest` controller. Consolidated in
[Phase 006](../006-testing-and-verification/DESIGN.md).

## Deployment Strategy
Flyway `V6` (additive). No flag — new resource consumed by the onboarding form (Task 005) and the
onboarding guard.
