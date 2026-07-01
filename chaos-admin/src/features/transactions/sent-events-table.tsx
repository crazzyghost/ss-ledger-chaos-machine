import { JsonPanel } from "@/components/layout/json-panel";
import { ListPagination } from "@/components/layout/list-pagination";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  listSentHistory,
  listTransactionFailuresByRequestIds,
  type HistoryFilters,
  type PublishRecordResponse,
  type TransactionFailureResponse
} from "@/lib/api";
import { formatDate, formatEnumValue, getStatusBadgeVariant } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { FileText } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// The ledger Outcome of a published row (Phase 017): distinct from the publish-to-Kafka Status.
// "No failure" frames the absence honestly (failures are asynchronous — never an affirmative success).
export function OutcomeCell({
  record,
  failureMap,
  loading
}: {
  record: PublishRecordResponse;
  failureMap: Map<string, TransactionFailureResponse>;
  loading: boolean;
}) {
  if (!record.transactionRequestId) {
    return (
      <span className="text-muted-foreground" title="Non-transactional flow — no ledger outcome">
        —
      </span>
    );
  }
  const failure = failureMap.get(record.transactionRequestId);
  if (failure) {
    return (
      <Badge variant="destructive" title={failure.failureReason ?? "Rejected by the ledger"}>
        Failed @ ledger{failure.failureCode ? ` · ${failure.failureCode}` : ""}
      </Badge>
    );
  }
  // Don't assert "no failure" until the per-page lookup has resolved — avoid transiently
  // mislabelling a genuinely-failed row.
  if (loading) {
    return (
      <span className="text-muted-foreground" title="Checking ledger outcome…">
        …
      </span>
    );
  }
  return (
    <Badge
      variant="neutral"
      title="Ledger acceptance — no failure observed (asynchronous; not a success guarantee)"
    >
      No failure
    </Badge>
  );
}

