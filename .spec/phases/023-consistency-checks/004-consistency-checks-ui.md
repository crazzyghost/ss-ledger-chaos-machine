# Task 004 - Consistency checks UI (frontend list, detail, and trigger)

## Functional Requirements
Add a **"Consistency Checks"** page to the Ledger nav group that lets operators trigger checks, list
all checks (with filters), view a single check's detail, and page through its findings. Must toast
when a mismatch event is consumed (polling the backend every 5s); must handle the ledger-unavailable
state (404/503) gracefully; must show "task worker not running" warning for long-pending checks.

## Acceptance Criteria
- [ ] New nav item **"Consistency Checks"** in the Ledger nav group (after "Trial Balance"), icon `ShieldAlert` from lucide-react
- [ ] Route `/ledger/consistency-checks` renders the **list view**
- [ ] Route `/ledger/consistency-checks/:checkId` renders the **detail view** (single check + discrepancies tab)
- [ ] List view has a **"Trigger Check"** button that opens a modal with a type selector (ALL / ACCOUNT_BALANCE_PROJECTION / ENTRY_BALANCE / SEQUENCE_INTEGRITY) and triggers `PUT /api/v0/ledger/consistency-checks?type={type}`
- [ ] List view has filter dropdowns for **type**, **status**, **initiatorType** (multi-select or single-select; all optional)
- [ ] List view is paginated (default page size 20), fetched via `GET /api/v0/ledger/consistency-checks?type=&status=&initiatorType=&page=&size=`
- [ ] Each list row shows: checkId (truncated UUID, clickable → detail), type, status (badge with color: PENDING=yellow, IN_PROGRESS=blue, COMPLETED=green, FAILED=red), initiatorType, initiatedAt, discrepancyCount (0 if clean run)
- [ ] Detail view shows check metadata (type, status, initiatorType, initiatedBy, asOf, initiatedAt, completedAt/erroredAt, errorCode, discrepancyCount) in a **card**
- [ ] Detail view has a **"Discrepancies"** tab that pages findings via `GET /api/v0/ledger/consistency-checks/{checkId}/discrepancies?code=&page=&size=`
- [ ] Discrepancies tab has an optional **code filter** dropdown (all discrepancy codes are enum values fetched from the first page's items; no backend enum endpoint)
- [ ] Each discrepancy row shows: code, accountId (clickable → virtual account detail if present), entryId (display only, no link), detectedAt, details (expandable JSON panel)
- [ ] UI polls `GET /api/v0/reconciliation-mismatches?since={lastPollTime}` every 5s when the consistency checks page is open or within 60s of triggering a check
- [ ] When a mismatch is polled, toast immediately: **"Consistency check {type} found {discrepancyCount} issue(s)"** (red/warning toast, clickable → detail)
- [ ] When the ledger returns **404** for any proxy call, show a dismissible banner: "Consistency checks are unavailable on the connected ledger. [Learn more](link to ledger docs)"
- [ ] When the ledger returns **503** (circuit breaker open), show a dismissible banner: "Ledger is temporarily unavailable (under load). Retry in a few moments."
- [ ] When a check is PENDING for > 30s, show a warning badge next to the status: "Task worker may not be running. [Help](link to ledger task-worker docs)"

## Technical Design

**Package structure (new):**
```
chaos-admin/src/features/consistency-checks
├── consistency-checks-page.tsx           # list view + filters + trigger modal
├── consistency-check-detail-page.tsx     # detail view + discrepancies tab
├── trigger-check-modal.tsx               # type selector + submit
├── discrepancies-table.tsx               # paginated findings table
├── reconciliation-mismatch-poller.tsx    # custom hook for polling mismatches
├── api.ts                                # react-query hooks
└── types.ts                              # TypeScript interfaces
```

**Nav integration:**
In `app-shell.tsx`, add to `ledgerNavigation`:
```tsx
import { ShieldAlert } from "lucide-react";

const ledgerNavigation: NavItem[] = [
  { to: "/transactions", label: "Transactions", icon: FileText },
  { to: "/trial-balance", label: "Trial Balance", icon: Scale },
  { to: "/ledger/consistency-checks", label: "Consistency Checks", icon: ShieldAlert }
];
```

**Routing:**
In `router.tsx`, add:
```tsx
{
  path: "ledger/consistency-checks",
  element: <ConsistencyChecksPage />,
},
{
  path: "ledger/consistency-checks/:checkId",
  element: <ConsistencyCheckDetailPage />,
}
```

**API hooks (react-query):**
```tsx
// api.ts
export function useConsistencyChecks(filters: {
  type?: string;
  status?: string;
  initiatorType?: string;
  page: number;
  size: number;
}) {
  return useQuery({
    queryKey: ["consistency-checks", filters],
    queryFn: () => get("/api/v0/ledger/consistency-checks", { params: filters }),
    // handle 404 / 503 via onError
  });
}

export function useConsistencyCheck(checkId: string) {
  return useQuery({
    queryKey: ["consistency-check", checkId],
    queryFn: () => get(`/api/v0/ledger/consistency-checks/${checkId}`),
  });
}

export function useConsistencyCheckDiscrepancies(checkId: string, code?: string, page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: ["consistency-check-discrepancies", checkId, code, page, size],
    queryFn: () => get(`/api/v0/ledger/consistency-checks/${checkId}/discrepancies`, {
      params: { code, page, size },
    }),
  });
}

export function useTriggerConsistencyCheck() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (type: string) => put(`/api/v0/ledger/consistency-checks?type=${type}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["consistency-checks"] });
      toast.success("Consistency check triggered");
    },
  });
}

