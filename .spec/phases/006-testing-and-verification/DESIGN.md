# Phase 6 - Testing & Verification

> **Consolidated testing phase.** Unit and integration testing for every prior phase is owned
> here as discrete, trackable work items. Each task/feature spec's own "Testing Strategy"
> section states *intent* (what to verify); the executable test work lives in this phase so
> testing is planned, sequenced, and reviewed as a whole rather than interleaved.

## Summary
A final phase that delivers the test suites validating the whole system: backend unit tests,
backend integration tests (Testcontainers Kafka + WireMock ledger/auth + SQLite + contract
parity against the ledger's `bin/kafka-payload-samples.md`), frontend tests (Vitest + Testing
Library + MSW), and an end-to-end chaos verification that proves the harness drives a real
ledger and that chaos strategies land as intended (idempotency, DLT, balance validation).

## Motivation
Centralizing testing makes coverage auditable against the coverage gate inherited from the
ledger build (`jacocoTestCoverageVerification`), keeps the earlier phases focused on behavior,
and ensures the cross-cutting concerns (contract parity, resilience, chaos semantics) are
verified once, coherently, with shared fixtures and harnesses.

## User-Facing Changes
None. Engineering-facing: green `./gradlew check` (unit + integration + coverage gate) and
`npm run test` (frontend), runnable in CI, plus an opt-in e2e profile.

## Architecture Impact
Adds `src/test/java` (unit) and `src/integration-test/java` (integration, `@Tag("integration")`)
trees mirroring the ledger's layout and Gradle wiring (separate `integrationTest` source set +
task), shared test fixtures (`JsonFixtures` from the bin samples), Testcontainers + WireMock
support, and frontend `*.test.tsx` + MSW handlers. No production code added beyond test seams.

## Edge Cases (to exercise)
- Contract drift vs ledger event schemas (all 12 flows) → fixture parity tests fail on drift.
- SQLite single-writer contention under concurrent history/batch writes.
- Ledger/auth-service outages → circuit-breaker open, degraded `503`, PENDING bootstrap reconcile.
- Chaos: duplicate (idempotency), out-of-order, malformed → DLT, unbalanced → validation reject, burst/backpressure.
- CSV partial failure isolation + large-file streaming.
- Auth: valid/invalid/expired token, allow-list, bearer-secured Swagger.

## Testing Strategy
This phase *is* the testing strategy; see tasks. Conventions mirror the ledger: JUnit 5,
AssertJ, Mockito, `MockRestServiceServer`/WireMock, Testcontainers (`kafka`), Spring Boot test
slices; coverage gate reused from `build.gradle`. Frontend mirrors swift-admin's conventions
(Vitest + Testing Library + MSW).

## Deployment Strategy
CI runs `./gradlew check` (unit + integration + coverage) and `npm run test` on PRs. The e2e
verification runs behind a profile/tag (`integration-stress` / a compose-based job) so it is
opt-in and does not gate every PR.

## Tasks
- [001 - Backend unit tests](001-backend-unit-tests.md)
- [002 - Backend integration tests](002-backend-integration-tests.md)
- [003 - Frontend tests](003-frontend-tests.md)
- [004 - End-to-end chaos verification](004-end-to-end-chaos-verification.md)

## Parallel Tasks
**001**, **002**, **003** can proceed in parallel (independent suites). **004** runs last, after
the backend and frontend suites are green, since it exercises the assembled system.
