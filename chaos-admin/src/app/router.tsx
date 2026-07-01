import { RouteErrorBoundary } from "@/app/route-error-boundary";
import { LoadingCard } from "@/components/layout/state-panel";
import { AppShell } from "@/components/layout/app-shell";
import { LoginPage } from "@/features/auth/login-page";
import { ProtectedRoute } from "@/features/auth/protected-route";
import { ScenarioRunnerLayout } from "@/features/chaos/scenario-runner-layout";
import { dlqDetailPath, runDetailPath } from "@/lib/routes";
import { Suspense, lazy, type ReactNode } from "react";
import { Navigate, createBrowserRouter, useParams } from "react-router-dom";

const ChartOfAccountsPage = lazy(() =>
  import("@/features/chart-of-accounts/chart-of-accounts-page").then(m => ({
    default: m.ChartOfAccountsPage
  }))
);
const VirtualAccountsPage = lazy(() =>
  import("@/features/virtual-accounts/virtual-accounts-page").then(m => ({
    default: m.VirtualAccountsPage
  }))
);
const VirtualAccountDetailPage = lazy(() =>
  import("@/features/virtual-accounts/virtual-account-detail-page").then(m => ({
    default: m.VirtualAccountDetailPage
  }))
);
const TransactionsPage = lazy(() =>
  import("@/features/transactions/transactions-page").then(m => ({
    default: m.TransactionsPage
  }))
);
const TransactionDetailPage = lazy(() =>
  import("@/features/transactions/transaction-detail-page").then(m => ({
    default: m.TransactionDetailPage
  }))
);
const TrialBalancePage = lazy(() =>
  import("@/features/trial-balance/trial-balance-page").then(m => ({
    default: m.TrialBalancePage
  }))
);
const SingleFlowPage = lazy(() =>
  import("@/features/chaos/single-flow-page").then(m => ({ default: m.SingleFlowPage }))
);
const RunHistoryTab = lazy(() =>
  import("@/features/chaos/run-history-tab").then(m => ({ default: m.RunHistoryTab }))
);
const BatchRunPage = lazy(() =>
  import("@/features/chaos/batch-run-page").then(m => ({ default: m.BatchRunPage }))
);
const DeadLetterQueuePage = lazy(() =>
  import("@/features/dlq/dead-letter-queue-page").then(m => ({
    default: m.DeadLetterQueuePage
  }))
);
const DeadLetterQueueDetailPage = lazy(() =>
  import("@/features/dlq/dead-letter-queue-detail-page").then(m => ({
    default: m.DeadLetterQueueDetailPage
  }))
);
const CountriesPage = lazy(() =>
  import("@/features/countries/countries-page").then(m => ({ default: m.CountriesPage }))
);
const CurrenciesPage = lazy(() =>
  import("@/features/currencies/currencies-page").then(m => ({ default: m.CurrenciesPage }))
);
const SupportedCountriesPage = lazy(() =>
  import("@/features/supported-countries/supported-countries-page").then(m => ({
    default: m.SupportedCountriesPage
  }))
);
const OrganizationTypesPage = lazy(() =>
  import("@/features/organization-types/organization-types-page").then(m => ({
    default: m.OrganizationTypesPage
  }))
);
const OrganizationsPage = lazy(() =>
  import("@/features/organizations/organizations-page").then(m => ({
    default: m.OrganizationsPage
  }))
);

// Param-preserving redirects for legacy detail URLs (declarative <Navigate> can't interpolate a
// route param, so these read it and forward).
function RedirectBatchToRun() {
  const { batchId } = useParams<{ batchId: string }>();
  return <Navigate to={runDetailPath(batchId ?? "")} replace />;
}

function RedirectDlqDetail() {
  const { id } = useParams<{ id: string }>();
  return <Navigate to={dlqDetailPath(id ?? "")} replace />;
}

function withSuspense(node: ReactNode) {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[50vh] items-center justify-center p-8">
          <LoadingCard
            className="max-w-md"
            title="Loading module"
            description="Preparing the requested admin surface."
          />
        </div>
      }
    >
      {node}
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
    errorElement: <RouteErrorBoundary />
  },
  {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <AppShell />,
        errorElement: <RouteErrorBoundary />,
        children: [
          {
            index: true,
            element: <Navigate to="/chaos/scenario-runner" replace />
          },
          // Scenario Runner — one tabbed console (Run Scenario / Run History / DLQ) with
          // deep-linkable nested routes (ADR-030).
          {
            path: "/chaos/scenario-runner",
            element: <ScenarioRunnerLayout />,
            children: [
              { index: true, element: withSuspense(<SingleFlowPage />) },
              { path: "history", element: withSuspense(<RunHistoryTab />) },
              { path: "runs/:runId", element: withSuspense(<BatchRunPage />) },
              { path: "dlq", element: withSuspense(<DeadLetterQueuePage />) },
              { path: "dlq/:id", element: withSuspense(<DeadLetterQueueDetailPage />) }
            ]
          },
          // Legacy Operate URLs → their new homes (keep old bookmarks working).
          {
            path: "/chaos/single-flow",
            element: <Navigate to="/chaos/scenario-runner" replace />
          },
          {
            path: "/chaos/batches",
            element: <Navigate to="/chaos/scenario-runner/history" replace />
          },
          {
            path: "/chaos/batches/:batchId",
            element: <RedirectBatchToRun />
          },
          {
            path: "/chaos/upload",
            element: <Navigate to="/chaos/scenario-runner/history" replace />
          },
          {
            path: "/chaos/dlq",
            element: <Navigate to="/chaos/scenario-runner/dlq" replace />
          },
          {
            path: "/chaos/dlq/:id",
            element: <RedirectDlqDetail />
          },
          // Accounts
          {
            path: "/chart-of-accounts",
            element: withSuspense(<ChartOfAccountsPage />)
          },
          {
            path: "/virtual-accounts",
            element: withSuspense(<VirtualAccountsPage />)
          },
          {
            path: "/virtual-accounts/:vaId",
            element: withSuspense(<VirtualAccountDetailPage />)
          },
          // Organizations
          {
            path: "/countries",
            element: withSuspense(<CountriesPage />)
          },
          {
            path: "/currencies",
            element: withSuspense(<CurrenciesPage />)
          },
          {
            path: "/supported-countries",
            element: withSuspense(<SupportedCountriesPage />)
          },
          {
            path: "/organization-types",
            element: withSuspense(<OrganizationTypesPage />)
          },
          {
            path: "/organizations",
            element: withSuspense(<OrganizationsPage />)
          },
          // Ledger
          {
            path: "/transactions",
            element: withSuspense(<TransactionsPage />)
          },
          {
            path: "/transactions/:ref",
            element: withSuspense(<TransactionDetailPage />)
          },
          {
            path: "/trial-balance",
            element: withSuspense(<TrialBalancePage />)
          }
        ]
      }
    ]
  }
]);