export function useReconciliationMismatches(since: string, size: number = 20) {
  return useQuery({
    queryKey: ["reconciliation-mismatches", since],
    queryFn: () => get("/api/v0/reconciliation-mismatches", { params: { since, size } }),
    refetchInterval: 5000, // poll every 5s
    enabled: !!since, // only poll if `since` is set
  });
}
```

**List page (`consistency-checks-page.tsx`):**
- Tabbed layout? No — single list view (no tabs; the detail page has tabs).
- Filter dropdowns: **Type** (ALL / ACCOUNT_BALANCE_PROJECTION / ENTRY_BALANCE / SEQUENCE_INTEGRITY), **Status** (PENDING / IN_PROGRESS / COMPLETED / FAILED), **Initiator Type** (SYSTEM / PLATFORM_OPERATOR). All dropdowns are optional (absent = no filter).
- "Trigger Check" button opens `TriggerCheckModal` (shadcn Dialog).
- Table columns: Check ID (truncated, clickable), Type, Status (badge), Initiator, Initiated At, Discrepancies.
- Pagination: shadcn Pagination component (arrows + page numbers).
- Error states: 404 → banner "not available", 503 → banner "temporarily unavailable" (both dismissible via shadcn Alert component).

**Detail page (`consistency-check-detail-page.tsx`):**
- Card: Check metadata (type, status, initiatorType, initiatedBy, asOf, initiatedAt, completedAt/erroredAt, errorCode, discrepancyCount).
- Tabs: **Overview** (metadata card), **Discrepancies** (if `discrepancyCount > 0`; otherwise tab is disabled).
- Discrepancies tab: Filter by code (dropdown), paginated table, each row expandable (details JSON panel via shadcn Collapsible or shadcn Sheet).
- PENDING warning: If `status === "PENDING"` and `(now - initiatedAt) > 30s`, show shadcn Alert badge: "Task worker may not be running. [Help](link)".

**Trigger modal (`trigger-check-modal.tsx`):**
- shadcn Dialog with a Select dropdown for type (ALL / ACCOUNT_BALANCE_PROJECTION / ENTRY_BALANCE / SEQUENCE_INTEGRITY).
- "Trigger" button calls `useTriggerConsistencyCheck().mutate(type)`.
- On success, close modal, invalidate `consistency-checks` query, toast "Triggered", start mismatch polling for 60s.

**Mismatch poller (`reconciliation-mismatch-poller.tsx`):**
Custom hook that polls `GET /api/v0/reconciliation-mismatches?since={lastPollTime}` every 5s:
```tsx
export function useReconciliationMismatchPoller() {
  const [lastPollTime, setLastPollTime] = useState(() => new Date().toISOString());
  const { data } = useReconciliationMismatches(lastPollTime);

  useEffect(() => {
    if (data?.items?.length > 0) {
      data.items.forEach((mismatch) => {
        toast.error(
          `Consistency check ${mismatch.type} found ${mismatch.discrepancyCount} issue(s)`,
          {
            action: {
              label: "View",
              onClick: () => navigate(`/ledger/consistency-checks/${mismatch.checkId}`),
            },
          }
        );
      });
      setLastPollTime(data.nextSince); // update cursor
    }
  }, [data]);
}
```

The hook is invoked in `consistency-checks-page.tsx` (always polls when page is open) and in the
trigger modal (polls for 60s after triggering).

**Types (`types.ts`):**
```tsx
export type ConsistencyCheckType = "ACCOUNT_BALANCE_PROJECTION" | "ENTRY_BALANCE" | "SEQUENCE_INTEGRITY";
export type ConsistencyCheckStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
export type ConsistencyCheckInitiatorType = "SYSTEM" | "PLATFORM_OPERATOR";

