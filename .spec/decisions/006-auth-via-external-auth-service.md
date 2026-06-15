# ADR 006 - Authentication via the external AUTH SERVICE

## Status
Accepted

## Context
The MANIFEST requires "Login via the AUTH SERVICE" and the backend to be a proxy. The
reference ledger does **not** sign or parse JWTs locally; it introspects bearer tokens by
calling an external auth service (`auth/AccessTokenVerifier.java` → `RestClient` POST to
`authentication.service.token-verification-uri`, gated by
`authentication.service.client-auth.enabled`).

## Decision
Adopt the ledger's token-introspection model and add a thin login proxy:
- `POST /api/v0/auth/login` forwards credentials to the AUTH SERVICE and returns the access
  token (+ expiry) to the UI.
- A servlet `AccessTokenFilter` extracts `Authorization: Bearer …` and delegates to a
  `TokenVerifier` that introspects against the AUTH SERVICE; verified principals/authorities
  populate the `SecurityContext`.
- `SecurityConfiguration` permits `/api/v0/auth/login`, actuator health, and OpenAPI;
  everything else under `/api/v0/**` requires authentication. CSRF disabled (stateless).

Auth-service base URL, login URI, token-verification URI, and `client-auth.enabled` are
externalized exactly like the ledger.

## Consequences
- (+) Identical trust model and config keys as the ledger; reuses the same AUTH SERVICE.
- (+) No secret-signing keys in the chaos machine.
- (−) Every authenticated request incurs an introspection call; mitigated with a short-TTL
  in-memory cache of verification results keyed by token hash.
- (−) Runtime dependency on the AUTH SERVICE for all protected calls; surfaced as `401/503`
  with clear messaging when unreachable.
