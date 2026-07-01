import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { SentEventsTable } from "@/features/transactions/sent-events-table";
import { listRuns, type RunStatusRollup, type RunSummaryResponse } from "@/lib/api";
import { runDetailPath } from "@/lib/routes";
import { formatDate, formatEnumValue } from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, Layers } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

const PER_PAGE = 20;
const RUNNING_POLL_MS = 3000;
const COLUMN_COUNT = 9;

const KIND_OPTIONS = [
  { value: "", label: "All kinds" },
  { value: "N_TIMES", label: "N-Times" },
  { value: "LIFECYCLE", label: "Lifecycle" },
  { value: "BATCH_DISBURSEMENT", label: "Batch Disbursement" },
  { value: "SINGLE", label: "Single publish" },
  { value: "MANUAL_SEQUENCE", label: "Manual sequence" },
  { value: "CSV", label: "CSV (historical)" }
] as const;

type RunFilters = { kind: string; from: string; to: string };
const INITIAL_FILTERS: RunFilters = { kind: "", from: "", to: "" };

function toISOFrom(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T00:00:00.000Z`;
}

function toISOTo(dateStr: string): string | undefined {
  if (!dateStr) return undefined;
  return `${dateStr}T23:59:59.999Z`;
}

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

const STATUS_VARIANT: Record<string, BadgeVariant> = {
  RUNNING: "warning",
  ALL_PUBLISHED: "success",
  HAS_FAILURES: "warning",
  FAILED: "destructive"
};

function statusVariant(status: RunStatusRollup): BadgeVariant {
  return STATUS_VARIANT[status] ?? "neutral";
}

function statusLabel(status: RunStatusRollup): string {
  return status === "RUNNING" ? "Running" : formatEnumValue(status);
}

function RunRow({ run }: { run: RunSummaryResponse }) {
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(false);

  // Tracked runs drill down by batch_id; untracked groups by correlation_id (ADR-031).
  const eventFilters = run.tracked
    ? { batchId: run.batchId ?? run.runKey }
    : { correlationId: run.correlationId ?? run.runKey };

  return (
    <>
      <TR
        role="button"
        tabIndex={0}
        className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
        onClick={() => setExpanded(v => !v)}
        onKeyDown={e => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            setExpanded(v => !v);
          }
        }}
      >
        <TD className="w-8 text-muted-foreground">
          {expanded ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
        </TD>
        <TD>
          <div className="flex items-center gap-1.5">
            <Badge variant={run.tracked ? "secondary" : "neutral"}>
              {formatEnumValue(run.kind)}
            </Badge>
          </div>
        </TD>
        <TD className="max-w-[16rem] truncate font-mono text-xs text-muted-foreground">
          {run.flowTypes.length > 0 ? run.flowTypes.map(formatEnumValue).join(", ") : "—"}
        </TD>
        <TD className="tabular-nums">{run.eventCount}</TD>
        <TD>
          <Badge variant={statusVariant(run.status)}>{statusLabel(run.status)}</Badge>
        </TD>
        <TD className="text-xs text-muted-foreground">
          <span className="text-emerald-700">{run.publishedCount} published</span>
          {run.failedCount > 0 && (
            <>
              {" · "}
              <span className="text-red-700">{run.failedCount} failed</span>
            </>
          )}
        </TD>
        <TD>
          {run.intentionalFailure ? (
            <Badge variant="warning" title="Run contains intentional/chaos failures">
              Chaos
            </Badge>
          ) : (
            <span className="text-muted-foreground">—</span>
          )}
        </TD>
        <TD>{formatDate(run.lastActivityAt)}</TD>
        <TD className="text-right">
          {run.tracked && (
            <Button
              variant="ghost"
              size="sm"
              onClick={e => {
                e.stopPropagation();
                navigate(runDetailPath(run.runKey));
              }}
            >
              View run
            </Button>
          )}
        </TD>
      </TR>
      {expanded && (
        <TR>
          <TD colSpan={COLUMN_COUNT} className="bg-muted/20 p-0">
            <div className="px-4 py-3">
              <SentEventsTable
                filters={eventFilters}
                emptyMessage={
                  run.status === "RUNNING"
                    ? "Run in progress — events may still be arriving."
                    : "No published events recorded for this run."
                }
              />
            </div>
          </TD>
        </TR>
      )}
    </>
  );
}

/**
 * The Run History tab: a run-grouped, expandable accordion over `GET /api/v0/runs` (ADR-031).
 *
 * <p>Each row is a run; expanding it lazy-loads that run's individual published events (the shared
 * {@link SentEventsTable}, drilled by `batchId` for tracked runs or `correlationId` for untracked).
 * Tracked runs deep-link to their live detail/progress page; while any visible run is RUNNING the
 * feed polls until it goes terminal. Replaces both the old Sent-history tab and the Batches list.
 */
export function RunHistoryTab() {
  const { token } = useSession();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<RunFilters>(INITIAL_FILTERS);
  const [applied, setApplied] = useState<RunFilters>(INITIAL_FILTERS);

  function applyFilters() {
    setPage(0);
    setApplied(draft);
  }
  function clearFilters() {
    setPage(0);
    setDraft(INITIAL_FILTERS);
    setApplied(INITIAL_FILTERS);
  }

  const query = useQuery({
    queryKey: ["runs", { ...applied, page }],
    queryFn: () =>
      listRuns(token!, {
        page,
        size: PER_PAGE,
        kind: applied.kind || undefined,
        from: toISOFrom(applied.from),
        to: toISOTo(applied.to)
      }),
    placeholderData: keepPreviousData,
    // Keep counters fresh while any visible run is still running; stop once all are terminal.
    refetchInterval: q =>
      (q.state.data?.items ?? []).some(r => r.status === "RUNNING") ? RUNNING_POLL_MS : false
  });

  const runs = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Run History"
        description="Published activity grouped by the run that produced it. Expand a run to see its events; open a tracked run for live progress."
      />
      <PageContent className="gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <label className="text-[10px] uppercase tracking-wide text-muted-foreground">Kind</label>
            <Select
              value={draft.kind}
              onChange={value => setDraft(d => ({ ...d, kind: value }))}
              options={KIND_OPTIONS}
              className="w-44"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-[10px] uppercase tracking-wide text-muted-foreground">From</label>
            <Input
              type="date"
              value={draft.from}
              onChange={e => setDraft(d => ({ ...d, from: e.target.value }))}
              onKeyDown={e => e.key === "Enter" && applyFilters()}
              className="w-40"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-[10px] uppercase tracking-wide text-muted-foreground">To</label>
            <Input
              type="date"
              value={draft.to}
              onChange={e => setDraft(d => ({ ...d, to: e.target.value }))}
              onKeyDown={e => e.key === "Enter" && applyFilters()}
              className="w-40"
            />
          </div>
          <Button onClick={applyFilters} disabled={query.isFetching}>
            Apply
          </Button>
          <Button variant="outline" onClick={clearFilters} disabled={query.isFetching}>
            Clear
          </Button>
        </div>

        {query.isLoading ? (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <RunHistoryHeader />
              <TBody>
                <TableLoadingRows columns={COLUMN_COUNT} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : query.error ? (
          <StatePanel
            title="Failed to load runs"
            description={getErrorMessage(query.error)}
            tone="danger"
            icon="error"
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          />
        ) : runs.length === 0 ? (
          <StatePanel
            title="No runs yet"
            description="Fire a scenario from the Run Scenario tab and it will appear here."
            iconNode={<Layers className="h-5 w-5" />}
          />
        ) : (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <RunHistoryHeader />
              <TBody>
                {runs.map(run => (
                  <RunRow key={run.runKey} run={run} />
                ))}
              </TBody>
            </Table>
          </TableContainer>
        )}

        <ListPagination
          page={page}
          total={total}
          pageSize={PER_PAGE}
          itemLabel="run"
          hasNextPage={hasNextPage}
          disabled={query.isFetching}
          onPrevious={() => setPage(p => Math.max(p - 1, 0))}
          onNext={() => setPage(p => p + 1)}
        />
      </PageContent>
    </Page>
  );
}

function RunHistoryHeader() {
  return (
    <THead>
      <TR>
        <TH className="w-8" />
        <TH>Kind</TH>
        <TH>Flow Type(s)</TH>
        <TH>Events</TH>
        <TH title="Publish-status rollup">Status</TH>
        <TH>Outcome</TH>
        <TH>Intentional</TH>
        <TH>Last Activity</TH>
        <TH className="text-right">Action</TH>
      </TR>
    </THead>
  );
}
