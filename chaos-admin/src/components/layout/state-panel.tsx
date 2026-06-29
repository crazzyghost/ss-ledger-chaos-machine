import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { AlertTriangle, Database, Inbox, RefreshCw, Search, ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";

type StateTone = "default" | "warning" | "danger";
type StateIcon = "error" | "warning" | "empty" | "search" | "access";

const toneStyles: Record<StateTone, string> = {
  default: "border-border bg-card",
  warning: "border-amber-200 bg-amber-50/80",
  danger: "border-destructive/30 bg-destructive/5"
};

const iconMap: Record<StateIcon, ReactNode> = {
  error: <AlertTriangle className="h-5 w-5" />,
  warning: <ShieldAlert className="h-5 w-5" />,
  empty: <Inbox className="h-5 w-5" />,
  search: <Search className="h-5 w-5" />,
  access: <Database className="h-5 w-5" />
};

type StatePanelProps = {
  title: string;
  description: string;
  action?: ReactNode;
  secondaryAction?: ReactNode;
  tone?: StateTone;
  icon?: StateIcon;
  iconNode?: ReactNode;
  className?: string;
};

export function StatePanel({
  title,
  description,
  action,
  secondaryAction,
  tone = "default",
  icon = "empty",
  iconNode,
  className
}: StatePanelProps) {
  return (
    <Card className={cn("w-full", toneStyles[tone], className)}>
      <CardHeader className="gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-background/90 text-foreground shadow-sm">
          {iconNode ?? iconMap[icon]}
        </div>
        <div className="space-y-1">
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
      </CardHeader>
      {(action || secondaryAction) && (
        <CardFooter className="gap-3">
          {action}
          {secondaryAction}
        </CardFooter>
      )}
    </Card>
  );
}

type InlineNoticeProps = {
  title?: string;
  description: string;
  tone?: StateTone;
  className?: string;
};

export function InlineNotice({ title, description, tone = "default", className }: InlineNoticeProps) {
  return (
    <div
      className={cn(
        "rounded-lg border px-4 py-3 text-xs",
        tone === "danger" && "border-destructive/30 bg-destructive/5 text-destructive",
        tone === "warning" && "border-amber-200 bg-amber-50/80 text-amber-900",
        tone === "default" && "border-border bg-muted/40 text-muted-foreground",
        className
      )}
    >
      {title ? <p className="font-medium text-foreground break-words">{title}</p> : null}
      <p className="break-words">{description}</p>
    </div>
  );
}

type LoadingCardProps = {
  title: string;
  description: string;
  className?: string;
};

export function LoadingCard({ title, description, className }: LoadingCardProps) {
  return (
    <StatePanel
      title={title}
      description={description}
      className={className}
      icon="empty"
      action={
        <Button variant="ghost" size="sm" disabled className="gap-2">
          <RefreshCw className="h-4 w-4 animate-spin" />
          Loading
        </Button>
      }
    />
  );
}

type SkeletonBlockProps = {
  className?: string;
};

export function SkeletonBlock({ className }: SkeletonBlockProps) {
  return <div className={cn("animate-pulse rounded-md bg-muted/70", className)} aria-hidden="true" />;
}

type TableLoadingRowsProps = {
  columns: number;
  rows?: number;
};

export function TableLoadingRows({ columns, rows = 5 }: TableLoadingRowsProps) {
  return (
    <>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <tr key={`loading-row-${rowIndex}`} className="border-b border-border">
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <td key={`loading-cell-${rowIndex}-${columnIndex}`} className="px-4 py-3">
              <SkeletonBlock className={cn("h-4", columnIndex === columns - 1 ? "ml-auto w-16" : "w-full max-w-[12rem]")} />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

type DetailPanelSkeletonProps = {
  includeSidebar?: boolean;
};

export function DetailPanelSkeleton({ includeSidebar = true }: DetailPanelSkeletonProps) {
  return (
    <div className={cn("grid gap-0 xl:grid-cols-3")}>
      <Card
        style={{ borderRadius: 0, boxShadow: "none" }}
        className="flex min-h-[26rem] flex-col rounded-none border-x-0 border-y-0 bg-transparent shadow-none xl:col-span-2"
      >
        <CardHeader className="space-y-3">
          <SkeletonBlock className="h-6 w-52" />
          <SkeletonBlock className="h-4 w-full max-w-xl" />
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={`detail-skeleton-${index}`} className={cn("space-y-2", index === 4 ? "md:col-span-2" : "")}>
              <SkeletonBlock className="h-4 w-28" />
              <SkeletonBlock className="h-10 w-full" />
            </div>
          ))}
        </CardContent>
      </Card>
      {includeSidebar ? (
        <Card
          style={{ borderRadius: 0, boxShadow: "none" }}
          className="hidden rounded-none border-x-0 border-y-0 bg-transparent shadow-none xl:flex"
        >
          <CardHeader className="space-y-3">
            <SkeletonBlock className="h-6 w-40" />
            <SkeletonBlock className="h-4 w-full max-w-xs" />
          </CardHeader>
          <CardContent className="space-y-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={`summary-skeleton-${index}`} className="space-y-2">
                <SkeletonBlock className="h-3 w-24" />
                <SkeletonBlock className="h-5 w-40" />
              </div>
            ))}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