export interface ConsistencyCheck {
  checkId: string;
  type: ConsistencyCheckType;
  status: ConsistencyCheckStatus;
  initiatorType: ConsistencyCheckInitiatorType;
  initiatedBy?: string;
  asOf?: string;
  discrepancyCount: number;
  initiatedAt: string;
  completedAt?: string;
  erroredAt?: string;
  errorCode?: string;
}

export interface ConsistencyCheckDiscrepancy {
  id: string;
  code: string;
  accountId?: string;
  entryId?: string;
  details: Record<string, any>;
  detectedAt: string;
}

export interface ReconciliationMismatch {
  id: string;
  checkId: string;
  type: ConsistencyCheckType;
  initiatorType: ConsistencyCheckInitiatorType;
  asOf: string;
  initiatedAt: string;
  completedAt: string;
  discrepancyCount: number;
  consumedAt: string;
}
```

## Implementation Notes

**Status badge colors:** Use shadcn Badge with variants:
- PENDING → `variant="secondary"` (yellow/gray)
- IN_PROGRESS → `variant="default"` (blue)
- COMPLETED → `variant="success"` (green; custom variant if not in shadcn, or use `className="bg-green-500"`)
- FAILED → `variant="destructive"` (red)

**Truncated UUID:** Show first 8 chars + ellipsis (`checkId.substring(0, 8)}...`), clickable to detail.

**Discrepancy details JSON panel:** Use shadcn Collapsible with a `<pre>` block inside, or shadcn Sheet (side panel) for full-screen view. Format JSON with `JSON.stringify(details, null, 2)`.

**Account ID link:** If `accountId` is present, link to `/virtual-accounts/{accountId}` (the existing VA detail page from Phase 015). If `accountId` is absent (discrepancy is not account-specific), show "—".

**Entry ID:** Display as truncated UUID, no link (the chaos machine has no entry detail page; entries are a ledger concept).

**Error code display:** If `status === "FAILED"` and `errorCode` is present, show it in the detail card as a red badge. If absent, show "—".

**Polling window:** The poller runs for 60s after triggering a check (enough time for most checks to complete). After 60s, stop polling unless the user is still on the page. If the user navigates away and back, restart polling.

**Learn more links:**
- Ledger API unavailable: Link to the ledger's OpenAPI docs (URL TBD; placeholder: `#`).
- Task worker help: Link to the ledger's operational docs on enabling the task worker (URL TBD; placeholder: `#`).

