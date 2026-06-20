import { router } from "@/app/router";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { SessionProvider } from "@/features/auth/session-provider";
import { getMissingConfigKeys } from "@/lib/env";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 30_000
    }
  }
});

const missingConfigKeys = getMissingConfigKeys();

function ConfigErrorScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-10">
      <Card className="w-full max-w-3xl">
        <CardHeader>
          <CardTitle>Missing app configuration</CardTitle>
          <CardDescription>
            The dashboard booted, but one or more required runtime values are not set.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-xs text-muted-foreground">
          <p>Missing keys: {missingConfigKeys.join(", ")}</p>
          <div className="rounded-3xl bg-accent p-4">
            <p className="font-medium text-foreground">Expected runtime variable</p>
            <ul className="mt-2 space-y-1">
              <li>
                <code>VITE_CHAOS_API_BASE_URL</code> — the chaos gateway base URL (e.g.{" "}
                <code>http://localhost:27100/api/v0</code>)
              </li>
            </ul>
          </div>
          <div className="rounded-3xl bg-slate-950 p-4 text-slate-100">
            <p className="text-xs leading-6">
              Runtime configuration values are loaded from{" "}
              <code>/runtime-config.js</code>. Set{" "}
              <code>VITE_CHAOS_API_BASE_URL</code> in your <code>.env</code> file for local
              development.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {missingConfigKeys.length > 0 ? (
      <ConfigErrorScreen />
    ) : (
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <RouterProvider router={router} />
        </SessionProvider>
      </QueryClientProvider>
    )}
  </React.StrictMode>
);
