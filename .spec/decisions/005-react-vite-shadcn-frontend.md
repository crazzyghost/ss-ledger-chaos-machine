# ADR 005 - React + Vite + shadcn/ui frontend (follows swift-admin)

## Status
Accepted

## Context
The MANIFEST requires the frontend's "structure and components follow swift-admin".
swift-admin is React 19 + Vite 6 + TypeScript 5.8 + `react-router-dom` 7
(`createBrowserRouter`) + `@tanstack/react-query` 5 + Tailwind 3 + shadcn/ui (Radix
primitives) + `lucide-react`, organized feature-first with a fetch-based `src/lib/api.ts`
(Bearer token, `ApiError`), `ProtectedRoute` + `AppShell`, and a custom `Table` +
`ListPagination` + `StatePanel` list pattern. It currently authenticates via **Supabase**.

## Decision
Mirror swift-admin's stack, folder layout, API-client shape, routing, and list/table
conventions **exactly**, with one substitution: **replace Supabase auth with the chaos
backend's AUTH SERVICE token flow**. The login page POSTs credentials to
`/api/v0/auth/login`, stores the returned access token (localStorage), a `session-provider`
exposes it, `ProtectedRoute` redirects unauthenticated users to `/login`, and `lib/api.ts`
attaches `Authorization: Bearer <token>`.

Reuse swift-admin's conventions: react-query resource hooks with resource-based query keys,
`useState`-based forms with manual validation, Radix/shadcn `Dialog` for create flows,
`StatePanel`/`InlineNotice`/`TableLoadingRows` for loading/empty/error states.

## Consequences
- (+) Component and code parity with swift-admin; primitives and patterns are copy-portable.
- (+) Single auth model aligned with the gateway backend.
- (−) The Supabase-specific `session-provider`/`lib/auth.ts` must be re-implemented against
  the token endpoint (documented in Phase 005 task 002).
- (−) No `zod`/`react-hook-form` in swift-admin today; CSV/flow forms use manual validation.
  We note `zod` as an optional hardening for CSV schema validation but do not introduce it by
  default, to preserve "follow swift-admin".
