// Central route-path helpers for the unified Scenario Runner (Phase 021 / ADR-030).
//
// The runner collapses the old Operate nav into one tabbed page with deep-linkable nested routes.
// Keeping these paths in one place means the async-run handoffs, redirects, the tab strip, and the
// DLQ/run-detail back-navigation all agree on a single source of truth.

/** The Scenario Runner root — the Run Scenario tab (index). */
export const SCENARIO_RUNNER = "/chaos/scenario-runner";

/** The Run History tab. */
export const RUN_HISTORY_PATH = `${SCENARIO_RUNNER}/history`;

/** The DLQ list tab. */
export const DLQ_PATH = `${SCENARIO_RUNNER}/dlq`;

/** Deep-link to a tracked run's live detail/progress page. */
export function runDetailPath(runId: string): string {
  return `${SCENARIO_RUNNER}/runs/${encodeURIComponent(runId)}`;
}

/** Deep-link to a dead-letter detail page. */
export function dlqDetailPath(id: string): string {
  return `${DLQ_PATH}/${encodeURIComponent(id)}`;
}
