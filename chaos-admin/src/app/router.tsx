import { RouteErrorBoundary } from "@/app/route-error-boundary";
import { LoadingCard } from "@/components/layout/state-panel";
import { AppShell } from "@/components/layout/app-shell";
import { LoginPage } from "@/features/auth/login-page";
import { ProtectedRoute } from "@/features/auth/protected-route";
import { Suspense, lazy, type ReactNode } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

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
const SingleFlowPage = lazy(() =>
  import("@/features/chaos/single-flow-page").then(m => ({ default: m.SingleFlowPage }))
);
const BatchUploadPage = lazy(() =>
  import("@/features/chaos/batch-upload-page").then(m => ({ default: m.BatchUploadPage }))
);
const BatchRunPage = lazy(() =>
  import("@/features/chaos/batch-run-page").then(m => ({ default: m.BatchRunPage }))
);
const BatchesPage = lazy(() =>
  import("@/features/chaos/batches-page").then(m => ({ default: m.BatchesPage }))
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
            element: <Navigate to="/chaos/single-flow" replace />
          },
          // Chaos runner
          {
            path: "/chaos/single-flow",
            element: withSuspense(<SingleFlowPage />)
          },
          {
            path: "/chaos/upload",
            element: withSuspense(<BatchUploadPage />)
          },
          {
            path: "/chaos/batches",
            element: withSuspense(<BatchesPage />)
          },
          {
            path: "/chaos/batches/:batchId",
            element: withSuspense(<BatchRunPage />)
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
          }
        ]
      }
    ]
  }
]);
