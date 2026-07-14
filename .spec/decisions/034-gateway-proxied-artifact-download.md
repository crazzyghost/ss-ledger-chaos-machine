# ADR 034 - Statement artifacts stream through the gateway; the presigned URL never reaches the browser

## Status

Accepted

## Context

The ledger's export API hands a completed statement over as a **presigned S3 GET URL**:
`GET /api/v0/accounts/{accountId}/transaction-exports/{exportId}` returns
`downloadUrl` + `downloadUrlExpiresAt`, freshly minted per read with a default TTL of `PT15M`
(ledger ADR 071). The ledger's own design intent is that the caller then fetches the object
**directly from S3** — "bytes never proxy through the service".

So the chaos machine has a fork: pass that URL through to the operator's browser, or fetch the
object itself and stream the bytes back.

Facts that bear on the choice, verified in `ss-ledger-service-beta`:

- The object is uploaded with a **`Content-Type`** (`text/csv` / `application/pdf`) but **no
  `Content-Disposition`** (`S3ArtifactStore.MultipartSink` sets `contentType` only), and
  `presignedGetUrl` adds **no `response-content-disposition` / `response-content-type` override**.
  So a browser hitting the presigned URL renders a PDF **inline in a tab** rather than downloading
  it, and names any download after the raw S3 object key.
- The `download` attribute on an `<a>` element is **ignored for cross-origin URLs**, so the frontend
  cannot force a filename or force a download from an S3 link either.
- The presigned URL **is a bearer capability**. Anyone holding it can fetch that account's statement
  until it expires, with no token and no authorization check. The ledger treats it accordingly: it
  is never logged, never persisted, and never put in a redirect or `Location` header.
- [ADR-003](003-backend-as-single-api-gateway.md) is explicit that **the React UI talks only to the
  chaos backend**. Every call the UI makes today goes to `/api/v0/**` on the chaos machine.
- The S3 endpoint is not necessarily reachable from the operator's browser at all. In a dev or CI
  environment it is LocalStack/MinIO on an internal address; the ledger reaches it, the browser may
  not.

## Decision

**The chaos machine fetches the artifact server-side and streams it to the browser. The presigned URL
is an internal implementation detail and never crosses the chaos API boundary.**

- A chaos-only endpoint, `GET /api/v0/ledger/accounts/{accountId}/transaction-exports/{exportId}/download`,
  calls the ledger's `GET .../{exportId}` with the caller's token, takes the `downloadUrl` from the
  response, performs a **server-side GET** against it, and streams the bytes back to the browser with
  `Content-Type` from the export's format and an explicit
  `Content-Disposition: attachment; filename="…"`.
- The chaos-facing export DTO **omits `downloadUrl` and `downloadUrlExpiresAt` entirely** and exposes
  a derived boolean instead. The URL is deserialized into an internal DTO used only by the client and
  the download endpoint; it is never serialized back out. This is enforced by a test, not just by
  convention.
- The server-side fetch uses a **dedicated HTTP client with no `Authorization` header and no logging
  interceptor**: sending a bearer token alongside a presigned SigV4 request would break the
  signature, and the existing `LoggingClientHttpRequestInterceptor` logs request URIs — which would
  write the capability straight into the logs, violating the ledger's never-log rule.

## Consequences

**Positive**

- **The single-gateway invariant holds.** The UI still talks to exactly one origin. No S3 CORS
  configuration, no browser reachability requirement on the object store, no third origin in the
  frontend's threat model.
- **The capability stays server-side.** The presigned URL never lands in browser memory, `history`,
  a devtools network log, or a copy-pasted link. It has a 15-minute TTL and an authorization-free
  bearer capability inside it; keeping it in the backend is strictly safer, and it extends the
  ledger's own never-log rule across the gateway.
- **The download actually behaves like a download.** The chaos machine sets
  `Content-Disposition: attachment` with a meaningful filename
  (`statement-<accountCode>-<from>-<to>.pdf`), so a PDF saves instead of opening inline and a CSV is
  not named after a UUID-laden object key.
- Works identically in dev (LocalStack), staging, and production, because only the backend ever
  needs to reach the object store.

**Negative / accepted trade-offs**

- **Bytes transit the gateway.** This is the decision's real cost, and it deliberately reverses the
  ledger's "bytes never proxy" stance *for this hop*. Accepted: the chaos machine is a
  single-operator test harness, statements over a ≤366-day window on a chaos-seeded account are
  small (KBs to low MBs), and downloads are occasional and human-initiated. It would be the wrong
  trade for a multi-tenant production consumer serving many concurrent large statements — that
  consumer should take the presigned URL.
- The download endpoint holds a **streaming connection** for the duration of the transfer, occupying
  a (virtual) request thread and a socket to S3. Bounded by a configured maximum artifact size, and
  virtual threads make the hold cheap, but it is a resource the read proxy never consumed.
- **Two hops per download** (ledger `GET` for the URL, then S3 `GET`), so a download costs one extra
  round trip and depends on both the ledger *and* the object store being reachable. The ledger hop
  rides the existing circuit breaker; the S3 hop is a **separate dependency with no breaker** — an
  S3 outage surfaces as a failed download, not as a tripped ledger circuit.
- The frontend needs **blob-capable fetch plumbing** (`lib/api.ts` can only do JSON today, and there
  is no `Content-Disposition` parsing anywhere in the codebase). That is one new function, but it is
  genuinely new ground for this frontend.
- The chaos machine cannot hand an operator a shareable link to a statement. Nobody asked for one,
  and given the URL is an unauthenticated capability, not having one is a feature.
