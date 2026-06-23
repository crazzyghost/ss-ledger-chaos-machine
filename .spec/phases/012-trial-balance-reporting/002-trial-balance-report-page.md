# Task 002 - Trial Balance report page (frontend)

## Functional Requirements

- Add a **Trial Balance** item to the side-nav's **Ledger** section.
- Add a `/trial-balance` route rendering a read-only report page.
- Let the operator choose a **period two ways** (both supported, kept in sync):
  1. a **period quick-picker** (month select → sets the date range to that month), and
  2. editable **From / To date inputs** (native `type="date"`) as the source of truth.
- Expose an optional **currency** filter (`Select`, default "All currencies").
- On **Apply**, call `GET /api/v0/ledger/reporting/trial-balance` (task 001) and render:
  - a **summary header**: total debits, total credits, a **Balanced / Out of balance** badge
    (`isBalanced`), number of accounts, and the echoed period.
  - a **table** of per-account rows: account code, account name, ownership type, currency,
    debits, credits, net movement — ordered as returned (by `accountCode` ASC).
- Handle loading, error, and empty states; guard obviously-invalid periods client-side
  (`from >= to`, span > 366 days) before calling the API.

## Acceptance Criteria

- [ ] "Trial Balance" appears in the sidebar under the Ledger group and routes to `/trial-balance`;
      the nav item highlights when active.
- [ ] The page defaults to a sensible period (current month: `from` = first of month,
      `to` = first of next month) and loads on mount.
- [ ] Selecting a month in the quick-picker updates both From and To inputs; editing From/To
      directly updates the range used for the next Apply (manual edits win over the picker).
- [ ] The currency `Select` offers "All currencies" + the known ISO codes; choosing one scopes
      the report; "All currencies" omits the param.
- [ ] Summary header shows formatted debits/credits/net (money formatting) and a green
      **Balanced** badge when `isBalanced`, a red/warning **Out of balance** badge otherwise.
- [ ] Table renders one row per `accounts[]` entry with formatted money and an ownership-type badge;
      `accountOwnerId` shown only for ORGANIZATION rows (`—` for SYSTEM).
- [ ] Loading shows skeleton rows; an API error shows a `StatePanel` with a Retry; an empty
      `accounts[]` shows an empty `StatePanel`.
- [ ] A client-side guard blocks Apply (with an inline message) when `from >= to` or the span
      exceeds 366 days, so the obvious bad cases never round-trip; the ledger's `400` is still
      surfaced gracefully if it slips through.
- [ ] The query is keyed by the applied `{from, to, currency}` and refetches on Apply, not on every keystroke.

## Technical Design

Stack: **React 19 + Vite 6 + react-router 7 + react-query 5 + Tailwind + shadcn/ui**
([ADR-005](../../decisions/005-react-vite-shadcn-frontend.md)). The page mirrors the
`features/transactions` read-page pattern (draft vs. applied filter state, `useQuery`, shadcn
`Table`, `StatePanel`) — **minus pagination**, since the trial balance is a single aggregate.

```mermaid
flowchart TD
    nav["Sidebar: Ledger ▸ Trial Balance"] --> route["/trial-balance"]
    route --> page["TrialBalancePage"]
    page --> filters["Period quick-picker + From/To dates + currency Select<br/>(draft → applied on Apply)"]
    filters -->|applied {from,to,currency}| q["useQuery(['trial-balance', applied])<br/>getTrialBalance(token, applied)"]
    q -->|TrialBalanceResponse| summary["Summary header:<br/>totals + Balanced badge + #accounts"]
    q --> table["Per-account table<br/>(code, name, ownership, ccy, dr, cr, net)"]
    q -->|isLoading| skel["TableLoadingRows"]
    q -->|error| err["StatePanel danger + Retry"]
    q -->|accounts == []| empty["StatePanel empty"]
```

### Period model

- State holds `from`/`to` as `YYYY-MM-DD` strings (native date inputs) plus a `currency` string.
- Quick-picker is a month `Select` (e.g. last ~12 months + current). Choosing a month sets
  `from = first day`, `to = first day of next month`. The date inputs remain editable and
  override the picker.
- On Apply, convert to instants for the API: `from → ${from}T00:00:00.000Z`,
  `to → ${to}T00:00:00.000Z` (the ledger treats `to` as **exclusive**, so "June" = from
  `2026-06-01` to `2026-07-01`). Reuse/extend the `toISOFrom` helper pattern from
  `transactions-page.tsx`; note `to` here is start-of-day exclusive, **not** the `23:59:59.999`
  inclusive form used by the transactions filter — keep a local converter to avoid confusion.

### API client addition (`lib/api.ts`)

