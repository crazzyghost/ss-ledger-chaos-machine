# ADR 002 - SQLite persistence with JPA and Flyway

## Status
Accepted

## Context
The MANIFEST specifies a "simple sqlite backed store". The reference ledger uses
PostgreSQL with Spring Data JPA + Flyway migrations. We want to keep the ledger's
persistence *ergonomics* (JPA entities, repositories, versioned migrations) while honoring
the SQLite requirement. The chaos machine's data is low-volume operator/config/audit data
(chart of accounts, VA registry, publish history, batch runs) — not a hot transactional path.

## Decision
Use **SQLite** as a single-file database accessed through **Spring Data JPA** with the
**`org.hibernate.community.dialect.SQLiteDialect`** (from `hibernate-community-dialects`)
and the **`org.xerial:sqlite-jdbc`** driver. Manage schema with **Flyway** using its
community SQLite support. Keep the ledger's repository conventions.

Configuration guard-rails for SQLite's single-writer model:
- `spring.datasource.hikari.maximum-pool-size=1` (SQLite serializes writes).
- Enable WAL mode + `busy_timeout` via connection init SQL.
- File path externalized via `chaos.datasource.path` (default `./data/chaos.db`).

## Consequences
- (+) Zero external DB to run the harness; trivial local + CI startup; portable file.
- (+) Same JPA/Flyway developer experience as the ledger.
- (−) Single-writer concurrency — acceptable for this workload; batch *publishing*
  concurrency is on Kafka, while DB writes (history rows) are funneled through a bounded queue.
- (−) Flyway/Hibernate SQLite support is "community" tier; we pin versions and cover schema
  with migration tests. If SQLite ever becomes limiting, the JPA abstraction lets us swap to
  Postgres with only dialect/driver/migration changes.
