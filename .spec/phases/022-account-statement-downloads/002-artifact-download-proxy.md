# Task 002 - Artifact Download Proxy

## Functional Requirements

Add the chaos-only endpoint that actually delivers the statement bytes:

```
GET /api/v0/ledger/accounts/{accountId}/transaction-exports/{exportId}/download
```

Per [ADR-034](../../decisions/034-gateway-proxied-artifact-download.md), the chaos machine fetches
the artifact **server-side** and streams it to the browser. The presigned S3 URL never crosses the
chaos API boundary.

- The endpoint resolves the export through the ledger (`GET .../{exportId}` with the caller's token),
  takes the `downloadUrl` from the internal DTO, performs a **server-side GET** against it, and
  streams the bytes back.
- The response carries `Content-Type` derived from the export's `format` (`text/csv` /
  `application/pdf`) and an explicit
  `Content-Disposition: attachment; filename="statement-<account>-<from>-<to>.<ext>"`, so a PDF
  **downloads** instead of rendering inline and a CSV is not named after an S3 object key.
- Downloading an export that is **not `COMPLETED`** is a **409 Conflict** — it has no artifact.
- The presigned URL is **never logged**, at any level, and never appears in a response header or
  body. This extends the ledger's own never-log rule (ledger ADR 071) across the gateway.
- The artifact stream is **size-bounded**; an artifact exceeding the configured maximum is refused
  rather than buffered or streamed indefinitely.

## Acceptance Criteria

- [ ] `GET .../{exportId}/download` on a `COMPLETED` export returns **200** with the artifact bytes
      **byte-for-byte identical** to what the presigned URL serves.
- [ ] The response carries `Content-Disposition: attachment; filename="…"` and the format's
      `Content-Type` (`text/csv` / `application/pdf`).
- [ ] The filename is derived from the account and the resolved window — e.g.
      `statement-ASSET.PLATFORM.FLOAT-20260601-20260701.pdf` — and is **sanitized**: no CR/LF (header
      injection), no quotes, no path separators; characters outside `[A-Za-z0-9._-]` are replaced.
      An account with no resolvable code falls back to the account id.
- [ ] `PENDING`, `IN_PROGRESS`, `FAILED`, `CANCELLED` → **409** with a message naming the status.
- [ ] Ledger 403/404 on the resolve hop propagate as **403/404**
      ([ADR-035](../../decisions/035-faithful-status-propagation-on-ledger-command-proxy.md)).
- [ ] An S3 fetch that fails (expired/revoked URL, object gone, store down) yields **502 Bad Gateway**
      — never a 200 with a truncated or empty body.
- [ ] An artifact larger than `chaos.statements.max-artifact-bytes` is refused with **502** and the
      stream is abandoned; the gateway never buffers it whole in memory.
- [ ] **The presigned URL appears in no log line at any level** — asserted by a log-capture test over
      a full download, including the failure paths.
- [ ] The outbound S3 request carries **no `Authorization` header**.

## Technical Design

Target: **Java 25**, Spring Boot 4, Spring MVC. Streams on a virtual thread (Tomcat request threads
are already virtual — `spring.threads.virtual.enabled: true`).

```mermaid
sequenceDiagram
    autonumber
    participant UI as Chaos Admin
    participant CTRL as LedgerExportController
    participant CL as LedgerClient
    participant L as ss-ledger-service
    participant AF as ArtifactFetcher<br/>(no auth header, no logging interceptor)
    participant S3 as S3 / LocalStack

    UI->>CTRL: GET …/transaction-exports/{exportId}/download  (Bearer)
    CTRL->>CL: getExport(callerToken, accountId, exportId)
    CL->>L: GET /api/v0/accounts/{id}/transaction-exports/{exportId}
    L-->>CL: 200 { status: COMPLETED, downloadUrl: "https://s3…?X-Amz-Signature=…" }
    alt status != COMPLETED
        CTRL-->>UI: 409 Conflict ("export is PENDING — no artifact yet")
    else COMPLETED
        CTRL->>AF: fetch(downloadUrl)
        AF->>S3: GET (presigned; NO Authorization header)
        S3-->>AF: 200 octet stream
        AF-->>CTRL: InputStream (size-checked)
        CTRL-->>UI: 200 + Content-Disposition: attachment; filename="statement-….pdf"<br/>+ Content-Type: application/pdf<br/>(streamed — never fully buffered)
    end
    Note over CTRL,S3: the presigned URL exists only inside this box.<br/>It is never serialized, never logged, never returned.
```

