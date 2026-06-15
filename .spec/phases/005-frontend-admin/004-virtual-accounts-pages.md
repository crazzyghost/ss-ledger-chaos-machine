# Task 004 - Virtual Accounts Pages

## Functional Requirements
- Pages to **create** a virtual account, **view all** virtual accounts (list with search +
  filters), and view a **VA detail** page — with a link/section to that VA's transaction
  history (Task 005). Consumes Phase 002 (registry) and Phase 004 (ledger proxy) APIs.

## Acceptance Criteria
- [ ] `Virtual Accounts` list page: paginated `Table` with filters (ownership, org, currency,
      status) + free-text search on name/id → `GET /api/v0/virtual-accounts`.
- [ ] "Create Virtual Account" `Dialog`: name, ownership, currency, org (for ORG VAs), channel,
      optional `announce` toggle → `POST /api/v0/virtual-accounts`; list invalidates.
- [ ] Row click → `VA detail` page (`/virtual-accounts/:id`) showing registry fields + (if
      available) ledger account/balance from the proxy + a "Transactions" section.
- [ ] An "Announce to ledger" action on detail → `POST /api/v0/virtual-accounts/{id}/publish`.
- [ ] Loading/empty/error states use swift-admin primitives.

## Technical Design
- `features/virtual-accounts/virtual-accounts-page.tsx` — filter bar (`Input`, `Select`),
  `Table`, `ListPagination`; draft vs applied filters (swift-admin pattern).
- `features/virtual-accounts/virtual-account-detail-page.tsx` — header card + tabs: **Overview**
  (registry + proxied ledger balance) and **Transactions** (embeds Task 005's per-VA view).
- `features/virtual-accounts/create-virtual-account-dialog.tsx` — `useState` form + `useMutation`.
- Hooks: `useVirtualAccounts(filters,page)`, `useVirtualAccount(id)`,
  `useCreateVirtualAccount()`, `useAnnounceVirtualAccount(id)`; keys
  `["virtual-accounts", filters, page]`.

```mermaid
flowchart LR
  list[VirtualAccountsPage] -->|row click| detail[VADetailPage]
  list --> create[Create Dialog]
  detail --> tx[Transactions section (Task 005)]
  detail --> ann[Announce → POST .../publish]
```

## Implementation Notes
- `lib/api.ts`: `listVirtualAccounts`, `getVirtualAccount`, `createVirtualAccount`,
  `announceVirtualAccount`. Overview balance via `getLedgerAccountBalances(vaId)` (proxy);
  handle proxy `503` gracefully (degraded `StatePanel`).
- Reuse swift-admin `Table`/`ListPagination`/`StatePanel`/`Dialog`/`Badge`.

## Non-Functional Requirements
- Server pagination + filters; debounce free-text search.
- Detail page resilient to ledger-proxy degradation (registry still renders).

## Dependencies
Task 001/002; Phase 002 (registry + announce); Phase 004 Task 002 (ledger proxy balances).

## Risks & Mitigations
- *Ledger proxy down on detail* → render registry data + degraded notice for ledger sections.
- *Announce double-send* → button disabled while pending; idempotency-keyed server side.

## Testing Strategy
- MSW tests: list + filters + pagination; create (incl. announce); detail with proxy up/down;
  announce action.

## Deployment Strategy
No flag.
