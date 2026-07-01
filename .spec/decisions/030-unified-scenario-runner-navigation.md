# ADR 030 - Unified Scenario Runner navigation (tabbed Operate console)

## Status
Accepted

## Context
Idea `016_unified_scenario_runner.md` asks to consolidate the operator's "drive + observe"
surface. Today (verified in `chaos-admin/src`):

- The **Operate** nav group has three items: *Single Flow Run* (`/chaos/single-flow`),
  *Batches* (`/chaos/batches`), and *Dead Letter Queue* (`/chaos/dlq`).
- The **Ledger** nav group has *Transactions* (`/transactions`) and *Trial Balance*.
- The *Transactions* page (`features/transactions/transactions-page.tsx`) is itself tabbed:
  **Sent (Chaos History)** (`GET /api/v0/history`) and **Ledger** (`GET /api/v0/ledger/transactions`).
- Each Operate concern is its own top-level route with its own detail page
  (`/chaos/batches/:batchId`, `/chaos/dlq/:id`).

The operator's workflow — *fire a scenario → watch what was sent → inspect what the ledger
rejected* — is spread across three nav items plus a tab buried inside a different nav group.
The forces at play:

- **One mental model.** "Run a scenario and observe its consequences" should be one place.
- **Deep-linking + back-button.** The app uses `react-router` 7 `createBrowserRouter` with a
  route per page; operators bookmark/share `/chaos/...` URLs and expect browser history to work.
- **Minimal churn.** The underlying pages (single-flow runner, DLQ list/detail, run-detail
  progress view) are mature; consolidation should re-home them, not rewrite them.
- **The Sent history is run-oriented, not VA-oriented.** It belongs next to the runner, while
  the *Transactions* page's reason to exist becomes the **ledger** view (see
  [ADR-032](032-ledger-transactions-account-scoped-view.md)).

## Decision
Collapse the **Operate** group to a **single nav item, "Scenario Runner"**, backed by a tabbed
page with three tabs:

| Tab | Content | Source today |
|---|---|---|
| **Run Scenario** | the current Single Flow Run page | `features/chaos/single-flow-page.tsx` |
| **Run History** | run-grouped published-event history | the *Sent (Chaos History)* tab + the *Batches* list, merged ([ADR-031](031-run-grouped-history-and-csv-retirement.md)) |
| **DLQ** | the current Dead Letter Queue list + detail | `features/dlq/*` |

Tabs are **deep-linkable nested routes**, not internal component state, so each tab and its
detail pages keep working URLs and browser history:

```
/chaos/scenario-runner            → Run Scenario (index)
/chaos/scenario-runner/history    → Run History
/chaos/scenario-runner/runs/:runId→ run detail (re-homed batch-run-page; tracked runs)
/chaos/scenario-runner/dlq        → DLQ list
/chaos/scenario-runner/dlq/:id    → DLQ detail
```

A `ScenarioRunnerLayout` renders the shared `Page` header + a `Tabs` strip whose active tab is
driven by the matched child route (`useMatch`/`NavLink`), with `<Outlet/>` for the body.

**Redirects** preserve every old bookmark (handled by `react-router` `redirect` loaders or
`<Navigate replace/>`):

- `/chaos/single-flow` → `/chaos/scenario-runner`
- `/chaos/batches` → `/chaos/scenario-runner/history`
- `/chaos/batches/:id` → `/chaos/scenario-runner/runs/:id`
- `/chaos/dlq` → `/chaos/scenario-runner/dlq`
- `/chaos/dlq/:id` → `/chaos/scenario-runner/dlq/:id`
- the index redirect `/` → `/chaos/single-flow` is repointed to `/chaos/scenario-runner`

The **Batches** and **Dead Letter Queue** nav items are removed; the *Transactions* item stays in
the **Ledger** group but loses its *Sent* tab (it moves into Run History) and becomes the
account-scoped ledger view ([ADR-032](032-ledger-transactions-account-scoped-view.md)).

The **per-VA** transactions view embedded in the VA detail page (`TransactionsTab` with a locked
VA id) is **unchanged** — it is VA-scoped, not run-scoped, and out of this consolidation's scope.

## Consequences
- **Positive:** one cohesive operator console for the full run→observe loop; three fewer
  top-level nav items; all tabs and detail pages remain deep-linkable with working back-button;
  old URLs keep resolving via redirects; the underlying pages are re-homed, not rewritten.
- **Negative:** more routing surface (a layout route + nested children + a redirect table to
  maintain); the Scenario Runner becomes a larger, busier page; the per-VA sent history now lives
  in a different place (VA detail) from the global run history (Scenario Runner), a deliberate
  split the UI copy must make obvious; deep-linking to a tab requires the layout to resolve the
  active tab from the route rather than from a simpler `useState`.
