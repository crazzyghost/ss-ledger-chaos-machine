# ADR 003 - Backend as the single API gateway

## Status
Accepted

## Context
The MANIFEST says the backend "serves as proxy for reaching other services" and "supports
login via the AUTH SERVICE", while the UI must also view virtual accounts and transaction
history that physically live in the ledger. Two topologies were possible: (a) the UI calls
each backend directly (swift-admin's CORS-proxy style), or (b) the chaos backend is the
single gateway the UI talks to. The user explicitly chose (b).

## Decision
The **chaos backend is the single gateway**. The React UI calls only `/api/v0/**` on the
chaos backend. The backend:
- **Owns** its data (chart of accounts, VA registry, publish history, batch runs) in SQLite.
- **Proxies authentication** to the AUTH SERVICE (login + token introspection).
- **Proxies reads** of accounts / balances / transactions from `ss-ledger-service` via a
  server-side `RestClient`, re-shaping them into chaos DTOs.
- **Publishes** flow events to Kafka.

## Consequences
- (+) One origin for the UI → no browser CORS gymnastics, one auth surface, one place to add
  resilience (timeouts/retries/circuit breakers) around downstream calls.
- (+) The UI never holds ledger/auth service URLs or credentials.
- (−) The backend must expose pass-through read endpoints and own DTO mapping for ledger data.
- (−) The gateway is a runtime dependency on the ledger for read paths; mitigated with
  circuit breaking and clear `503`/degraded responses (see Phase 004).