**Why a dedicated HTTP client** (`ArtifactFetcher`), rather than reusing `ledgerProxyRestClient`:

1. **No `Authorization` header.** The presigned URL *is* the credential; an extra bearer header on a
   SigV4-presigned request is at best ignored and at worst breaks the signature. Every existing
   `LedgerClient` call unconditionally sets one.
2. **No logging interceptor.** `LoggingClientHttpRequestInterceptor` logs request URIs. Attaching it
   here would write the presigned capability — the one thing the ledger refuses to log — straight
   into the chaos logs.
3. **No base URL.** The presigned URL is absolute and points at S3, not the ledger.
4. **Its own timeouts.** An artifact transfer is a different animal from a 100 ms JSON read.

**Streaming, not buffering.** Return a `ResponseEntity<StreamingResponseBody>` (or
`InputStreamResource`), copying the S3 response body straight to the servlet output stream with a
bounded counter that aborts past `max-artifact-bytes`. Do **not** `readAllBytes()` — a multi-MB
statement held whole in the gateway's heap for every concurrent download is exactly the failure mode
[ADR-034](../../decisions/034-gateway-proxied-artifact-download.md) accepts bytes-through-the-gateway
*on the condition* of avoiding.

**Circuit-breaker boundary.** The ledger hop rides the existing `CircuitBreaker`. The **S3 hop does
not** — it is a different dependency, and tripping the ledger's breaker on an object-store outage
would take out the read proxy (accounts, balances, transactions) over a failure that has nothing to
do with the ledger. An S3 failure is a **502** on this endpoint only, and that blast radius is
correct.

## Implementation Notes

Files to create (`chaos-machine/src/main/java/com/softspark/chaos/ledgerproxy/`):

- `ArtifactFetcher.java` — `@Component`. Wraps a **dedicated** `RestClient` (or the JDK `HttpClient`
  directly) built with **no base URL, no interceptor, no auth**, and its own timeouts from
  `chaos.statements.artifact.*`. Exposes something like
  `ArtifactStream fetch(URI presignedUrl, long maxBytes)`, where `ArtifactStream` carries the
  `InputStream` and the upstream `Content-Length` when present. Throws `BadGatewayException` on any
  non-2xx, on an over-size artifact, and on an I/O failure. **Its javadoc must state that the URI is
  a bearer capability and must never be logged** — that is the one rule a future editor is most
  likely to break.
- `StatementFilenameFactory.java` — pure function
  `String filenameFor(LedgerTransactionExportDto export, @Nullable String accountCode)`. Format:
  `statement-{accountCode|accountId}-{rangeFrom:yyyyMMdd}-{rangeTo:yyyyMMdd}.{csv|pdf}`. **Sanitize**
  to `[A-Za-z0-9._-]`, collapse runs, strip CR/LF, cap the length (~120 chars). Unit-tested in
  isolation — this is the header-injection surface.

Files to modify:

- `LedgerExportController.java` (Task 001) — add the `/download` handler.
- `LedgerClient.java` — reuse Task 001's `getExport`. Optionally add `getAccount` reuse for the
  account code (the client already has `getAccount`); a failed/slow account lookup must **degrade to
  the account id in the filename**, never fail the download.
- `application.yml`:

  ```yaml
  chaos:
    statements:
      artifact:
        connect-ms: ${CHAOS_STATEMENT_ARTIFACT_CONNECT_MS:5000}
        read-ms: ${CHAOS_STATEMENT_ARTIFACT_READ_MS:60000}
      max-artifact-bytes: ${CHAOS_STATEMENT_MAX_ARTIFACT_BYTES:52428800}   # 50 MiB
  ```

  Follow the existing `${ENV_VAR:default}` convention on every leaf. Bind as a record
  `@ConfigurationProperties(prefix = "chaos.statements")` registered in
  `config/ChaosPropertiesConfiguration.java` alongside `ChaosLimits`/`OutboxProperties`.

Traps:

- The presigned URL must be passed **as-is**. Do not re-encode it, re-build it through a
  `UriBuilder`, or normalize it — its query string is part of the SigV4 signature and any
  re-encoding invalidates it. Take the `URI` from the DTO and hand it to the client unchanged.
- The ledger mints a **fresh** URL on every `GET .../{exportId}`, so the download endpoint resolves
  the export **on every call**. Never cache the URL, and never persist it (that is precisely what the
  ledger refuses to do).
