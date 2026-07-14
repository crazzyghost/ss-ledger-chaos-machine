import { ListPagination } from "@/components/layout/list-pagination";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EnumBadge } from "@/components/ui/enum-badge";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  cancelStatementExport,
  createStatementExport,
  downloadStatementExport,
  listStatementExports,
  type ExportFormat,
  type ExportRangeType,
  type TransactionExport
} from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Loader2, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";

const PER_PAGE = 20;

// The ledger's own OpenAPI advises polling an export no more often than every 2 seconds.
const POLL_INTERVAL_MS = 2_500;

// A ceiling, not a guess: after this many consecutive polls with something still active we stop and
// say why. An export that never leaves PENDING almost always means the ledger's task worker is off
// (`ledger.tasks.worker.enabled` defaults to false) — the one failure mode that otherwise looks like
// nothing at all happening, forever.
const MAX_POLLS = 120; // ≈ 5 minutes of continuous activity

const RANGE_TYPE_OPTIONS = [
  { value: "DAILY", label: "Daily" },
  { value: "WEEKLY", label: "Weekly" },
  { value: "MONTHLY", label: "Monthly" },
  { value: "YEARLY", label: "Yearly" },
  { value: "CUSTOM", label: "Custom" }
] as const satisfies readonly { value: ExportRangeType; label: string }[];

const FORMAT_OPTIONS = [
  { value: "CSV", label: "CSV" },
  { value: "PDF", label: "PDF" }
] as const satisfies readonly { value: ExportFormat; label: string }[];

function isActive(row: TransactionExport): boolean {
  return row.status === "PENDING" || row.status === "IN_PROGRESS";
}

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

function errorStatus(err: unknown): number | undefined {
  return err instanceof Error ? (err as { status?: number }).status : undefined;
}

