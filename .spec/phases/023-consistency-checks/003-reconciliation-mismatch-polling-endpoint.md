# Task 003 - Reconciliation mismatch polling endpoint (backend API for UI toasts)

## Functional Requirements
Expose a chaos-machine endpoint that the frontend can poll to fetch newly consumed reconciliation
mismatch events since a given timestamp, so the UI can toast immediately when a check completes with
findings without requiring WebSocket or SSE infrastructure. Must paginate; must support efficient
"give me everything since my last poll" queries; must never return the same event twice in a single
polling session.

## Acceptance Criteria
- [ ] `GET /api/v0/reconciliation-mismatches?since={iso8601_timestamp}&size={int}` returns a paged list of `ReconciliationMismatchDto` objects consumed after the `since` timestamp, ordered by `consumed_at` ascending
- [ ] `since` parameter is required; missing or malformed returns **400**
- [ ] `size` parameter is optional (default 20, max 100); out-of-range returns **400**
- [ ] Response is `PageResponse<ReconciliationMismatchDto>` (the existing `com.softspark.chaos.base.PageResponse`)
- [ ] Each `ReconciliationMismatchDto` includes: `id`, `checkId`, `type`, `initiatorType`, `asOf`, `initiatedAt`, `completedAt`, `discrepancyCount`, `consumedAt`
- [ ] Response includes a `nextSince` timestamp (the `consumedAt` of the last returned row) so the UI can poll `?since={nextSince}` on its next call
- [ ] Endpoint is authenticated (existing `@PreAuthorize("isAuthenticated()")`)
- [ ] Endpoint is logged via `@LoggedOperation("consistency-check.mismatch.poll")`

## Technical Design

**Package structure (extension):**
```
com.softspark.chaos.consistencycheck
├── controller
│   └── ReconciliationMismatchController.java   # ← new
├── repository
│   └── ReconciliationMismatchRepository.java   # ← extend with custom query
├── dto
│   └── ReconciliationMismatchDto.java          # ← new
└── service
    └── ReconciliationMismatchService.java      # ← new
```

**Controller (new):**
`ReconciliationMismatchController` has a single `GET /api/v0/reconciliation-mismatches` handler that
validates the `since` parameter, delegates to `ReconciliationMismatchService`, and returns
`PageResponse<ReconciliationMismatchDto>`.

```java
@GetMapping("/api/v0/reconciliation-mismatches")
@LoggedOperation("consistency-check.mismatch.poll")
@PreAuthorize("isAuthenticated()")
@Operation(summary = "Poll for new reconciliation mismatches")
public ResponseEntity<PageResponse<ReconciliationMismatchDto>> pollMismatches(
    @RequestParam String since,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
  // parse `since` to Instant (or LocalDateTime; decision: LocalDateTime for consistency with other chaos timestamps)
  // call service.pollMismatches(parsedSince, size)
  // return PageResponse
}
```

**Service (new):**
`ReconciliationMismatchService` has one method:
```java
public PageResponse<ReconciliationMismatchDto> pollMismatches(LocalDateTime since, int size) {
  // query repository: findAllByConsumedAtAfter(since, PageRequest.of(0, size, Sort.by("consumedAt").ascending()))
  // map each entity to ReconciliationMismatchDto
  // compute nextSince = lastRow.consumedAt (or `since` if no rows)
  // return PageResponse with items, totalElements, hasNext, nextCursor=nextSince
}
```

**Repository extension:**
Add a custom query to `ReconciliationMismatchRepository`:
```java
Page<ReconciliationMismatch> findAllByConsumedAtAfter(
    LocalDateTime since,
    Pageable pageable);
```

Spring Data JPA generates the query: `SELECT * FROM reconciliation_mismatch WHERE consumed_at > ?1 ORDER BY consumed_at ASC LIMIT ?2 OFFSET ?3`. The `idx_reconciliation_mismatch_consumed_at` index (from Task 002 migration) makes this efficient.

**DTO (new):**
`ReconciliationMismatchDto` is a record mirroring the entity's columns. All fields are non-null
(except `nextSince` in the response envelope, which is computed by the service, not this DTO).

```java
public record ReconciliationMismatchDto(
    UUID id,
    UUID checkId,
    String type,
    String initiatorType,
    Instant asOf,
    Instant initiatedAt,
    Instant completedAt,
    int discrepancyCount,
    LocalDateTime consumedAt) {
  
  public static ReconciliationMismatchDto from(ReconciliationMismatch entity) {
    // map entity → DTO
  }
}
```

**Response envelope:**
Reuse `PageResponse<T>` (the existing record from `com.softspark.chaos.base`):
```java
public record PageResponse<T>(
    List<T> items,
    int totalElements,
    int page,
    int size,
    boolean hasNext) { }
```

The service computes `hasNext` by checking if `items.size() == size` (a heuristic: if the page is
full, there *might* be more; if it is partial, there are no more). The `nextSince` is returned as a
separate field **outside** `PageResponse` — the response shape is:
```json
{
  "items": [...],
  "totalElements": 20,
  "page": 0,
  "size": 20,
  "hasNext": true,
  "nextSince": "2026-07-20T12:34:56"
}
```