function HistoryDetailDialog({
  record,
  failure,
  open,
  onOpenChange
}: {
  record: PublishRecordResponse | null;
  failure?: TransactionFailureResponse | null;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  if (!record) return null;

  let parsedPayload: Record<string, unknown> | string | null = null;
  if (record.payloadJson) {
    try {
      parsedPayload = JSON.parse(record.payloadJson) as Record<string, unknown>;
    } catch {
      parsedPayload = record.payloadJson;
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Event — {record.eventType}</DialogTitle>
          <DialogDescription>
            {record.id} · {formatDate(record.createdAt)}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-3">
            {[
              { label: "Event ID", value: record.eventId, mono: true },
              { label: "Topic", value: record.topic, mono: true },
              { label: "Source", value: record.source },
              { label: "Status", value: record.status, badge: true },
              { label: "Correlation ID", value: record.correlationId ?? "—", mono: true },
              { label: "Source VA", value: record.sourceVaId ?? "—", mono: true },
              { label: "Destination VA", value: record.destinationVaId ?? "—", mono: true },
              { label: "Partition", value: record.kafkaPartition?.toString() ?? "—" },
              { label: "Offset", value: record.kafkaOffset?.toString() ?? "—" }
            ].map(f => (
              <div key={f.label}>
                <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">
                  {f.label}
                </dt>
                <dd className="mt-0.5 text-xs">
                  {f.badge ? (
                    <Badge variant={getStatusBadgeVariant(f.value as string)}>
                      {formatEnumValue(f.value as string)}
                    </Badge>
                  ) : f.mono ? (
                    <span className="font-mono">{f.value}</span>
                  ) : (
                    String(f.value)
                  )}
                </dd>
              </div>
            ))}
          </dl>
          {record.chaosStrategy && (
            <InlineNotice
              title={`Chaos: ${record.chaosStrategy}`}
              description={`This was an intentional ${record.intentionalFailure ? "failure" : "chaos"} event.`}
              tone="warning"
            />
          )}
          {failure && (
            <InlineNotice
              title={`Failed at ledger${failure.failureCode ? `: ${failure.failureCode}` : ""}`}
              description={`${failure.failureReason ?? "Rejected by the ledger"} · recording ${failure.ledgerTransactionId} · ${formatDate(failure.occurredAt)}`}
              tone="danger"
            />
          )}
          {parsedPayload && (
            <JsonPanel
              title="Event Payload"
              description="The serialized event envelope sent to Kafka."
              value={parsedPayload}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

/**
 * The published-event table (the familiar "Sent" columns incl. the Phase 017 ledger Outcome column).
 *
 * <p>Drives off `GET /api/v0/history` with the given filters and resolves each page's ledger outcome
 * with one batched `/transaction-failures` lookup. Reused by the Scenario Runner's Run History
 * accordion (expanding a run passes `{ batchId }` for a tracked run or `{ correlationId }` for an
 * untracked run) — so the run-event renderer lives in exactly one place (ADR-031).
 *
 * @param emptyMessage optional override for the empty-state copy
 */
export function SentEventsTable({
  filters,
  emptyMessage
}: {
  filters: HistoryFilters;
  emptyMessage?: string;
}) {
  const { token } = useSession();
  const [page, setPage] = useState(0);
  const [selectedRecord, setSelectedRecord] = useState<PublishRecordResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  useEffect(() => {
    setPage(0);
  }, [
    filters.vaId,
    filters.eventType,
    filters.correlationId,
    filters.batchId,
    filters.status,
    filters.from,
    filters.to
  ]);

  const query = useQuery({
    queryKey: ["history", { ...filters, page }],
    queryFn: () => listSentHistory(token!, { ...filters, page, size: PER_PAGE })
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  // One batch failures lookup per page (Phase 017): collect the page's request ids and resolve their
  // ledger outcome in a single call, building a requestId → failure map.
  const requestIds = useMemo(
    () =>
      Array.from(
        new Set(records.map(r => r.transactionRequestId).filter((id): id is string => Boolean(id)))
      ),
    [records]
  );
  const failuresQuery = useQuery({
    queryKey: ["transaction-failures", "by-request-ids", requestIds],
    queryFn: () => listTransactionFailuresByRequestIds(token!, requestIds),
    enabled: requestIds.length > 0
  });
  const failureMap = useMemo(() => {
    const map = new Map<string, TransactionFailureResponse>();
    for (const f of failuresQuery.data?.items ?? []) map.set(f.transactionRequestId, f);
    return map;
  }, [failuresQuery.data]);

  function openDetail(record: PublishRecordResponse) {
    setSelectedRecord(record);
    setDetailOpen(true);
  }

  const header = (
    <THead>
      <TR>
        <TH>Event Type</TH>
        <TH>Source VA</TH>
        <TH>Destination VA</TH>
        <TH title="Publish to Kafka">Status</TH>
        <TH title="Ledger acceptance (async)">Outcome</TH>
        <TH>Chaos</TH>
        <TH>Correlation ID</TH>
        <TH>Created</TH>
      </TR>
    </THead>
  );

  return (
    <>
      {query.isLoading ? (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            {header}
            <TBody>
              <TableLoadingRows columns={8} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load events"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : records.length === 0 ? (
        <StatePanel
          title="No sent events"
          description={emptyMessage ?? "No publish history found for the current filters."}
          iconNode={<FileText className="h-5 w-5" />}
        />
      ) : (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            {header}
            <TBody>
              {records.map(record => (
                <TR
                  key={record.id}
                  role="button"
                  tabIndex={0}
                  className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                  onClick={() => openDetail(record)}
                  onKeyDown={e => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      openDetail(record);
                    }
                  }}
                >
                  <TD className="font-mono text-xs">{record.eventType}</TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.sourceVaId ?? "—"}
                  </TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.destinationVaId ?? "—"}
                  </TD>
                  <TD>
                    <Badge variant={getStatusBadgeVariant(record.status)}>
                      {formatEnumValue(record.status)}
                    </Badge>
                  </TD>
                  <TD>
                    <OutcomeCell
                      record={record}
                      failureMap={failureMap}
                      loading={failuresQuery.isLoading}
                    />
                  </TD>
                  <TD>
                    {record.chaosStrategy ? (
                      <Badge variant="warning">{record.chaosStrategy}</Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TD>
                  <TD className="max-w-[10rem] truncate font-mono text-muted-foreground">
                    {record.correlationId ?? "—"}
                  </TD>
                  <TD>{formatDate(record.createdAt)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      )}

      <ListPagination
        page={page}
        total={total}
        pageSize={PER_PAGE}
        itemLabel="event"
        hasNextPage={hasNextPage}
        disabled={query.isFetching}
        onPrevious={() => setPage(p => Math.max(p - 1, 0))}
        onNext={() => setPage(p => p + 1)}
      />

      <HistoryDetailDialog
        record={selectedRecord}
        failure={
          selectedRecord?.transactionRequestId
            ? failureMap.get(selectedRecord.transactionRequestId) ?? null
            : null
        }
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />
    </>
  );
}
