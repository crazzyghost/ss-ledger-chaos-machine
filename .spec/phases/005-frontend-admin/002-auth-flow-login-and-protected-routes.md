# Task 002 - Auth Flow: Login & Protected Routes

## Functional Requirements
- A login screen that retrieves an access token from the gateway, persists it, exposes it via a
  session provider, attaches it to API calls, and gates the app behind authentication —
  mirroring swift-admin's `login-page` / `session-provider` / `protected-route` structure but
  using the gateway's AUTH SERVICE token instead of Supabase.

## Acceptance Criteria
- [ ] `/login` posts to `POST /api/v0/auth/login` and on success stores the token + expiry.
- [ ] Token persists across reloads (localStorage) and is attached as `Bearer` on every request.
- [ ] `ProtectedRoute` redirects unauthenticated users to `/login`; authenticated users reach `AppShell`.
- [ ] A `401` from any API call clears the session and redirects to `/login`.
- [ ] Sign-out clears the token and returns to `/login`.
- [ ] `GET /api/v0/auth/me` populates the current principal shown in the shell.

## Technical Design
Mirror swift-admin's `features/auth/` trio, swapping the Supabase calls:

- `features/auth/login-page.tsx` — email/password form (shadcn `Input`/`Button`), `useMutation`
  → `login()`; on success store token + navigate to `/`.
- `features/auth/session-provider.tsx` — React context exposing `{ token, principal, loading,
  signIn, signOut }`; hydrates from `lib/auth.ts` on mount; calls `auth/me`.
- `features/auth/protected-route.tsx` — loading spinner; no token → `<Navigate to="/login"/>`;
  token present → `<Outlet/>` inside `AppShell`.
- `lib/auth.ts` — `getToken()/setToken()/clearToken()` (localStorage), optional JWT
  expiry-decode to pre-empt expiry; a `useAuthToken()` hook.
- `lib/api.ts` — reads the token from the session/storage and attaches it; a single place maps
  `401 → signOut()` (event or callback) to centralize redirect.

```mermaid
sequenceDiagram
  participant U as User
  participant LP as LoginPage
  participant API as lib/api.ts
  participant GW as Chaos Gateway
  U->>LP: submit email/password
  LP->>API: login(email,password)
  API->>GW: POST /api/v0/auth/login
  GW-->>API: { access_token, expires_in }
  API-->>LP: ok → setToken(); navigate /
  Note over API,GW: subsequent calls send Bearer; 401 → clear + /login
```

## Implementation Notes
- No Supabase. Token in `localStorage` key `chaos.token`. Consider in-memory + storage hybrid
  to reduce XSS exposure; document the tradeoff (operator-tool context).
- Wrap the router tree with `SessionProvider`; `ProtectedRoute` consumes it.
- Centralize the `401 → signOut` handling in `lib/api.ts` (e.g. a registered callback).

## Non-Functional Requirements
- No credential persistence (only the token). Token never logged.
- Redirect-on-expiry is seamless (no infinite loops on `/login`).

## Dependencies
Task 001 (scaffold, api client), Phase 004 Task 001 (gateway auth endpoints).

## Risks & Mitigations
- *XSS token theft via localStorage* → note risk; keep tokens short-lived (gateway TTL);
  optionally move to in-memory + silent refresh.
- *Redirect loops* → `/login` excluded from the protected tree; guard against re-entrancy.

## Testing Strategy
- Vitest + Testing Library + MSW: login success/failure, token attach, 401 → redirect, sign-out.
- `ProtectedRoute` redirect vs render tests.

## Deployment Strategy
Token TTL governed by the AUTH SERVICE. No build flag.
