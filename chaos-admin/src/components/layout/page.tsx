import { cn } from "@/lib/utils";
import type { PropsWithChildren, ReactNode } from "react";

export function Page({ children }: PropsWithChildren) {
  return <div className="flex min-h-full min-w-0 flex-col">{children}</div>;
}

export function PageHeader({
  title,
  description,
  actions,
  leadingActions
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  leadingActions?: ReactNode;
}) {
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/95 px-6 py-5 backdrop-blur-sm md:px-8">
      <div className="flex flex-col gap-4">
        {leadingActions ? <div className="flex flex-wrap items-center gap-2">{leadingActions}</div> : null}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-foreground">{title}</h2>
            {description && (
              <p className="mt-1 max-w-2xl text-xs text-muted-foreground">{description}</p>
            )}
          </div>
          {actions && (
            <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
          )}
        </div>
      </div>
    </header>
  );
}

export function PageContent({ children, className }: PropsWithChildren<{ className?: string }>) {
  return (
    <main className="min-h-0 flex-1">
      <div className={cn("grid gap-5 px-6 py-6 md:px-8", className)}>{children}</div>
    </main>
  );
}
