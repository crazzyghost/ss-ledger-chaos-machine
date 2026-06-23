# ADR-015 — Surface the ledger trial balance through the existing ledger read proxy

- **Status:** Accepted
- **Date:** 2026-06-23
- **Phase:** [012 — Trial Balance Reporting](../phases/012-trial-balance-reporting/DESIGN.md)
- **Relates to:** [ADR-003 — Backend as single API gateway](003-backend-as-single-api-gateway.md);
  [Phase 004 / task 002 — Ledger read proxy](../phases/004-gateway-auth-ledger-proxy/002-ledger-read-proxy.md)

## Context

The Trial Balance feature (idea `005_trial_balance.md`) needs to display an **unadjusted
trial balance over a selected period**. The authoritative computation already lives in
`ss-ledger-service`: a `ReportingController` at `GET /api/v0/reporting/trial-balance`
(`from: Instant`, `to: Instant`, optional `currency: String`) returns a `TrialBalanceResponse`
(period echo + `totalDebits`/`totalCredits`/`isBalanced`/`numberOfAccounts` + per-account
rows). The chaos machine computes nothing here — it owns no journals or balances ([ADR-003]).

The UI cannot call the ledger directly: the chaos backend is the **single gateway** for the
React app ([ADR-003]). So the chaos backend must expose a chaos-side endpoint that read-proxies
to the ledger. The question is **where** that endpoint and its supporting code live:

- **Option A — a new `reporting` package + `/api/v0/reporting/trial-balance`** that mirrors the
  ledger's own module name and path one-for-one.
- **Option B — extend the existing ledger read proxy** (`com.softspark.chaos.ledgerproxy`,
  `LedgerReadController` at `/api/v0/ledger`) with `GET /api/v0/ledger/reporting/trial-balance`.

The existing `ledgerproxy` package already solves every cross-cutting concern this endpoint
needs: a configured `ledgerProxyRestClient` (timeouts + request logging), per-request Bearer
token forwarding (caller token, falling back to a static service token), a `CircuitBreaker`,
and a uniform `4xx → NotFoundException` / `5xx → InternalServerErrorException` translation.

## Decision

**Extend the existing ledger read proxy (Option B).** Add a `getTrialBalance(...)` method to
`LedgerClient` and a `GET /api/v0/ledger/reporting/trial-balance` handler to
`LedgerReadController`, with two new DTO records (`TrialBalanceDto`, `TrialBalanceEntryDto`) in
the `ledgerproxy` package mirroring the ledger's response. The call runs inside the **same
circuit breaker and token-forwarding path** as every other ledger read.

Why Option B wins:

- **The trial balance *is* a ledger read.** It belongs to the same trust boundary, resilience
  posture, and auth model as `/accounts` and `/transactions` — not a separate concern.
- **Zero new infrastructure.** No second `RestClient` bean, no second circuit breaker, no new
  package wiring. The endpoint inherits the proven proxy behaviour for free, so a flaky/slow
  ledger degrades identically to existing reads (open circuit → `503`-style error).
- **Path honesty.** `/api/v0/ledger/...` tells the operator the data is *read-through from the
  ledger*, consistent with the rest of the proxy surface, rather than implying the chaos
  machine itself is a reporting authority.

The frontend therefore consumes `GET /api/v0/ledger/reporting/trial-balance` (not the ledger's
own `/api/v0/reporting/...` path, which it cannot reach).

## Consequences

**Positive**

- Inherits timeouts, retries, circuit breaker, request logging, and token forwarding with no
  new code paths to test or operate.
- Keeps the proxy surface coherent: one package, one prefix, one resilience story for all
  ledger reads. A future reader finds all read-through endpoints in one place.
- The DTOs are thin pass-throughs, so the chaos machine stays a transparent gateway and does
  not fork the ledger's report semantics.

**Negative / trade-offs**

- The chaos path (`/api/v0/ledger/reporting/trial-balance`) **diverges from the ledger path**
  (`/api/v0/reporting/trial-balance`). The idea's wording matches the ledger path; the small
  rename is documented here and in the task so it is not mistaken for a bug.
- `LedgerReadController` / `LedgerClient` grow a non-account/non-transaction responsibility. If
  ledger reporting later expands (multiple report types, heavier payloads, caching), this is the
  point to revisit and split a dedicated `reporting` proxy package out — this ADR would then be
  superseded. Recorded as the natural seam.
- Period validation (`from < to`, span ≤ 366 days) stays authoritative in the ledger; the chaos
  proxy forwards the ledger's `400` rather than re-validating, accepting one network round-trip
  to learn a request was malformed. The frontend mitigates with a client-side guard.