**No WebSocket / SSE:** Polling is sufficient for this phase. If real-time updates are ever needed, a future phase can add WebSocket (out of scope here).

## Non-Functional Requirements
- **Performance:** List page must render in < 500 ms (20 rows). Detail page must render in < 300 ms (single check + first page of discrepancies).
- **Usability:** Filters must be clearable (shadcn Select with "Clear all" button). Toast must be dismissible. Pagination must be keyboard-navigable.
- **Accessibility:** All interactive elements (buttons, links, dropdowns) must be keyboard-accessible and screen-reader-friendly (shadcn components handle this by default).

## Dependencies
- Existing `@/components/ui` (shadcn components: Button, Badge, Card, Dialog, Select, Alert, Table, Pagination, Collapsible/Sheet)
- Existing `@/features/auth` (session provider for bearer token)
- Existing `@/lib/api` (axios instance)
- react-query for data fetching
- react-router for navigation
- sonner for toasts
- lucide-react for icons

## Risks & Mitigations
**Risk:** Ledger does not have the consistency-check API (currently under development).  
**Mitigation:** All proxy calls 404; the banner says "not available". No crash, no confusion.

**Risk:** Polling hammers the backend (5s interval).  
**Mitigation:** The backend query is indexed and fast (Task 003). Polling only runs when the page is
open or within 60s of triggering a check.

**Risk:** Toast spam (100 checks triggered in quick succession).  
**Mitigation:** The toast library (sonner) deduplicates by content. If 3 checks of the same type
complete simultaneously, only one toast is shown. If 3 different types complete, 3 toasts are shown
(acceptable — it is a real event).

**Risk:** Long-pending check with no task worker → user waits forever.  
**Mitigation:** The 30s warning says "Task worker may not be running. [Help]". If the user ignores
it, the check stays PENDING forever, but the UI is honest about the state.

## Testing Strategy

**Unit tests (components):**
- `consistency-checks-page.test.tsx`: Mock `useConsistencyChecks`, verify table renders 20 rows,
  verify filter dropdowns change query params, verify "Trigger Check" button opens modal.
- `consistency-check-detail-page.test.tsx`: Mock `useConsistencyCheck`, verify metadata card shows
  all fields, verify Discrepancies tab is disabled when `discrepancyCount === 0`, verify PENDING
  warning appears when `status === "PENDING"` and `(now - initiatedAt) > 30s`.
- `trigger-check-modal.test.tsx`: Mock `useTriggerConsistencyCheck`, select type ALL, click
  "Trigger", verify mutation is called with `type=ALL`, verify modal closes on success.

**Integration tests (E2E):**
- Deploy backend + frontend, navigate to `/ledger/consistency-checks`, verify list is empty (or
  shows existing checks), trigger a check via the modal, verify the check appears in the list with
  status PENDING, wait for the ledger to complete the check (task worker running), verify the status
  changes to COMPLETED, verify discrepancy count updates, click the row, verify detail page loads,
  verify Discrepancies tab shows findings.

**Manual verification:**
- Run chaos machine + ledger, navigate to `/ledger/consistency-checks`, trigger all checks, verify 3
  rows appear, wait 60s, verify statuses update, verify toast appears if a check finds a mismatch.
- Trigger a check with the ledger's task worker disabled, verify the check stays PENDING forever,
  verify the warning appears after 30s.
- Stop the ledger, trigger a check, verify 503 banner appears, restart the ledger, verify banner
  disappears, verify check completes.
- Deploy against a ledger without the API, navigate to the page, verify 404 banner appears, verify
  no crash.

## Deployment Strategy
Deploy frontend only (backend is already deployed from Tasks 001–003). Verify the nav item appears,
verify the list page loads, verify Swagger UI shows the four proxy endpoints. No Flyway migration,
no backend config change.
