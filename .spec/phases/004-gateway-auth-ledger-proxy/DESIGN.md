# Phase 4 - Gateway: Auth & Ledger Proxy

## Summary
Make the backend the **single gateway** the UI talks to (ADR-003): a login proxy + bearer-token
introspection against the **AUTH SERVICE** (mirroring the ledger's auth model), and a resilient
server-side **read proxy** that surfaces ledger accounts / balances / transactions as chaos
DTOs — so the UI never talks to the ledger or auth service directly.

## Motivation
The MANIFEST requires "Login via the AUTH SERVICE" and that the service "serves as proxy for
reaching other services". The UI's virtual-account and transaction pages need ledger-owned data;
routing it through the gateway gives one origin, one auth surface, and one place for resilience.

## User-Facing Changes
- `POST /api/v0/auth/login`, `POST /api/v0/auth/refresh`, `GET /api/v0/auth/me`.
- All other `/api/v0/**` require a verified token.
- `GET /api/v0/ledger/accounts`, `/ledger/accounts/{id}`, `/ledger/accounts/{id}/balances`,
  `/ledger/transactions` (proxied reads).

## Architecture Impact
Adds `com.softspark.chaos.auth` (login proxy, `AccessTokenFilter`, `TokenVerifier`,
`SecurityConfiguration`) and `com.softspark.chaos.ledgerproxy` (`RestClient` to the ledger +
DTO mapping + resilience). Establishes the security posture for every other phase's endpoints.

## Edge Cases
- AUTH SERVICE unreachable → `503` on login, `401` on protected calls, with clear messaging.
- Token expiry / invalid token → `401`.
- Ledger unreachable / slow → circuit-breaker open → `503` degraded, not a hang.
- Local dev with `auth-service.client-auth.enabled=false` → a permissive dev principal (mirrors ledger).

## Testing Strategy
- Auth: WebMvc + filter tests for login forward, token verify success/failure, public-path allow-list.
- Proxy: `RestClient` mapping tests (WireMock/MockRestServiceServer); timeout/retry/circuit-breaker behavior.
- Integration: end-to-end login → call a protected proxied read with a stub auth + stub ledger.

## Deployment Strategy
Auth-service + ledger URLs via env (same keys as the ledger). Circuit-breaker + timeouts tuned
via config. Toggle `client-auth.enabled` per environment. No data migration.

## Tasks
- [001 - Auth proxy & token verification](001-auth-proxy-and-token-verification.md)
- [002 - Ledger read proxy](002-ledger-read-proxy.md)

## Parallel Tasks
**001** and **002** can be built in parallel after Phase 001; 002's endpoints are protected by
001's security config, so 001 should merge first or stub the filter.
