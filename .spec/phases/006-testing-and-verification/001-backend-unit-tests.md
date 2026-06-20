# Task 001 - Backend Unit Tests

## Functional Requirements
- Fast, isolated unit tests covering the backend's business logic and contracts across all prior
  phases, with no external infrastructure (no broker, DB, or network).

## Acceptance Criteria
- [ ] `./gradlew test` runs the unit suite on JDK 25 and is green.
- [ ] Coverage of the critical packages meets the inherited jacoco gate (service/engine/chaos/
      resolver/bootstrap), excluding dto/model/config/controller per the ledger's gate config.
- [ ] Every `FlowType` (all **12** flows incl. `DISBURSEMENT_COMPLETED`) has a builder + envelope
      contract test asserting byte-parity with the corresponding fixture.

## Technical Design
Scope by phase:
- **Phase 001:** `EventEnvelope`/`EventMetadata` snake_case round-trip vs `JsonFixtures`;
  `ApiError` mapping per exception family; producer config assertions.
- **Phase 002:** flow-slot resolution precedence branches; VA registry rules (SYSTEM vs ORG,
  org create-or-link); CoA config edits + cache eviction.
- **Phase 007:** catalog validation (unique/blank codes, parent resolution, cycle detection,
  topological order); provisioning response mapping; 409-adopt-existing logic (client unit, mocked).
- **Phase 003:** engine resolve→build→chaos→publish orchestration (stub publisher/history);
  per-flow builder contract tests vs fixtures; each chaos strategy's `expand()`
  (duplicate/out-of-order/malformed mutators/unbalanced/burst/delay); collection `net = gross −
  Σfees` and disbursement `gross = net + Σfees` invariants; CSV parser + row validation.
- **Phase 004:** auth token-filter allow-list/verify (mocked verifier); ledger-proxy DTO mapping.

Frameworks: JUnit 5, AssertJ, Mockito. Shared `JsonFixtures` loads the JSON blocks from
`ss-ledger-service/bin/kafka-payload-samples.md` (and the proposed disbursement fixture) as the
contract oracle.

```mermaid
flowchart LR
  fix[JsonFixtures<br/>(bin samples + disbursement)] --> ct[Per-flow contract tests]
  builders[FlowBuilders ×12] --> ct
  chaos[Chaos strategies] --> ut[Strategy unit tests]
  resolver[SlotResolver] --> ut
```

## Implementation Notes
- `src/test/java/com/softspark/chaos/...` mirroring main packages.
- Add a `flowTypeProvider` parameterized source so adding a flow forces a contract test.
- Keep tests infra-free; use fakes for publisher/history/clock (`ChaosClock` fixed instant).

## Non-Functional Requirements
- Whole unit suite < 30s locally. Deterministic (fixed clock, fixed ULID seed where needed).

## Dependencies
All backend phases (001, 002, 007, 003, 004, 008) for code under test.

## Risks & Mitigations
- *Coverage gate flakiness on generated builders* → exclude `*Builder` per inherited gate config.
- *Missing flow contract test* → parameterized over `FlowType`; registry completeness test.

## Deployment Strategy
Runs in CI on every PR via `./gradlew test` (part of `check`).
