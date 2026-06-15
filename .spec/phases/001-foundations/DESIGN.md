# Phase 1 - Foundations

## Summary
Stand up the chaos-machine backend skeleton with the same toolchain, conventions, and
cross-cutting infrastructure as `ss-ledger-service`: Gradle/Java 25/Spring Boot 4 build, a
SQLite persistence foundation (JPA + Flyway), shared web conventions (versioned `/api/v0`,
validation, `ApiError` + global exception handler, pagination), and the Kafka event
foundation (`EventEnvelope<T>`, producer config, topic catalog). Nothing user-visible ships
yet; this is the platform every later phase builds on.

## Motivation
All four later phases depend on a consistent build, persistence, web-error, and eventing
substrate. Establishing it once — mirroring the ledger so engineers feel at home — prevents
divergent patterns and rework.

## User-Facing Changes
None directly. Operationally: the service boots, exposes `/actuator/health` and Swagger UI,
creates/migrates `chaos.db`, and can publish a hello-world envelope to a configured topic in
a smoke test.

## Architecture Impact
Introduces `com.softspark.chaos` base packages: `config`, `advice`, `base`, `kafka`, plus the
Gradle build, `application.yml` profiles, Dockerfile, and Flyway baseline. Establishes the
`EventEnvelope<T>` contract reused by Phase 003 and the persistence conventions reused by
Phases 002/003.

## Edge Cases
- SQLite single-writer contention (pool size 1, WAL, busy_timeout).
- Kafka broker unavailable at startup → app still boots; topic provisioning is lazy/guarded.
- Snake_case ↔ camelCase mapping correctness for the envelope (contract-critical).
- Flyway + SQLite community support quirks (e.g., limited `ALTER`); migrations written additively.

## Testing Strategy
- Unit: envelope (de)serialization round-trip vs. fixtures from `bin/kafka-payload-samples.md`;
  `ApiError` mapping for each exception family.
- Integration (`@Tag("integration")`, Testcontainers Kafka): producer publishes a real
  envelope and a console consumer reads identical JSON. Flyway migrates a temp SQLite file.
- Build smoke: `./gradlew build` green on JDK 25.

## Deployment Strategy
Multi-stage Dockerfile (temurin 25), non-root user, `/actuator/health` healthcheck. Config via
env. Ships behind the same CI as the ledger. No flags needed — foundation only.

## Tasks
- [001 - Project scaffold & build](001-project-scaffold-and-build.md)
- [002 - SQLite persistence foundation](002-sqlite-persistence-foundation.md)
- [003 - Web conventions & error handling](003-web-conventions-and-error-handling.md)
- [004 - Kafka event envelope & producer](004-kafka-event-envelope-and-producer.md)

## Parallel Tasks
After 001 lands, **002**, **003**, and **004** can proceed in parallel (independent packages).
