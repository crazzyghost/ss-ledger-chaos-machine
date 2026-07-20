# Task 001 - Ledger consistency check proxy (backend client & controller)

## Functional Requirements
Expose the ledger's consistency-check API through the chaos machine gateway so operators can trigger
checks, list checks, retrieve single checks, and page findings. Must forward the operator's bearer
token; must faithfully propagate all ledger statuses (200/400/401/403/404); must not validate or
recompute anything locally.

## Acceptance Criteria
- [ ] `PUT /api/v0/ledger/consistency-checks?type={type}` triggers one or all check types and returns **201** with the `ConsistencyCheckTriggerResponse` (list of triggered checks, each with `type`, `checkId`, `status=PENDING`)
- [ ] `GET /api/v0/ledger/consistency-checks?type=&status=&initiatorType=&page=&size=` lists checks with optional filters, paged, newest-first
- [ ] `GET /api/v0/ledger/consistency-checks/{checkId}` retrieves a single check
- [ ] `GET /api/v0/ledger/consistency-checks/{checkId}/discrepancies?code=&page=&size=` pages findings with optional discrepancy-code filter
- [ ] All four endpoints forward the operator's bearer token via the `Authorization` header (following `ledger.proxy.forward-token` config)
- [ ] All four endpoints faithfully propagate ledger HTTP status codes (200/201/400/401/403/404) rather than collapsing 4xx to 404 (ADR-035 faithful status propagation)
- [ ] Circuit breaker guards all ledger calls (shared Resilience4j instance from existing `ledgerproxy` package)
- [ ] When circuit is open, all four endpoints return **503** (Service Unavailable) with `{"error": {"code": "LEDGER_UNAVAILABLE", "message": "Ledger service temporarily unavailable"}}`
- [ ] Controller is tagged for Swagger UI under a new "Consistency Checks (Ledger Proxy)" tag

## Technical Design

**Package structure (new):**
```
com.softspark.chaos.consistencycheck
├── controller
│   └── ConsistencyCheckProxyController.java
└── dto
    ├── ConsistencyCheckResponse.java
    ├── ConsistencyCheckListResponse.java
    ├── ConsistencyCheckTriggerResponse.java
    ├── ConsistencyCheckDiscrepancyResponse.java
    └── ConsistencyCheckDiscrepancyListResponse.java
```

**Existing package extension:**
```
com.softspark.chaos.ledgerproxy
├── LedgerClient.java                 # ← new methods added
├── dto
│   ├── LedgerConsistencyCheckDto.java          # ← new
│   ├── LedgerConsistencyCheckListDto.java      # ← new
│   ├── LedgerConsistencyCheckTriggerDto.java   # ← new
│   ├── LedgerConsistencyCheckDiscrepancyDto.java  # ← new
│   └── LedgerConsistencyCheckDiscrepancyListDto.java  # ← new
└── circuitbreaker
    └── (existing shared circuit breaker)
```

**Controller (new):**
`ConsistencyCheckProxyController` is a **thin proxy controller** that delegates all calls to
`LedgerClient`. It has **no service layer** — the pattern is direct call: controller → client →
ledger. Each handler:

1. Extracts the bearer token from the `HttpServletRequest` (via `extractToken` helper, following
   `LedgerReadController` / `LedgerExportController` pattern).
2. Calls the corresponding `LedgerClient` method (which is circuit-breaker guarded).
3. Maps the ledger's DTOs to chaos-facing DTOs (which are structurally identical but use the chaos
   package namespace for API contract hygiene).
4. Returns the mapped DTO. On `CircuitBreakerOpenException`, throws
   `InternalServerErrorException("Ledger service temporarily unavailable")`, which
   `GlobalExceptionHandler` maps to **503** via the existing handler.

**`LedgerClient` extensions (new methods):**
```java
@CircuitBreaker(name = "ledger")
public LedgerConsistencyCheckTriggerDto triggerConsistencyChecks(
    String bearerToken,
    @Nullable String type) { /* PUT /consistency-checks?type= */ }

@CircuitBreaker(name = "ledger")
public LedgerPageDto<LedgerConsistencyCheckDto> listConsistencyChecks(
    String bearerToken,
    @Nullable String type,
    @Nullable String status,
    @Nullable String initiatorType,
    int page,
    int size) { /* GET /consistency-checks?... */ }

@CircuitBreaker(name = "ledger")
public LedgerConsistencyCheckDto getConsistencyCheck(
    String bearerToken,
    String checkId) { /* GET /consistency-checks/{checkId} */ }

@CircuitBreaker(name = "ledger")
public LedgerPageDto<LedgerConsistencyCheckDiscrepancyDto> listConsistencyCheckDiscrepancies(
    String bearerToken,
    String checkId,
    @Nullable String code,
    int page,
    int size) { /* GET /consistency-checks/{checkId}/discrepancies?... */ }
```

