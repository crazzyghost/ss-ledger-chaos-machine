# Task 005 - Transactions & Per-VA History Pages

## Functional Requirements
- A transactions page that searches/browses transactions, and a per-VA transactions view
  reachable from a virtual account. Search supports virtual-account id and additional filters.
  Surfaces both **what the chaos machine sent** (local publish history, Phase 003/Task 005) and
  **what the ledger recorded** (ledger proxy, Phase 004/Task 002).

> The MANIFEST line listing search criteria is truncated ("Allows searching via …"). Assumed
> filter set: **virtual account id**, **flow/event type**, **correlation id**, **status**, and
> **date range**. Adjust if the complete MANIFEST differs (ARCHITECTURE §10).

## Acceptance Criteria
- [ ] `Transactions` page: filter bar (VA id, type, correlation id, status, date range) +
      paginated `Table`; searching by VA id returns transactions where it is source or destination.
- [ ] A toggle/tab switches between **Sent (chaos history)** → `GET /api/v0/history` and
      **Ledger** → `GET /api/v0/ledger/transactions`.
- [ ] Per-VA history: from a VA detail page, a `Transactions` tab pre-filters by that VA id.
- [ ] Row click opens a detail drawer/dialog showing the full event envelope JSON (for sent) or
      the ledger transaction detail (for ledger).
- [ ] Loading/empty/error/degraded states use swift-admin primitives.

## Technical Design
- `features/transactions/transactions-page.tsx` — `Tabs` (Sent | Ledger), shared filter bar,
  `Table` + `ListPagination`. Draft vs applied filters (swift-admin pattern); filters map to
  query params for each source.
- `features/transactions/transaction-detail-dialog.tsx` — pretty-printed JSON (sent) / fields (ledger).
- Hooks: `useSentHistory(filters,page)` → `["history", filters, page]`;
  `useLedgerTransactions(filters,page)` → `["ledger-transactions", filters, page]`.
- Per-VA reuse: the same components accept a `vaId` prop to lock the VA filter (embedded in
  Task 004's detail page).

```mermaid
flowchart LR
  page[TransactionsPage] --> tabs{Sent | Ledger}
  tabs -->|Sent| h[useSentHistory → /history]
  tabs -->|Ledger| l[useLedgerTransactions → /ledger/transactions]
  page --> row[Row click → detail dialog]
```

## Implementation Notes
- `lib/api.ts`: `listSentHistory(filters)`, `listLedgerTransactions(filters)`.
- Date-range via two date inputs → ISO timestamps; type filter from `GET /flows/catalog`.
- "Sent" rows show chaos labels (`chaos_strategy`, `intentional_failure`) as `Badge`s — useful
  for resilience analysis (compare what we sent vs. what the ledger accepted/DLT'd).
- Reuse swift-admin `ledger-transactions-page.tsx` structure as the template.

## Non-Functional Requirements
- Server pagination + filters; p95 list < 300ms (local history) / bounded by proxy for ledger.
- Ledger tab degrades gracefully on proxy `503`.

## Dependencies
Task 001/002/004; Phase 003 Task 005 (history API); Phase 004 Task 002 (ledger transactions proxy).

## Risks & Mitigations
- *Two differently-shaped sources* → normalize into a common `TransactionRow` view model per tab.
- *Truncated MANIFEST filters* → assumption documented; filter set easily extended.

## Testing Strategy
- MSW tests: search by VA id (source/destination), type + date filters, tab switch, detail
  dialog, per-VA pre-filter, ledger-degraded state.

## Deployment Strategy
No flag.
