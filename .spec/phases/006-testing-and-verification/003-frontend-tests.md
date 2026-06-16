# Task 003 - Frontend Tests

## Functional Requirements
- Component and integration tests for the React admin app, mocking the chaos gateway, covering
  auth, the list/table+search pattern, forms, the chaos runner, and error/degraded states —
  following swift-admin's testing conventions.

## Acceptance Criteria
- [ ] `npm run test` (Vitest) is green in CI.
- [ ] `lib/api.ts` tests: Bearer attach, `ApiError` on non-2xx, 204 handling, `401 → signOut`.
- [ ] Auth: login success/failure, token persistence, `ProtectedRoute` redirect vs render, sign-out.
- [ ] List pattern: virtual-accounts + transactions pages render, paginate, filter/search
      (incl. search by VA id across source/destination), and show loading/empty/error states.
- [ ] Chaos runner: catalog-driven dynamic form per flow (all 12 incl. disbursement), single
      run result, chaos-option caps + confirm dialog, CSV upload → run page polling → terminal.
- [ ] CoA page: roles + flow-slot edit; VA create dialog (incl. announce); degraded ledger-proxy state.

## Technical Design
- Vitest + `@testing-library/react` + `@testing-library/user-event`.
- **MSW** (Mock Service Worker) handlers mock `/api/v0/**` (auth, flows catalog, flows, batches,
  history, virtual-accounts, chart-of-accounts, ledger reads) for deterministic component tests.
- Render with the real react-query `QueryClient` + router in test wrappers (mirror swift-admin
  test utilities).

```mermaid
flowchart LR
  test[Vitest + Testing Library] --> comp[Components/pages]
  comp --> api[lib/api.ts]
  api --> msw[[MSW: /api/v0 handlers]]
```

## Implementation Notes
- Co-locate tests as `*.test.tsx` beside features (swift-admin convention) + `src/test/msw/handlers.ts`.
- Provide a `renderWithProviders` helper (QueryClientProvider + MemoryRouter + SessionProvider).
- Drive chaos-runner form tests from a mocked `GET /flows/catalog` so the 12-flow coverage is data-driven.

## Non-Functional Requirements
- Suite < 30s; no real network (MSW intercepts). Deterministic timers for polling tests.

## Dependencies
Phase 005 (frontend code under test). Mirrors swift-admin testing setup.

## Risks & Mitigations
- *Polling tests flakiness* → fake timers; assert on query-state transitions, not wall-clock.
- *Catalog/UI drift* → catalog-driven tests assert the renderer handles every flow descriptor.

## Deployment Strategy
`npm run test` in CI on PRs touching the frontend.