All four methods use `RestClient` (the existing `ledgerClient` instance), forward the bearer token
via `.header("Authorization", "Bearer " + bearerToken)`, and return the ledger's DTOs unchanged. The
`@CircuitBreaker` annotation guards each call; when the circuit is open, Resilience4j throws
`CallNotPermittedException`, which `LedgerClient` catches and rethrows as
`CircuitBreakerOpenException` (the existing pattern from Phase 004).

**DTOs (new):**
All chaos-facing DTOs (`ConsistencyCheck*`) are **record types** (Java 25) mirroring the ledger's
own response shapes. No `@RecordBuilder` (these are never mutated). Each DTO has a static `from`
factory that maps from the corresponding `LedgerConsistencyCheck*Dto`. The ledger uses
`snake_case` in its responses; the chaos DTOs use `camelCase` for frontend consumption (Spring Boot
4's default JSON serialization). Field types:

- `checkId`, `accountId`, `entryId`, `initiatedBy`: `UUID`
- `type`: `String` (enum name: `ACCOUNT_BALANCE_PROJECTION`, `ENTRY_BALANCE`, `SEQUENCE_INTEGRITY`)
- `status`: `String` (enum name: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`)
- `initiatorType`: `String` (enum name: `SYSTEM`, `PLATFORM_OPERATOR`)
- `code`: `String` (enum name: varies by check type, e.g. `BALANCE_MISMATCH`, `DEBIT_CREDIT_MISMATCH`)
- `asOf`, `initiatedAt`, `completedAt`, `erroredAt`, `detectedAt`: `Instant`
- `discrepancyCount`: `int`
- `errorCode`: `String` (nullable; enum name, e.g. `TASK_EXECUTION_FAILED`)
- `details`: `Map<String, Object>` (JSONB pass-through; UI renders as formatted JSON panel)

**List DTOs:**
`ConsistencyCheckListResponse` and `ConsistencyCheckDiscrepancyListResponse` are `PageResponse<T>`
wrappers (the existing `com.softspark.chaos.base.PageResponse` record). Each has a static
`fromPage` factory that maps from the ledger's `LedgerPageDto<T>` to `PageResponse<T>` (the existing
pattern from `LedgerReadController`).

**Trigger DTO:**
`ConsistencyCheckTriggerResponse` is a record with a single component:
`List<TriggeredCheck> checks`, where `TriggeredCheck(String type, UUID checkId, String status)` is a
nested record. The factory maps from `LedgerConsistencyCheckTriggerDto`, which has the identical shape.

## Implementation Notes

**Controller path:** `/api/v0/ledger/consistency-checks` (mirrors the ledger's `/api/v0/consistency-checks`)

**No local validation:** The chaos controller never validates `type`, `status`, `initiatorType`, or
`code` parameters — they are forwarded to the ledger unchanged. If the ledger returns **400** for an
unknown value, that 400 is propagated to the chaos UI unchanged. This follows ADR-035 (faithful
status propagation).

**Bearer token extraction:** Reuse the `extractToken(HttpServletRequest)` helper from
`LedgerReadController` / `LedgerExportController`:
```java
private static String extractToken(HttpServletRequest request) {
  var header = request.getHeader("Authorization");
  if (header != null && header.startsWith("Bearer ")) {
    return header.substring(7);
  }
  throw new UnauthorizedException("Missing or invalid Authorization header");
}
```

**Circuit breaker config:** Reuse the existing `"ledger"` circuit breaker instance configured in
`application.yaml` (the shared Resilience4j config from Phase 004). No new circuit breaker
configuration is required.

**`LedgerClient` baseUrl:** The existing `ledger.base-url` config property (default
`http://localhost:9090`) is used. The new paths are relative to that base.

**Springdoc OpenAPI integration:** Add `@Tag(name = "Consistency Checks (Ledger Proxy)",
description = "Proxied read-through to the ledger's consistency-check endpoints")` to the
controller. Each handler gets `@Operation(summary = "...", security = @SecurityRequirement(name =
"bearerAuth"))`. This surfaces the four endpoints in the Swagger UI under a new tag, separate from
the existing "Ledger Proxy" tag (which covers accounts/transactions/balances/exports).

**Error handling:**
- `CircuitBreakerOpenException` → **503** (via `GlobalExceptionHandler`, existing handler)
- `RestClientException` with ledger 400 → **400** (propagated unchanged)
- `RestClientException` with ledger 401/403 → **401/403** (propagated unchanged)
- `RestClientException` with ledger 404 → **404** (propagated unchanged)
- `RestClientException` with ledger 5xx → **502** (via `GlobalExceptionHandler`, existing handler)

## Non-Functional Requirements
- **Performance:** Circuit breaker must trip and recover per the existing shared config (failure
  rate threshold 50%, wait duration 10s, permitted calls in half-open 3).
- **Security:** All four endpoints require authentication (existing `@PreAuthorize("isAuthenticated()")` from the global security config). The ledger enforces its own authority checks (`ledger_consistency_check:view::allow`, `ledger_consistency_check:create::allow`).
- **Observability:** All four endpoints are logged via the existing `@LoggedOperation` AOP (from
  Phase 001); operation names: `consistency-check.trigger`, `consistency-check.list`,
  `consistency-check.get`, `consistency-check.discrepancies.list`.

## Dependencies
- Existing `ledgerproxy` package (shared `LedgerClient`, shared circuit breaker)
- Existing `base` package (`PageResponse`, `ApiError`, `ErrorDescription`)
- Existing `advice` package (`GlobalExceptionHandler`)
- Existing `config` package (`SecurityConfiguration`, `OpenApiConfiguration`)
- No new external dependencies (Spring Boot 4 `RestClient`, Resilience4j circuit breaker already present)

## Risks & Mitigations
**Risk:** Ledger does not have the consistency-check API (currently under development).  
**Mitigation:** All four endpoints 404 cleanly; Task 005 (frontend) degrades to an explicit "not
available" state.

**Risk:** Ledger's task worker is disabled (`ledger.tasks.worker.enabled=false`, the default) — a
triggered check sits `PENDING` forever.  
**Mitigation:** Task 005 (frontend detail view) shows a **"Task worker not running"** warning when a
check is `PENDING` for > 30s and offers a help link to the ledger's task-worker docs.

**Risk:** Circuit breaker trips during normal chaos testing (expected — the harness deliberately
stresses the ledger).  
**Mitigation:** The **503** response is correct behaviour; the UI should surface it as "ledger is
under load" rather than as a fatal error. Task 005 handles this with a dismissible banner.

## Testing Strategy

**Unit tests (controller):**
- `ConsistencyCheckProxyControllerTest`: Mock `LedgerClient`, verify each handler calls the correct
  client method with the correct parameters, verify DTO mapping, verify circuit-breaker-open path
  returns **503**.
- `ConsistencyCheck*Response.from` tests: Verify each DTO factory correctly maps from the ledger DTO.

**Integration tests (client):**
- `LedgerClientConsistencyCheckIntegrationTest`: Use `MockRestServiceServer` to stub the ledger's
  four endpoints, verify `LedgerClient` sends the correct HTTP requests (method, path, query params,
  Authorization header), verify response mapping, verify 404/400/401/403 propagation, verify
  circuit-breaker trip on repeated 5xx.

**Manual verification:**
- Deploy against a ledger that has the consistency-check API (branch to be determined).
- Trigger a check via `PUT /ledger/consistency-checks?type=ALL`, verify **201** response with three
  check ids (one per type).
- List checks via `GET /ledger/consistency-checks`, verify paging works, verify filter by
  `type=ENTRY_BALANCE` returns only that type.
- Get a single check via `GET /ledger/consistency-checks/{checkId}`, verify status/discrepancyCount.
- List discrepancies via `GET /ledger/consistency-checks/{checkId}/discrepancies`, verify pagination,
  verify `code` filter narrows results.
- Deploy against a ledger without the API, verify all four endpoints return **404**, verify UI shows
  "not available".
- Stop the ledger, verify circuit breaker opens after repeated failures, verify **503** on next call,
  verify circuit recovers after ledger restarts.

## Deployment Strategy
No Flyway migration. No configuration change. No Kafka topic. Deploy backend only; verify via
Swagger UI before deploying frontend (Task 005).
