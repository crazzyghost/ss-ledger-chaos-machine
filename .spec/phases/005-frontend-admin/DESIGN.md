# Phase 5 - Frontend Admin

## Summary
A React + Vite + TypeScript admin UI that **follows swift-admin's** structure, components, and
conventions (react-router 7 `createBrowserRouter`, `@tanstack/react-query` 5, Tailwind +
shadcn/ui, fetch-based `lib/api.ts` with Bearer + `ApiError`, `ProtectedRoute`/`AppShell`,
custom `Table` + `ListPagination` + `StatePanel`). It talks only to the chaos gateway and
delivers: login, chart-of-accounts config, virtual-account create/list/detail, transactions
(with search) + per-VA history, and a chaos runner (single flow + CSV). (See
[ADR-005](../../decisions/005-react-vite-shadcn-frontend.md).)

## Motivation
The MANIFEST's frontend section: login screen, create/view virtual accounts, configure chart
of accounts, view all virtual accounts (+ per-VA transactions), and a transactions page with
search. Mirroring swift-admin keeps components copy-portable and the look/feel consistent.

## User-Facing Changes
A full operator console: sign in, configure the chart of accounts, register/announce virtual
accounts, browse accounts + transactions, drill into a VA's history, and drive single or CSV
chaos flows with live run progress.

## Architecture Impact
New React app (own repo dir / package) mirroring swift-admin's `src/` layout: `app/`,
`lib/`, `components/{layout,ui}`, `features/{auth,chart-of-accounts,virtual-accounts,
transactions,chaos}`. All data via the chaos gateway `/api/v0`. Supabase auth is **replaced**
by the gateway token flow.

## Edge Cases
- Token expiry mid-session → 401 interceptor → redirect to `/login`.
- Long-running CSV runs → poll `GET /batches/{id}` with react-query refetch interval.
- Degraded ledger proxy (`503`) → `StatePanel` degraded state, retry.
- Large transaction lists → server pagination + filters.
- Destructive chaos options → explicit confirm dialog + visible target-cluster label.

## Testing Strategy
- Component/unit (Vitest + Testing Library): api client (Bearer attach, `ApiError`),
  `ProtectedRoute`, list/table + pagination, forms validation, CSV upload.
- Integration (MSW mocking the gateway): login flow, CoA edit, VA create, transactions search,
  batch run progress.
- Mirror swift-admin's testing conventions where present.

## Deployment Strategy
Vite build → static assets behind nginx (mirror swift-admin's `Dockerfile`/`nginx.conf`).
`VITE_CHAOS_API_BASE_URL` points at the gateway. No CORS proxy needed (single origin via
gateway). Optional dev proxy mirrors swift-admin's `.env.proxy` for local cross-origin.

## Tasks
- [001 - Frontend scaffold & API client](001-frontend-scaffold-and-api-client.md)
- [002 - Auth flow: login & protected routes](002-auth-flow-login-and-protected-routes.md)
- [003 - Chart of accounts config page](003-chart-of-accounts-config-page.md)
- [004 - Virtual accounts pages](004-virtual-accounts-pages.md)
- [005 - Transactions & per-VA history pages](005-transactions-and-va-history-pages.md)
- [006 - Chaos runner (single & CSV)](006-chaos-runner-single-and-csv.md)

## Parallel Tasks
001 → 002 first (scaffold + auth). Then **003**, **004**, **005**, **006** in parallel (each a
self-contained feature folder consuming `lib/api.ts`).
