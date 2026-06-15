# Task 001 - Frontend Scaffold & API Client

## Functional Requirements
- Stand up the React + Vite + TypeScript app mirroring swift-admin's tooling, folder layout,
  Tailwind + shadcn/ui setup, react-query provider, router shell, and a fetch-based API client
  that attaches the bearer token and throws `ApiError` — pointed at the chaos gateway.

## Acceptance Criteria
- [ ] `npm run dev` serves the app (Vite, port 5173); `npm run build` produces static assets.
- [ ] Path alias `@ -> ./src`, Tailwind + shadcn `components.json`, `lucide-react` icons configured.
- [ ] `QueryClientProvider` + `RouterProvider` wired in `main.tsx` (mirrors swift-admin).
- [ ] `lib/api.ts` exposes a typed `request()` that sets `Authorization: Bearer <token>`,
      JSON-encodes bodies, and throws `ApiError(status, message)` on non-2xx.
- [ ] `lib/env.ts` reads `VITE_CHAOS_API_BASE_URL` into an `appConfig`.
- [ ] shadcn UI primitives present: `button, card, dialog, select, input, table, badge, tabs`.
- [ ] `AppShell` with sidebar nav + `ProtectedRoute` placeholder; `/login` public.

## Technical Design
Stack pinned to swift-admin: React 19, Vite 6 (`@vitejs/plugin-react-swc`), TypeScript ~5.8,
`react-router-dom` 7 (`createBrowserRouter`), `@tanstack/react-query` 5, Tailwind 3 +
shadcn/ui (Radix), `lucide-react`.

```
src
├── main.tsx                 # QueryClientProvider + RouterProvider
├── app/router.tsx           # createBrowserRouter: /login public, ProtectedRoute+AppShell tree
├── app/error-boundary.tsx
├── lib/
│   ├── api.ts               # fetch wrapper, Bearer, ApiError, resource fns
│   ├── env.ts               # appConfig from import.meta.env
│   └── auth.ts              # token storage + decode (Task 002)
├── components/
│   ├── layout/app-shell.tsx # sidebar nav arrays + <Outlet/>
│   └── ui/*                 # shadcn primitives
└── features/*               # Tasks 003–006
```

API client shape (mirrors swift-admin `lib/api.ts`):

```ts
export class ApiError extends Error { constructor(public status: number, message: string){ super(message);} }
async function request<T>(path: string, init?: RequestInit & { token?: string }): Promise<T> {
  const res = await fetch(`${appConfig.apiBaseUrl}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json",
               ...(init?.token ? { Authorization: `Bearer ${init.token}` } : {}),
               ...init?.headers },
  });
  if (!res.ok) throw new ApiError(res.status, (await safeJson(res))?.message ?? res.statusText);
  return res.status === 204 ? (undefined as T) : res.json();
}
```

Nav (sidebar) groups: **Operate** (Chaos Runner, Batches), **Accounts** (Chart of Accounts,
Virtual Accounts), **Ledger** (Transactions). A visible **target-cluster badge** (from
`GET /api/v0/meta` or health) warns which Kafka cluster is targeted.

## Implementation Notes
- Copy swift-admin's `vite.config.ts`, `tsconfig*.json`, `tailwind.config.ts`,
  `components.json`, `postcss.config.js`, `index.html`, `Dockerfile`, `nginx.conf` and adapt
  names/env. Replace Supabase deps with none (auth via gateway, Task 002).
- `.env.example`: `VITE_CHAOS_API_BASE_URL=http://localhost:27100/api/v0`.
- Reuse swift-admin `src/components/ui/*` primitives verbatim where possible.

## Non-Functional Requirements
- Strict TS. Manual-chunk vendor split (router/query) like swift-admin.
- No secrets in the bundle; only the gateway base URL.

## Dependencies
Chaos gateway (Phase 004) for runtime; standalone for scaffold.

## Risks & Mitigations
- *Drift from swift-admin conventions* → port its config + ui folder directly; review against it.

## Testing Strategy
- Vitest: `request()` attaches Bearer, parses `ApiError`, handles 204.
- Render smoke test of `AppShell` + router.

## Deployment Strategy
Static build behind nginx (mirror swift-admin). `VITE_CHAOS_API_BASE_URL` per environment.
