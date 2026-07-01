import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DLQ_PATH, RUN_HISTORY_PATH, SCENARIO_RUNNER } from "@/lib/routes";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

type RunnerTab = "run" | "history" | "dlq";

const TAB_PATHS: Record<RunnerTab, string> = {
  run: SCENARIO_RUNNER,
  history: RUN_HISTORY_PATH,
  dlq: DLQ_PATH
};

/**
 * Maps the current pathname to the active tab. The run-detail page (`runs/:runId`) is reached by
 * deep-link from Run History and reads as the History tab while open; the DLQ detail page reads as
 * the DLQ tab (ADR-030).
 */
function activeTabFor(pathname: string): RunnerTab {
  if (pathname.startsWith(`${SCENARIO_RUNNER}/dlq`)) return "dlq";
  if (pathname.startsWith(`${SCENARIO_RUNNER}/history`)) return "history";
  if (pathname.startsWith(`${SCENARIO_RUNNER}/runs`)) return "history";
  return "run";
}

/**
 * The Scenario Runner shell: a route-driven three-tab strip (Run Scenario / Run History / DLQ) over
 * an `<Outlet/>` for the nested route body. The active tab derives from the URL — not local state —
 * so each tab and its detail pages keep deep-linkable URLs and a working back button (ADR-030).
 *
 * <p>The shell owns only the tab strip; each nested page renders its own `<Page>`/`PageHeader`. The
 * body scrolls independently of the (non-scrolling) strip so a tab's sticky header never collides
 * with the strip.
 */
export function ScenarioRunnerLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const activeTab = activeTabFor(pathname);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0 border-b border-border bg-background">
        <Tabs
          value={activeTab}
          defaultValue="run"
          onValueChange={value => navigate(TAB_PATHS[value as RunnerTab])}
        >
          <TabsList>
            <TabsTrigger value="run">Run Scenario</TabsTrigger>
            <TabsTrigger value="history">Run History</TabsTrigger>
            <TabsTrigger value="dlq">DLQ</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <Outlet />
      </div>
    </div>
  );
}
