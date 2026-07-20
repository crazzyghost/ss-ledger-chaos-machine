import { useSession } from "@/features/auth/session-provider";
import { cn } from "@/lib/utils";
import {
  Activity,
  BookOpen,
  Building2,
  ChevronRight,
  Coins,
  FileText,
  Globe,
  Globe2,
  LogOut,
  Play,
  Scale,
  ShieldAlert,
  Tags,
  Wallet
} from "lucide-react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { Toaster } from "sonner";

type NavItem = {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
};

const operateNavigation: NavItem[] = [
  { to: "/chaos/scenario-runner", label: "Scenario Runner", icon: Play }
];

const accountsNavigation: NavItem[] = [
  { to: "/chart-of-accounts", label: "Chart of Accounts", icon: BookOpen },
  { to: "/virtual-accounts", label: "Virtual Accounts", icon: Wallet }
];

const organizationsNavigation: NavItem[] = [
  { to: "/countries", label: "Countries", icon: Globe },
  { to: "/currencies", label: "Currencies", icon: Coins },
  { to: "/supported-countries", label: "Supported Countries", icon: Globe2 },
  { to: "/organization-types", label: "Organization Types", icon: Tags },
  { to: "/organizations", label: "Organizations", icon: Building2 }
];

const ledgerNavigation: NavItem[] = [
  { to: "/transactions", label: "Transactions", icon: FileText },
  { to: "/trial-balance", label: "Trial Balance", icon: Scale },
  { to: "/ledger/consistency-checks", label: "Consistency Checks", icon: ShieldAlert }
];

function NavGroup({ label, items }: { label: string; items: NavItem[] }) {
  return (
    <>
      <div className="mb-1 px-2 pb-1 pt-2">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-sidebar-muted">
          {label}
        </p>
      </div>
      <div className="space-y-0.5">
        {items.map(item => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "group flex items-center gap-2.5 rounded-md px-2.5 py-2 text-xs font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-white"
                    : "text-sidebar-fg hover:bg-sidebar-accent/60 hover:text-white"
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    className={cn(
                      "h-4 w-4 shrink-0",
                      isActive ? "text-sidebar-primary" : "text-sidebar-muted group-hover:text-sidebar-fg"
                    )}
                  />
                  <span className="flex-1 truncate">{item.label}</span>
                  {isActive && <ChevronRight className="h-3 w-3 text-sidebar-muted" />}
                </>
              )}
            </NavLink>
          );
        })}
      </div>
    </>
  );
}

export function AppShell() {
  const { principal, token, signOut, clusterLabel } = useSession();

  const emailInitial = (principal?.subject ?? "?")[0]?.toUpperCase() ?? "?";
  const displayName = principal?.subject ?? "—";
  const role = principal?.authorities?.[0] ?? "no role";

  return (
    <div className="h-dvh min-h-0 overflow-hidden">
      <div className="grid h-full min-h-0 lg:grid-cols-[240px_minmax(0,1fr)]">
        {/* Sidebar */}
        <aside className="flex h-full min-h-0 flex-col overflow-y-auto bg-sidebar-bg">
          {/* Logo */}
          <Link
            to="/chaos/scenario-runner"
            className="flex items-center gap-2.5 px-4 py-5 transition-opacity hover:opacity-80"
          >
            <Activity className="h-7 w-7 shrink-0 text-sidebar-primary" />
            <div>
              <p className="text-xs font-semibold text-white">Chaos Admin</p>
              <p className="text-[10px] leading-none text-sidebar-muted">Resilience Console</p>
            </div>
          </Link>

          {/* Cluster badge */}
          {clusterLabel && (
            <div className="mx-3 mb-1">
              <div className="flex items-center gap-1.5 rounded-md border border-amber-500/30 bg-amber-500/10 px-2.5 py-1.5">
                <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
                <span className="text-[10px] font-medium text-amber-300">
                  Target: {clusterLabel}
                </span>
              </div>
            </div>
          )}

          <div className="mx-3 border-t border-white/5" />

          {/* Navigation */}
          <nav className="flex-1 space-y-3 px-3 py-3">
            <NavGroup label="Operate" items={operateNavigation} />
            <NavGroup label="Accounts" items={accountsNavigation} />
            <NavGroup label="Organizations" items={organizationsNavigation} />
            <NavGroup label="Ledger" items={ledgerNavigation} />
          </nav>

          {/* User session */}
          <div className="mx-3 border-t border-white/5" />
          <div className="p-3">
            <div className="flex items-center gap-2.5 rounded-md px-2.5 py-2">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sidebar-accent text-xs font-semibold text-white">
                {emailInitial}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-medium text-sidebar-fg">{displayName}</p>
                <p className="truncate text-[10px] text-sidebar-muted">{role}</p>
              </div>
              <button
                type="button"
                onClick={signOut}
                title="Sign out"
                className="rounded-md p-1 text-sidebar-muted transition-colors hover:bg-sidebar-accent hover:text-white"
              >
                <LogOut className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </aside>

        {/* Main content */}
        <main className="h-full min-h-0 overflow-y-auto bg-background">
          <Outlet />
        </main>
      </div>
      {/* Global toaster — failure / balance / reservation watches surface here (Phases 017–019) */}
      <Toaster richColors position="top-right" closeButton />
    </div>
  );
}
