import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { login, ApiError } from "@/lib/api";
import { useSession } from "@/features/auth/session-provider";
import { Activity, Shield, Zap } from "lucide-react";
import { useState } from "react";
import { Navigate, useLocation } from "react-router-dom";

export function LoginPage() {
  const { token, signIn } = useSession();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const redirectTo =
    typeof location.state === "object" &&
    location.state !== null &&
    "from" in location.state &&
    typeof (location.state as { from?: { pathname?: string; search?: string; hash?: string } }).from?.pathname === "string"
      ? `${(location.state as { from: { pathname: string; search?: string; hash?: string } }).from.pathname}${(location.state as { from?: { search?: string } }).from?.search ?? ""}${(location.state as { from?: { hash?: string } }).from?.hash ?? ""}`
      : "/chaos/scenario-runner";

  if (token) {
    return <Navigate to={redirectTo} replace />;
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (!email.trim() || !password.trim()) {
      setError("Email and password are required.");
      return;
    }

    setLoading(true);
    try {
      const response = await login(email.trim(), password);
      signIn(response.access_token, response.expires_in ?? null);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(
          err.status === 401
            ? "Invalid credentials. Check your email and password."
            : err.status === 503
              ? "Auth service is unavailable. Try again in a moment."
              : err.message
        );
      } else {
        setError("Unexpected error. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Left panel */}
      <div className="hidden flex-col justify-between bg-sidebar-bg p-10 lg:flex">
        <div className="flex items-center gap-2.5">
          <Activity className="h-8 w-8 shrink-0 text-sidebar-primary" />
          <span className="text-xs font-semibold text-white">Chaos Admin</span>
        </div>

        <div>
          <blockquote className="mb-10 max-w-md">
            <p className="text-2xl font-semibold leading-snug text-white">
              Ledger resilience testing, under control.
            </p>
            <p className="mt-4 text-xs leading-relaxed text-sidebar-fg">
              Drive the ledger through its Kafka event surface — well-formed or deliberately
              malformed — and observe idempotency, DLT routing, and balance integrity in real time.
            </p>
          </blockquote>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {[
              {
                icon: Zap,
                label: "Chaos Flows",
                description: "Single & CSV batch publishing"
              },
              {
                icon: Shield,
                label: "Controlled",
                description: "Bounded, opt-in, target-labelled"
              },
              {
                icon: Activity,
                label: "Observable",
                description: "Live run progress & history"
              }
            ].map(item => {
              const Icon = item.icon;
              return (
                <div
                  key={item.label}
                  className="rounded-lg border border-white/10 bg-sidebar-accent/60 p-4"
                >
                  <Icon className="mb-2.5 h-4 w-4 text-sidebar-primary" />
                  <p className="text-xs font-medium text-white">{item.label}</p>
                  <p className="mt-0.5 text-xs text-sidebar-muted">{item.description}</p>
                </div>
              );
            })}
          </div>
        </div>

        <p className="text-xs text-sidebar-muted">Restricted to authorised operators only.</p>
      </div>

      {/* Right panel — login form */}
      <div className="flex items-center justify-center bg-background px-6 py-12">
        <div className="w-full max-w-sm">
          {/* Mobile logo */}
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <Activity className="h-8 w-8 shrink-0 text-primary" />
            <span className="text-xs font-semibold">Chaos Admin</span>
          </div>

          <div className="mb-8">
            <h1 className="text-2xl font-semibold tracking-tight">Sign in</h1>
            <p className="mt-1.5 text-xs text-muted-foreground">
              Use your operator credentials to access the chaos console.
            </p>
          </div>

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-1.5">
              <label className="text-xs font-medium" htmlFor="email">
                Email
              </label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium" htmlFor="password">
                Password
              </label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />
            </div>

            {error && (
              <div className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2.5 text-xs text-destructive">
                {error}
              </div>
            )}

            <Button className="w-full" type="submit" disabled={loading}>
              {loading ? "Signing in…" : "Sign in"}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
