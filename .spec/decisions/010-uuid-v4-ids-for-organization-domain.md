# ADR 010 - UUID v4 Ids for the Organization Domain

## Status
Accepted

## Context
The codebase already has an identifier strategy: `base/Ids.java` generates **ULIDs** (lexically
sortable, time-prefixed, stored as `TEXT`), used for virtual accounts, batch runs, publish
records, and other server-assigned ids.

Idea `001` specifies the new `country`, `organization_type`, and `organization` tables use **uuid**
ids. The authoritative ledger sample
(`../ss-ledger-service/bin/publish-organization-onboarded.sh`) also generates the organization,
type, and country ids as **random UUIDs** (`random_uuid`). So the entities whose ids cross the wire
into `organization.onboarded` are UUIDs on the producing side the chaos machine emulates.

This forces a choice: reuse the house ULID generator for consistency, or use UUID v4 for these
three tables to match the idea and the emulated contract.

## Decision
Generate **UUID v4** (`java.util.UUID.randomUUID().toString()`) server-side for `country_id`,
`organization_type_id`, and `organization_id`. Persist as `TEXT` (same column type as ULID ids —
SQLite stores both as strings, so no schema-type divergence). Ids are server-assigned on create and
immutable; client-supplied ids are **not** accepted for these resources.

This is a deliberate, **scoped** divergence from the ULID convention, limited to the
organization-onboarding domain, justified by (a) the explicit idea requirement and (b) fidelity to
the ss-ledger-service contract these ids appear in. All other entities keep using `Ids` (ULID).

## Consequences
**Positive**
- Matches idea `001` and the ledger's own id format for organizations/types/countries, so emitted
  events look identical to the real producer's.
- UUID v4 needs no helper — `java.util.UUID` is JDK-standard; no dependency on `base/Ids`.

**Negative / trade-offs**
- **Two id schemes coexist** (ULID elsewhere, UUID v4 here). A future reader will rightly ask why;
  this ADR is the answer. Mitigate by keeping the divergence strictly scoped to these three tables.
- UUID v4 is **not** time-sortable (unlike ULID), so `country`/`organization_type`/`organization`
  primary keys carry no natural creation order — rely on `created_at` for ordering.
- Slightly less index locality than ULID on insert; negligible at this system's scale (SQLite,
  operator-driven test harness).