function isLedgerProxyUnavailable(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const status = errorStatus(error);
  return (
    status === 503 ||
    (status === 500 && error.message.toLowerCase().includes("temporarily unavailable"))
  );
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

// The window's bounds arrive as zoneless ISO local date-times; the operator only cares about the
// days. `rangeTo` is exclusive (the ledger's window is half-open `[from, to)`) — the same convention
// the trial-balance page already labels, so we label it identically rather than quietly inventing an
// inclusive end that would disagree with the neighbouring report.
function windowLabel(row: TransactionExport): string {
  return `${row.rangeFrom.slice(0, 10)} → ${row.rangeTo.slice(0, 10)}`;
}

/**
 * The operator's whole interface to statement exports for one account: request one, watch it run,
 * download it, cancel it.
 *
 * The chaos machine renders nothing here — the ledger produces the statement and the chaos gateway
 * streams the bytes back (ADR-033 / ADR-034). Notably there is **no** download URL on the wire: the
 * ledger's presigned S3 link stays server-side, and `downloadable` is the only signal this component
 * gets. It is also why every failure below can say something true: the proxy propagates the ledger's
 * 400/403/404/409 faithfully (ADR-035), so a missing authority is a 403 and a cancel-too-late is a
 * 409 — not a blanket "not found" about a row the operator is looking straight at.
 */
export function StatementsTab({
  vaId,
  accountCode,
  ownershipType
}: {
  vaId: string;
  accountCode?: string | null;
  ownershipType?: string | null;
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const today = useMemo(() => new Date(), []);
  const firstOfThisMonth = useMemo(
    () => isoDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    [today]
  );
  const firstOfNextMonth = useMemo(
    () => isoDate(new Date(today.getFullYear(), today.getMonth() + 1, 1)),
    [today]
  );

  const [rangeType, setRangeType] = useState<ExportRangeType>("MONTHLY");
  const [format, setFormat] = useState<ExportFormat>("CSV");
  const [from, setFrom] = useState(firstOfThisMonth);
  const [to, setTo] = useState(firstOfNextMonth);
  const [formError, setFormError] = useState<string | null>(null);

  // Exports requested in *this* session. Only these raise completion toasts — opening the tab on a
  // page of last week's finished exports must not fire a burst of them.
  const sessionIdsRef = useRef<Set<string>>(new Set());
  const previousStatusRef = useRef<Map<string, TransactionExport["status"]>>(new Map());
  const pollsRef = useRef(0);
  const [pollCeilingHit, setPollCeilingHit] = useState(false);

  const query = useQuery({
    queryKey: ["statement-exports", vaId, { page }],
    queryFn: () => listStatementExports(token!, vaId, { page, pageSize: PER_PAGE }),
    placeholderData: keepPreviousData,
    retry: false,
    // Poll only while something on this page is actually running, and only up to the ceiling. The
    // interval collapses to `false` the moment everything is terminal, and the tab itself unmounts
    // when another tab is selected (the custom Tabs renders null for inactive content) — so there is
    // no background polling from a tab nobody is looking at.
    refetchInterval: q => {
      const items = q.state.data?.items ?? [];
      if (!items.some(isActive)) return false;
      return pollsRef.current >= MAX_POLLS ? false : POLL_INTERVAL_MS;
    }
  });

  const rows = useMemo(() => query.data?.items ?? [], [query.data]);
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;
  const anyActive = rows.some(isActive);

  // Count each completed fetch while something is still active; reset the moment nothing is.
  useEffect(() => {
    if (!anyActive) {
      pollsRef.current = 0;
      setPollCeilingHit(false);
      return;
    }
    pollsRef.current += 1;
    if (pollsRef.current >= MAX_POLLS) setPollCeilingHit(true);
  }, [query.dataUpdatedAt, anyActive]);

  // Toast on a *transition* observed against the previous snapshot, not on every poll that happens
  // to see a COMPLETED row — otherwise each 2.5 s tick would re-announce the same export.
  useEffect(() => {
    let progressed = false;

    for (const row of rows) {
      const previous = previousStatusRef.current.get(row.id);
      previousStatusRef.current.set(row.id, row.status);
      if (previous && previous !== row.status) progressed = true;

      if (!sessionIdsRef.current.has(row.id)) continue;
      if (!previous || previous === row.status) continue;
      if (previous !== "PENDING" && previous !== "IN_PROGRESS") continue;

      if (row.status === "COMPLETED") {
        toast.success("Statement ready", {
          description: `${row.format} for ${windowLabel(row)} — download it from the table.`,
          duration: 8_000
        });
      } else if (row.status === "FAILED") {
        toast.error("Statement export failed", {
          description: row.errorCode
            ? `The ledger reported ${row.errorCode}.`
            : "The ledger could not produce this statement.",
          duration: 10_000
        });
      }
    }

    // Something actually moved, so the ledger is working — give the poll budget back. Without this,
    // an export that crawled past the ceiling could never regain live updates even while visibly
    // making progress.
    if (progressed) {
      pollsRef.current = 0;
      setPollCeilingHit(previous => (previous ? false : previous));
    }
  }, [rows]);

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["statement-exports", vaId] });

  const createMutation = useMutation({
    mutationFn: () =>
      createStatementExport(token!, vaId, {
        format,
        rangeType,
        from: `${from}T00:00:00.000Z`,
        // Only CUSTOM carries an explicit end — every other range type is derived by the ledger from
        // the calendar period containing `from`.
        to: rangeType === "CUSTOM" ? `${to}T00:00:00.000Z` : undefined
      }),
    onSuccess: result => {
      // Remember it, and seed its current status, so the completion toast fires on the *next*
      // transition rather than immediately.
      sessionIdsRef.current.add(result.export.id);
      previousStatusRef.current.set(result.export.id, result.export.status);

      if (result.created) {
        toast.success("Statement export queued", {
          description: `${result.export.format} for ${windowLabel(result.export)}.`
        });
      } else {
        // The ledger's idempotency window: an export for this resolved window + format is already
        // active, so it handed us that one back instead of starting a second (ADR-033).
        toast.info("An export for this window is already running", {
          description: "Joined the export already in flight — no second one was created."
        });
      }
      setPage(0);
      invalidate();
    },
    onError: err =>
      toast.error("Could not request the statement", { description: getErrorMessage(err) })
  });

  const cancelMutation = useMutation({
    mutationFn: (exportId: string) => cancelStatementExport(token!, vaId, exportId),
    onSuccess: () => {
      toast.success("Export cancelled");
      invalidate();
    },
    onError: err => {
      // A 409 here means the export finished between the render and the click. Saying "not found"
      // about a row still on screen would be a lie — this is the founding example for ADR-035.
      const status = errorStatus(err);
      toast.error(status === 409 ? "Too late to cancel" : "Could not cancel the export", {
        description: getErrorMessage(err)
      });
      if (status === 409) invalidate();
    }
  });

  const downloadMutation = useMutation({
    mutationFn: (exportId: string) => downloadStatementExport(token!, vaId, exportId),
    onError: err => toast.error("Download failed", { description: getErrorMessage(err) })
  });

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!from) {
      setFormError("Pick a From date.");
      return;
    }
    if (rangeType === "CUSTOM" && !to) {
      setFormError("A custom range needs a To date.");
      return;
    }
    // Everything else — the 366-day cap, from < to, an unknown format — is the ledger's to judge.
    // It now returns a legible 400, and duplicating its rules here is how the two drift apart.
    setFormError(null);
    createMutation.mutate();
  }

  const status = errorStatus(query.error);
  const headerRow = (
    <TR>
      <TH>Window (to exclusive)</TH>
      <TH>Range</TH>
      <TH>Format</TH>
      <TH>Status</TH>
      <TH>Requested</TH>
      <TH className="text-right">Actions</TH>
    </TR>
  );

  let body: React.ReactNode;

  if (query.isLoading) {
    body = (
      <TableContainer className="rounded-lg border border-border bg-card">
        <Table>
          <THead>{headerRow}</THead>
          <TBody>
            <TableLoadingRows columns={6} rows={5} />
          </TBody>
        </Table>
      </TableContainer>
    );
  } else if (status === 403) {
    // The likeliest failure on a chaos machine, and it deserves a real explanation: the entire chart
    // of accounts is SYSTEM-owned, and the ledger resolves SYSTEM accounts to super-users only — so
    // the accounts an operator reaches for first are exactly the ones an org-scoped token cannot
    // export. Nothing the chaos machine can fix; it can at least name the requirement.
    const isSystem = (ownershipType ?? "").toUpperCase() === "SYSTEM";
    body = (
      <StatePanel
        title="Not authorized to export statements for this account"
        description={
          isSystem
            ? "This is a SYSTEM account (part of the chart of accounts), and the ledger resolves SYSTEM accounts to super-users only — statements here need a token carrying *:*::allow. An org-scoped token cannot export them, however it is scoped."
            : "The ledger rejected this request. The token needs the ledger_account_transactions:export::allow authority (or super-user *:*::allow), and the account must be within its organization scope."
        }
        tone="warning"
        icon="access"
        action={<Button onClick={() => void query.refetch()}>Retry</Button>}
      />
    );
  } else if (status === 404) {
    // Not "no statements yet" — the endpoint itself is not there. The export API landed in the
    // ledger on its own branch; against any build without it, every call 404s. An empty table here
    // would imply the feature works and this account simply has no exports, which is false.
    body = (
      <StatePanel
        title="Statement exports are unavailable on the connected ledger"
        description="The connected ss-ledger-service build does not expose the statement export API, so there is nothing to list. This is not an empty history — the capability is absent."
        tone="warning"
        icon="warning"
        action={<Button onClick={() => void query.refetch()}>Retry</Button>}
      />
    );
  } else if (query.error) {
    body = (
      <StatePanel
        title="Failed to load statement exports"
        description={
          isLedgerProxyUnavailable(query.error)
            ? "The ledger is currently degraded, so its exports cannot be listed."
            : getErrorMessage(query.error)
        }
        tone="danger"
        icon="error"
        action={<Button onClick={() => void query.refetch()}>Retry</Button>}
      />
    );
  } else if (rows.length === 0) {
    body = (
      <StatePanel
        title="No statements yet"
        description="Request an export above and it will appear here — the ledger renders it in the background and the row advances on its own."
      />
    );
  } else {
    body = (
      <TableContainer className="rounded-lg border border-border bg-card">
        <Table>
          <THead>{headerRow}</THead>
          <TBody>
            {rows.map(row => {
              const cancelling =
                cancelMutation.isPending && cancelMutation.variables === row.id;
              const downloading =
                downloadMutation.isPending && downloadMutation.variables === row.id;
              return (
                <TR key={row.id}>
                  <TD className="whitespace-nowrap tabular-nums">{windowLabel(row)}</TD>
                  <TD className="text-muted-foreground">{row.rangeType}</TD>
                  <TD>{row.format}</TD>
                  <TD>
                    <div className="flex flex-col gap-0.5">
                      <EnumBadge value={row.status} />
                      {row.status === "FAILED" && row.errorCode ? (
                        <span className="text-[10px] text-destructive">{row.errorCode}</span>
                      ) : null}
                    </div>
                  </TD>
                  <TD className="whitespace-nowrap text-muted-foreground">
                    {formatDate(row.initiatedAt)}
                  </TD>
                  <TD className="text-right">
                    <div className="flex justify-end gap-2">
                      {/* `downloadable` is server-derived (true only once COMPLETED). There is no
                          URL on this row to link to — the button asks the gateway for the bytes. */}
                      {row.downloadable ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={downloading}
                          onClick={() => downloadMutation.mutate(row.id)}
                        >
                          {downloading ? (
                            <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Download className="mr-1.5 h-3.5 w-3.5" />
                          )}
                          Download
                        </Button>
                      ) : null}
                      {isActive(row) ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={cancelling}
                          onClick={() => cancelMutation.mutate(row.id)}
                        >
                          {cancelling ? (
                            <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <X className="mr-1.5 h-3.5 w-3.5" />
                          )}
                          Cancel
                        </Button>
                      ) : null}
                    </div>
                  </TD>
                </TR>
              );
            })}
          </TBody>
        </Table>
      </TableContainer>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-[11px] text-muted-foreground">
        Account statements for{" "}
        {accountCode ? (
          <code className="rounded bg-muted px-1 py-0.5">{accountCode}</code>
        ) : (
          "this account"
        )}
        , rendered by the ledger and downloaded through the chaos gateway. The window is half-open —{" "}
        <strong>To</strong> is exclusive.
      </p>

      <Card>
        <CardContent className="p-4">
          <form className="flex flex-wrap items-end gap-3" onSubmit={submit}>
            <div className="flex flex-col gap-1">
              <label className="text-[10px] uppercase tracking-wide text-muted-foreground">
                Range
              </label>
              <Select
                value={rangeType}
                onChange={setRangeType}
                options={RANGE_TYPE_OPTIONS}
                className="w-36"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label
                htmlFor="statement-from"
                className="text-[10px] uppercase tracking-wide text-muted-foreground"
              >
                From
              </label>
              <Input
                id="statement-from"
                type="date"
                className="w-40"
                value={from}
                onChange={e => setFrom(e.target.value)}
              />
            </div>
            {/* Only CUSTOM takes an explicit end: for every other range type the ledger derives the
                window from the calendar period containing `from`, so offering a To here would be a
                control that does nothing. */}
            {rangeType === "CUSTOM" ? (
              <div className="flex flex-col gap-1">
                <label
                  htmlFor="statement-to"
                  className="text-[10px] uppercase tracking-wide text-muted-foreground"
                >
                  To (exclusive)
                </label>
                <Input
                  id="statement-to"
                  type="date"
                  className="w-40"
                  value={to}
                  onChange={e => setTo(e.target.value)}
                />
              </div>
            ) : null}
            <div className="flex flex-col gap-1">
              <label className="text-[10px] uppercase tracking-wide text-muted-foreground">
                Format
              </label>
              <Select
                value={format}
                onChange={setFormat}
                options={FORMAT_OPTIONS}
                className="w-28"
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? (
                <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
              ) : null}
              Request
            </Button>
          </form>
        </CardContent>
      </Card>

      {formError ? (
        <InlineNotice title="Incomplete request" description={formError} tone="danger" />
      ) : null}

      {pollCeilingHit && anyActive ? (
        <div className="space-y-2">
          <InlineNotice
            title="This export has not moved in a while"
            description="Live updates have stopped after several minutes without progress. An export that stays PENDING almost always means the ledger's statement worker is not running — check ledger.tasks.worker.enabled on the connected ledger, which is off by default. Nothing is wrong with the chaos machine; nothing is picking the job up."
            tone="warning"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              pollsRef.current = 0;
              setPollCeilingHit(false);
              void query.refetch();
            }}
          >
            Resume live updates
          </Button>
        </div>
      ) : null}

      {body}

      <ListPagination
        page={page}
        total={total}
        pageSize={PER_PAGE}
        itemLabel="export"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />
    </div>
  );
}