- Set `Content-Length` on the response **only** when S3 reports one, so a chunked upstream doesn't
  produce a lying header.
- `Content-Disposition` filename goes through the sanitizer, quoted. A raw account name (operator
  free-text) must never reach the header unsanitized.

## Non-Functional Requirements

- **Memory:** O(buffer), not O(artifact). One bounded copy buffer (8–32 KiB) per download.
- **Latency:** two hops (ledger resolve + S3 GET). Dominated by the S3 transfer.
- **Security:** the presigned URL exists only in backend memory for the life of one request. Not
  logged, not persisted, not returned, not cached. No `Authorization` header on the S3 request.
  Filename sanitization prevents response-header injection.
- **Resilience:** an S3 outage degrades **this endpoint only** (502) — it does not trip the ledger
  circuit breaker and cannot take down the read proxy.
- **Bounded:** `max-artifact-bytes` caps what the gateway will relay, so a pathological ledger
  artifact cannot exhaust the harness.

## Dependencies

- **Task 001** — blocking. Needs `LedgerTransactionExportDto` (which carries `downloadUrl`),
  `LedgerClient.getExport`, `LedgerExportController`, and `LedgerStatusPropagation`.
- The ledger's S3 endpoint must be reachable **from the chaos backend** (not from the browser — that
  is the point of [ADR-034](../../decisions/034-gateway-proxied-artifact-download.md)). In dev this is
  the LocalStack/MinIO address the ledger itself uses.
- No new build dependency: JDK `HttpClient` / Spring `RestClient` and `StreamingResponseBody` are
  already on the classpath.

## Risks & Mitigations

- **Risk:** the presigned URL gets logged — by the interceptor, by a debug statement, by an exception
  message that embeds the request URI. This is the sharpest edge in the phase. **Mitigation:** a
  dedicated client with no logging interceptor; a log-capture test that runs a full download plus the
  failure paths and asserts the signature/host never appears; the never-log rule stated in
  `ArtifactFetcher`'s javadoc; exception messages reference the **export id**, never the URL.
- **Risk:** a large artifact OOMs the gateway. **Mitigation:** stream with a bounded buffer; enforce
  `max-artifact-bytes` while copying, not just from `Content-Length` (which a peer can omit or lie
  about).
- **Risk:** a slow S3 pins request threads. **Mitigation:** virtual threads make the hold cheap; the
  dedicated read timeout bounds it.
- **Risk:** URL expiry mid-download. **Mitigation:** the URL is minted fresh per request and the TTL
  is 15 minutes; a transfer that outlives it fails as a 502 and the operator simply retries — nothing
  is cached, so a retry is always clean.

## Testing Strategy

- **Unit:** `StatementFilenameFactory` — normal case; account code with dots (`ASSET.PLATFORM.FLOAT`
  survives); missing account code → id fallback; **CR/LF and quote injection attempts are stripped**;
  over-long names are capped; both extensions.
- **Unit:** `ArtifactFetcher` — non-2xx → `BadGatewayException`; over-size → `BadGatewayException` and
  the stream is closed; `Content-Length` absent → still streams; **no `Authorization` header on the
  outbound request** (asserted via `MockRestServiceServer` / a stub server).
- **Web (`@WebMvcTest` + `@MockitoBean LedgerClient` + `@MockitoBean ArtifactFetcher`):** `COMPLETED`
  → 200 with the exact headers; each non-`COMPLETED` status → 409 naming the status; ledger 403/404
  propagate; S3 failure → 502.
- **Log-capture test** (the ADR-034 rule, asserted directly, not assumed): drive a successful download
  and each failure path with a `ListAppender` on the root logger at `TRACE`; assert **no** emitted
  event contains the presigned URL's host, path, or `X-Amz-Signature`.
- **Integration** (`src/integration-test`, `spring-boot-restclient` is already on that source set's
  classpath): stub ledger + stub artifact server; end-to-end `GET /download` → byte-identical
  content, correct `Content-Disposition`, and a streamed (not buffered) transfer of an artifact
  larger than the copy buffer.

## Deployment Strategy

Additive: one new route, one config block with safe defaults, no migration, no dependency. Ships with
the phase.

The only operational prerequisite beyond Task 001's is **network reachability from the chaos backend
to the ledger's object store**. If the chaos machine cannot reach S3/LocalStack, exports still
create, poll, list, and cancel correctly — only `/download` fails, with a 502 that names the artifact
store rather than blaming the ledger. Rollback is a redeploy; nothing persists.
