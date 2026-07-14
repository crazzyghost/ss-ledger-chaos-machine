# ADR 035 - Faithful status propagation on the ledger command proxy

## Status

Accepted

## Context

Every method on `LedgerClient` translates ledger errors with the same two lines:

```java
.onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
    throw new NotFoundException("Ledger returned: " + resp.getStatusCode().value()); })
.onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
    throw new InternalServerErrorException("Ledger error: " + resp.getStatusCode().value()); })
```

**Every 4xx becomes a 404.** This is deliberate and documented (`LedgerBalanceDto`'s javadoc notes
that "a 400 for a bad/future `asOf` is translated to a `NotFoundException` like every other 4xx on
this proxy"), and for the existing surface it is defensible: those endpoints are pure reads whose
only realistic client error *is* "no such account", the response body carries nothing the operator
needs, and collapsing the rest costs little.

The statement export proxy ([ADR-033](033-account-statements-via-ledger-export-proxy.md)) breaks that
assumption completely. Its ledger endpoints return **five different 4xx codes that mean five
different things**, and the operator must be able to tell them apart:

| Ledger status | What it actually means | Collapsed to 404, the operator sees |
|---|---|---|
| **400** | The window is invalid — `from >= to`, missing `to` on a `CUSTOM` range, unknown `format`/`rangeType`, or a window wider than 366 days. The body names the offending field. | "Not found" — with no idea which field is wrong |
| **401** | The token expired. | "Not found" — instead of being signed out |
| **403** | The token lacks `ledger_account_transactions:export::allow`, **or** the account is a SYSTEM account (the entire chart of accounts) and the caller is not a super-user. | "Not found" — the single most likely failure for a chaos operator, rendered as a lie |
| **404** | Genuinely no such account, or an export id that belongs to a different account. | Correct |
| **409** | Cancel was called on an export that is already `COMPLETED`/`FAILED`/`CANCELLED`. | "Not found" — the export is right there on screen |

A 403 that says "not found" on the accounts an operator most wants to export, and a 409 that says
"not found" about a row visibly rendered in the table, are not cosmetic defects — they make the
feature undiagnosable. The `200` vs `201` distinction on `PUT` (joined an already-active export vs
created a new one) is likewise real information the current pattern would discard, since the proxy
only ever passes `200`.

Options:

1. **Retrofit faithful mapping across all of `LedgerClient`.** Rejected for this phase. It changes
   the observable behavior of endpoints the frontend already handles — `isLedgerProxyUnavailable()`
   and several pages special-case today's shapes — turning a small additive feature into a
   cross-cutting behavior change with its own regression surface. Worth doing; not worth coupling to
   this.
2. **Accept the collapse for exports too.** Rejected — see the table.
3. **Introduce faithful mapping scoped to the export calls.** Chosen.

## Decision

**The export proxy propagates the ledger's status codes faithfully. The existing read methods keep
their current (documented) behavior, unchanged.**

- A shared helper — `LedgerStatusPropagation` in `ledgerproxy` — maps a ledger response onto the
  matching chaos `HttpException`: `400 → BadRequestException`, `401 → UnauthorizedException`,
  `403 → ForbiddenException`, `404 → NotFoundException`, `409 → ConflictException`, any other 4xx →
  `BadGatewayException`, 5xx → `InternalServerErrorException`. `CircuitBreakerOpenException` keeps
  its existing controller-level treatment.
- The helper **best-effort-parses the ledger's `ApiError` body** and carries its `message` through,
  so "the resolved export window exceeds the maximum of 366 days" reaches the operator instead of
  "Ledger returned: 400". A body that is absent or unparseable falls back to a generic per-status
  message — parsing failure must never mask the status.
- Only the new export methods use it. Retrofitting the read methods is left as a follow-up, noted
  here so the inconsistency is a recorded decision rather than a discovered surprise.
- The `PUT` proxy preserves the ledger's **`201` vs `200`** distinction (created vs joined the active
  duplicate) rather than normalizing both to `200` — the UI uses it to tell the operator which
  happened.

## Consequences

**Positive**

- The three failures a chaos operator will actually hit — no export authority, SYSTEM account
  without super-user, cancelling an already-finished export — arrive as 403/403/409 with the
  ledger's own message, and the UI can say something true about each.
- Expired tokens surface as 401, which the frontend's `registerUnauthorizedHandler` already turns
  into a clean sign-out. Under the old mapping the operator would have seen a spurious "not found"
  and stayed on a dead session.
- Validation errors name the field. The chaos machine re-validates nothing
  ([ADR-033](033-account-statements-via-ledger-export-proxy.md), following
  [ADR-015](015-trial-balance-via-ledger-read-proxy.md)), so the ledger's message is the *only*
  source of that information — discarding it would leave the operator with no signal at all.

**Negative / accepted trade-offs**

- **`ledgerproxy` now has two error-translation conventions**, and which one applies depends on which
  method you call. That is genuinely worse than one convention, and it is only worth it because the
  alternative — changing the behavior of endpoints the frontend already depends on — is a bigger,
  riskier change that belongs in its own phase. The helper is written to be the *target* convention,
  so the follow-up is a migration rather than a rewrite.
- Error messages from the ledger are **passed through to the operator**. Acceptable here: both
  services are internal, the operator is authenticated, and the messages are validation text, not
  internal state. It is still one more place where an upstream string reaches a user, and the
  fallback path must never let a malformed body turn into a 500.
- Faithful propagation means the chaos machine's API surface now **inherits the ledger's status
  semantics**, including any future ones. That is the point, but it does mean a new ledger status
  code becomes a chaos `BadGatewayException` (502) until the mapping learns about it — a deliberate,
  loud default rather than a silent misclassification.
