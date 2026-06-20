import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  getBatch,
  isBatchTerminal,
  listBatchRows,
  type BatchRowResponse,
  type BatchRunResponse
} from "@/lib/api";
import { formatDate, formatEnumValue, getStatusBadgeVariant } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

const ROWS_PER_PAGE = 50;
const POLL_INTERVAL_MS = 1500;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Stats row
// ---------------------------------------------------------------------------

function StatCard({ label, value, variant }: { label: string; value: number; variant?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-2xl tabular-nums">{value}</CardTitle>
      </CardHeader>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Rows sub-component
// ---------------------------------------------------------------------------

function BatchRowsTable({
  token,
  batchId,
  poll
}: {
  token: string;
  batchId: string;
  poll: boolean;
}) {
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["batch-rows", batchId, page],
    queryFn: () => listBatchRows(token, batchId, { page, size: ROWS_PER_PAGE }),
    refetchInterval: poll ? POLL_INTERVAL_MS : false
  });

  const rows = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * ROWS_PER_PAGE < total;

  return (
    <div className="space-y-4">
      {query.isLoading ? (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>#</TH>
                <TH>Status</TH>
                <TH>Event ID</TH>
                <TH>Error</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              <TableLoadingRows columns={5} rows={8} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load rows"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
        />
      ) : rows.length === 0 ? (
        <StatePanel
          title="No rows yet"
          description="Batch rows will appear here as they are processed."
        />
      ) : (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>#</TH>
                <TH>Status</TH>
                <TH>Event ID</TH>
                <TH>Error</TH>
                <TH>Created</TH>
              </TR>
            </THead>
            <TBody>
              {rows.map(row => (
                <TR key={row.id}>
                  <TD className="tabular-nums">{row.rowNumber}</TD>
                  <TD>
                    <Badge variant={getStatusBadgeVariant(row.status)}>
                      {formatEnumValue(row.status)}
                    </Badge>
                  </TD>
                  <TD className="max-w-[14rem] truncate font-mono text-xs text-muted-foreground">
                    {row.eventId ?? "—"}
                  </TD>
                  <TD className="max-w-xs truncate text-destructive">
                    {row.error ?? "—"}
                  </TD>
                  <TD>{formatDate(row.createdAt)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      )}
      <ListPagination
        page={page}
        total={total}
        pageSize={ROWS_PER_PAGE}
        itemLabel="row"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Batch Run Page
// ---------------------------------------------------------------------------

export function BatchRunPage() {
  const navigate = useNavigate();
  const { batchId } = useParams<{ batchId: string }>();
  const { token } = useSession();

  const query = useQuery({
    queryKey: ["batch", batchId],
    queryFn: () => getBatch(token!, batchId!),
    enabled: Boolean(batchId),
    refetchInterval: query =>
      query.state.data && isBatchTerminal(query.state.data.status)
        ? false
        : POLL_INTERVAL_MS
  });

  const batch = query.data;

  if (query.isLoading) {
    return (
      <Page>
        <PageHeader title="Batch Run" description="Loading…" />
        <PageContent>
          <div className="h-40 animate-pulse rounded-lg border border-border bg-muted/40" />
        </PageContent>
      </Page>
    );
  }

  if (query.error || !batch) {
    return (
      <Page>
        <PageHeader title="Batch Run" description="Error" />
        <PageContent>
          <StatePanel
            title="Failed to load batch"
            description={getErrorMessage(query.error)}
            tone="danger"
            icon="error"
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          />
        </PageContent>
      </Page>
    );
  }

  const isTerminal = isBatchTerminal(batch.status);

  return (
    <Page>
      <PageHeader
        title={`Batch — ${batch.filename ?? batchId}`}
        description={`${formatEnumValue(batch.flowType)} · ${batch.total} rows`}
        leadingActions={
          <Button variant="ghost" size="sm" onClick={() => navigate("/chaos/batches")}>
            <ArrowLeft className="mr-1.5 h-4 w-4" />
            All Batches
          </Button>
        }
      />
      <PageContent>
        {/* Status + auto-refresh indicator */}
        <div className="flex items-center gap-3">
          <Badge variant={getStatusBadgeVariant(batch.status)}>
            {formatEnumValue(batch.status)}
          </Badge>
          {!isTerminal && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <span className="h-2 w-2 animate-ping rounded-full bg-amber-400" />
              Live — refreshing every {POLL_INTERVAL_MS / 1000}s
            </span>
          )}
          {isTerminal && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              {batch.status === "COMPLETED" ? (
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
              ) : (
                <XCircle className="h-3.5 w-3.5 text-destructive" />
              )}
              Completed {formatDate(batch.completedAt)}
            </span>
          )}
        </div>

        {/* Counters */}
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <StatCard label="Total rows" value={batch.total} />
          <StatCard label="Succeeded" value={batch.succeeded} variant="success" />
          <StatCard label="Failed" value={batch.failed} variant="destructive" />
          <StatCard label="Invalid" value={batch.invalid} variant="warning" />
        </div>

        {/* Row detail */}
        <Card>
          <CardHeader>
            <CardTitle>Row Results</CardTitle>
          </CardHeader>
          <CardContent>
            {batchId && token && (
              <BatchRowsTable token={token} batchId={batchId} poll={!isTerminal} />
            )}
          </CardContent>
        </Card>
      </PageContent>
    </Page>
  );
}