**Timestamp format:** The `since` parameter is ISO 8601 LocalDateTime (`yyyy-MM-dd'T'HH:mm:ss`), no
zone (SQLite stores timestamps as TEXT, no timezone; the existing pattern from Phase 018 / 019). The
`nextSince` response field is also LocalDateTime. The UI polls with the `nextSince` from the
previous response.

## Implementation Notes

**Polling interval:** The frontend (Task 005) polls every 5s when the consistency checks page is
open, or when the user triggers a check. No server-side polling state; the frontend maintains the
`since` cursor.

**Deduplication:** The query is `WHERE consumed_at > ?1`, **not** `>=`, so a row with
`consumed_at = T` is returned once (when the UI polls with `since < T`) and never again (when the UI
polls with `since = T`). This ensures no duplicate toasts.

**Clock skew:** If the chaos machine's clock drifts relative to the ledger's, the `consumed_at`
timestamp may be slightly off. This is acceptable — the toast is best-effort. If the UI misses an
event, it is eventually discovered via the list-checks proxy.

**No total count:** The `totalElements` field in `PageResponse` is the count of items returned in
this page, **not** the total count in the table. Computing the total count would require a separate
`COUNT(*)` query, which is expensive and unnecessary for a polling endpoint. The UI does not display
a total count; it only cares about "has more" (the `hasNext` field).

**No cursor-based pagination:** The endpoint uses `since` as an exclusive lower bound, not a cursor.
This is simpler than cursor-based pagination and sufficient for the polling use case. The UI never
needs to jump to page 3; it only needs to say "give me everything since my last poll".

**No filter by type or initiatorType:** The endpoint returns all mismatches. If filtering is needed,
the UI can filter client-side (the payload is small; a single page is at most 100 rows × ~200 bytes
= 20 KB). If server-side filtering is ever needed, it can be added in a future phase.

**Validation:**
- `since` parameter: Parse via `LocalDateTime.parse(value)` (Spring Boot 4's default
  `@RequestParam` conversion). If malformed, Spring throws `MethodArgumentTypeMismatchException`,
  which `GlobalExceptionHandler` maps to **400**.
- `size` parameter: Validated via `@Min(1) @Max(100)`. Out-of-range → **400**.

**Logging:** Log at DEBUG level (polling is frequent; INFO would be noisy):
```
Polling reconciliation mismatches since {since}: returned {count} rows
```

## Non-Functional Requirements
- **Performance:** The query must return in < 50 ms under normal load (polling every 5s from ≤ 10
  concurrent UI sessions). The `consumed_at` index makes this fast.
- **Scalability:** The table grows unbounded. If it accumulates > 100K rows, the query slows down. A
  cleanup job (out of scope for this phase) should periodically `DELETE FROM reconciliation_mismatch WHERE consumed_at < now() - INTERVAL 30 DAYS`.
- **Observability:** Polling calls are logged at DEBUG level (not INFO, to avoid log spam).

## Dependencies
- Existing `base` package (`PageResponse`)
- Existing `advice` package (`GlobalExceptionHandler`)
- Existing `config` package (`SecurityConfiguration`, `OpenApiConfiguration`)
- No new external dependencies

## Risks & Mitigations
**Risk:** UI polls too frequently, hammering the database.  
**Mitigation:** The 5s interval is deliberate; polling more frequently is wasteful. The query is
indexed and fast. If the UI ever adds a "real-time" mode, it should use WebSocket or SSE (future
phase, out of scope here).

**Risk:** The table grows unbounded (100K+ rows).  
**Mitigation:** A cleanup job (out of scope) should periodically delete old rows. The query is still
fast with 100K rows (indexed scan).

**Risk:** Clock skew between chaos machine and UI.  
**Mitigation:** The `since` timestamp is the chaos machine's `consumed_at`, not the UI's clock. No
cross-machine clock comparison.

## Testing Strategy

**Unit tests (service):**
- `ReconciliationMismatchServiceTest`: Mock `ReconciliationMismatchRepository`, create a page of
  entities, call `pollMismatches(since, size)`, verify the returned `PageResponse` has the correct
  items, verify `nextSince` is the last row's `consumedAt`, verify `hasNext` is correct.
- Test empty result: mock returns empty page, verify `nextSince = since` (unchanged).
- Test partial page: mock returns 5 rows when size=20, verify `hasNext = false`.

**Integration tests:**
- `ReconciliationMismatchControllerIntegrationTest` (Spring Boot Test, in-memory SQLite): Insert 3
  rows with `consumedAt = T1, T2, T3`, call `GET /reconciliation-mismatches?since=T0`, verify 3
  rows returned, call `GET ?since={nextSince}`, verify 0 rows returned (no new rows).
- Test deduplication: poll with `since=T1`, verify row T2 returned, poll again with `since=T2`,
  verify row T2 not returned (exclusive lower bound).

**Manual verification:**
- Deploy backend, trigger a consistency check via the ledger that completes with findings, verify the
  mismatch event is consumed (Task 002), call `GET /reconciliation-mismatches?since={5_minutes_ago}`,
  verify the event is returned, call again with the `nextSince` from the response, verify no rows
  returned (until a new event arrives).
- Trigger a second check, verify the second event is returned on the next poll.

## Deployment Strategy
No Flyway migration (table already exists from Task 002). Deploy backend only; verify via Swagger UI
before deploying frontend (Task 005).
