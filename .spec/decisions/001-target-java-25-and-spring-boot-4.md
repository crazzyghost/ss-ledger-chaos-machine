# ADR 001 - Target Java 25 and Spring Boot 4

## Status
Accepted

## Context
The chaos machine must "follow `ss-ledger-service`" structurally and will share Kafka
event contracts, security model, and developer ergonomics with it. The reference service
targets Java 25 (`.java-version` = `25`, Gradle toolchain `JavaLanguageVersion.of(25)`)
and Spring Boot `4.0.6` with the `io.spring.dependency-management` plugin.

## Decision
Target **Java 25** and **Spring Boot 4.0.6** on **Gradle**, group `com.softspark`, base
package `com.softspark.chaos`. Adopt the same toolchain block, `record-builder` annotation
processor (no Lombok), and OpenAPI/Actuator starters.

Version-specific features we will lean on:
- **Records + sealed interfaces** for the event-payload and flow-definition hierarchies.
- **Pattern-matching `switch`** for flow dispatch and chaos-strategy selection.
- **Virtual threads** (`spring.threads.virtual.enabled=true`) for CSV batch fan-out.
- **Text blocks** for embedded JSON samples/fixtures in tests.

## Consequences
- (+) Contract, security, and build parity with the ledger; engineers move between repos freely.
- (+) Virtual threads give cheap concurrency for batch publishing without a thread-pool tax.
- (−) Requires a JDK 25 toolchain in CI and on developer machines (same as ledger, so no net new cost).
- (−) Spring Boot 4 is recent; we accept its newer dependency baseline (already proven by the ledger).
