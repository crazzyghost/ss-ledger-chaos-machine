import { LoadingCard, StatePanel } from "@/components/layout/state-panel";
import { useSession } from "@/features/auth/session-provider";
import { Navigate, Outlet, useLocation } from "react-router-dom";

export function ProtectedRoute() {
  const { loading, token } = useSession();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <LoadingCard
          className="max-w-md"
          title="Loading session"
          description="Verifying your authentication token with the chaos gateway."
        />
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