```ts
export type TrialBalanceEntry = {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountOwnerId: string | null;
  accountOwnershipType: "SYSTEM" | "ORGANIZATION";
  currency: string;
  totalDebits: string;     // BigDecimal → string over the wire
  totalCredits: string;
  netMovement: string;
};

export type TrialBalanceResponse = {
  from: string;
  to: string;
  currency: string | null;
  totalDebits: string;
  totalCredits: string;
  isBalanced: boolean;
  numberOfAccounts: number;
  accounts: TrialBalanceEntry[];
};

export function getTrialBalance(
  token: string,
  params: { from: string; to: string; currency?: string }
): Promise<TrialBalanceResponse> {
  return request<TrialBalanceResponse>("/ledger/reporting/trial-balance", {
    token,
    query: { from: params.from, to: params.to, currency: params.currency || undefined }
  });
}
```

> Money values arrive as `BigDecimal` → JSON; treat them as **strings** and format with the
> existing `formatMoney`/number util rather than parsing to `number` (avoids float drift on
> ledger amounts).

## Implementation Notes

Files to create:
- `chaos-admin/src/features/trial-balance/trial-balance-page.tsx` — `TrialBalancePage` (uses
  `Page`/`PageHeader`/`PageContent`), filter bar, summary header, table, states.
- *(optional)* `chaos-admin/src/features/trial-balance/period-picker.tsx` — the month
  quick-picker + From/To inputs, if it's cleaner to extract; otherwise inline in the page.

Files to modify:
- `chaos-admin/src/components/layout/app-shell.tsx` — add to `ledgerNavigation`:
  `{ to: "/trial-balance", label: "Trial Balance", icon: Scale }` and import `Scale` from
  `lucide-react`.
- `chaos-admin/src/app/router.tsx` — lazy-import `TrialBalancePage` and add
  `{ path: "/trial-balance", element: withSuspense(<TrialBalancePage />) }` in the AppShell
  children (Ledger area).
- `chaos-admin/src/lib/api.ts` — add `TrialBalanceEntry`, `TrialBalanceResponse`, `getTrialBalance`.

Reuse:
- `Page`, `PageHeader`, `PageContent` from `@/components/layout/page`.
- `Table`, `THead`, `TBody`, `TR`, `TH`, `TD`, `TableContainer`, `TableLoadingRows` from `@/components/ui/table`.
- `Select` (with `options`) for currency + month; `Input type="date"`; `Button`; `Badge`; `Card`.
- `StatePanel`, `ListPagination` (not needed here), `getErrorMessage`, `useSession()` for the token,
  `formatMoney`/`formatDate`/`formatEnumValue` from `@/lib/utils`.

No new dependency: the existing codebase has **no calendar primitive** and uses native
`type="date"` inputs — match that; do **not** add a date-picker library.

## Non-Functional Requirements

- **Responsiveness:** summary header + table stack cleanly on narrow screens; long account names
  truncate (`max-w` + `truncate`) like the transactions table.
- **Accessibility:** the Balanced/Out-of-balance badge carries text, not color alone; date inputs
  and selects are labelled.
- **Perf:** single query per Apply; `keepPreviousData` so the table doesn't flash on period change.
- **Auth:** all calls carry the bearer token via the existing `request()` wrapper; the page sits
  behind the protected-route shell like every other feature.

## Dependencies

- **Task 001** (backend `GET /api/v0/ledger/reporting/trial-balance`) for live data; the page can
  be developed against an **MSW** fixture of `TrialBalanceResponse` in parallel and wired to the
  real endpoint once 001 lands.
- Existing layout/router/api-client foundation
  ([Phase 005 / task 001](../005-frontend-admin/001-frontend-scaffold-and-api-client.md)).

## Risks & Mitigations

- **`to` exclusivity confusion** (a "June" report must send `to = 2026-07-01`, not `06-30`):
  encapsulate in a local converter + a unit test; show the human period ("June 2026") in the
  header derived from the echoed `from`/`to`.
- **BigDecimal-as-number drift:** keep amounts as strings and format; covered by a render test
  with a high-precision value.
- **Large account lists** (wide period, all currencies): acceptable for an operator tool; the
  table simply scrolls. If needed later, add a client-side account-code filter (out of scope).
- **Currency option source:** the `Select` can hardcode common ISO codes or reuse the currencies
  feature's list; default to a small static list + "All currencies" to avoid coupling this page to
  the currencies query (note it as a follow-up if a full list is wanted).

## Testing Strategy

- **Component tests (Vitest + Testing Library + MSW):**
  - nav item renders and routes to `/trial-balance`;
  - default period loads (current month) and renders summary + rows from the fixture;
  - choosing a month updates From/To; editing From/To overrides the picker;
  - currency select scopes the request (asserts the outgoing query param);
  - `isBalanced=false` renders the **Out of balance** badge;
  - loading shows skeletons; MSW 500 shows the error `StatePanel` + Retry refetches;
  - empty `accounts[]` shows the empty state;
  - client-side guard blocks Apply on `from >= to` and on a > 366-day span.
- Fold into the Phase 006 frontend suite ([006 / task 003](../006-testing-and-verification/003-frontend-tests.md)).

## Deployment Strategy

Additive frontend-only change behind the existing auth shell; ships as a normal frontend deploy
alongside or after task 001. No feature flag, no migration. If 001 has not deployed yet, the page
renders its error state on Apply — harmless — so the two can deploy independently.
