import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { isBatchTerminal, listBatches } from "@/lib/api";
import { formatDate, formatEnumValue, getStatusBadgeVariant } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { LayersIcon, Plus } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

export function BatchesPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["batches", page],
    queryFn: () => listBatches(token!, { page, size: PER_PAGE })
  });

  const batches = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Batch Runs"
        description="History of all CSV batch runs submitted to the chaos machine."
        actions={
          <Button onClick={() => navigate("/chaos/upload")}>
            <Plus className="mr-1.5 h-4 w-4" />
            New Batch
          </Button>
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
            {query.isLoading ? (
              <TableContainer className="flex-1 border-y border-border bg-card">
                <Table>
                  <THead>
                    <TR>
                      <TH>ID</TH>
                      <TH>Flow Type</TH>
                      <TH>File</TH>
                      <TH>Status</TH>
                      <TH className="text-right">Total</TH>
                      <TH className="text-right">Succeeded</TH>
                      <TH className="text-right">Failed</TH>
                      <TH>Created</TH>
                    </TR>
                  </THead>
                  <TBody>
                    <TableLoadingRows columns={8} rows={6} />
                  </TBody>
                </Table>
              </TableContainer>
            ) : query.error ? (
              <StatePanel
                title="Failed to load batches"
                description={getErrorMessage(query.error)}
                tone="danger"
                icon="error"
                action={<Button onClick={() => void query.refetch()}>Retry</Button>}
              />
            ) : batches.length === 0 ? (
              <StatePanel
                title="No batch runs yet"
                description="Submit a CSV file to start your first batch run."
                iconNode={<LayersIcon className="h-5 w-5" />}
                action={
                  <Button onClick={() => navigate("/chaos/upload")}>Upload CSV</Button>
                }
              />
            ) : (
              <TableContainer className="flex-1 border-y border-border bg-card">
                <Table>
                  <THead>
                    <TR>
                      <TH>ID</TH>
                      <TH>Flow Type</TH>
                      <TH>File</TH>
                      <TH>Status</TH>
                      <TH className="text-right">Total</TH>
                      <TH className="text-right">OK</TH>
                      <TH className="text-right">Failed</TH>
                      <TH>Created</TH>
                    </TR>
                  </THead>
                  <TBody>
                    {batches.map(batch => (
                      <TR
                        key={batch.id}
                        role="button"
                        tabIndex={0}
                        className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                        onClick={() => navigate(`/chaos/batches/${batch.id}`)}
                        onKeyDown={e => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            navigate(`/chaos/batches/${batch.id}`);
                          }
                        }}
                      >
                        <TD className="max-w-[10rem] truncate font-mono text-xs text-muted-foreground">
                          {batch.id}
                        </TD>
                        <TD>{formatEnumValue(batch.flowType)}</TD>
                        <TD className="max-w-[10rem] truncate text-muted-foreground">
                          {batch.filename ?? "—"}
                        </TD>
                        <TD>
                          <Badge variant={getStatusBadgeVariant(batch.status)}>
                            {formatEnumValue(batch.status)}
                          </Badge>
                          {!isBatchTerminal(batch.status) && (
                            <span className="ml-2 h-1.5 w-1.5 inline-block animate-ping rounded-full bg-amber-400" />
                          )}
                        </TD>
                        <TD className="text-right tabular-nums">{batch.total}</TD>
                        <TD className="text-right tabular-nums text-emerald-600">
                          {batch.succeeded}
                        </TD>
                        <TD className="text-right tabular-nums text-destructive">
                          {batch.failed}
                        </TD>
                        <TD>{formatDate(batch.createdAt)}</TD>
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
              itemLabel="batch"
              hasNextPage={hasNextPage}
              disabled={query.isFetching}
              onPrevious={() => setPage(p => Math.max(p - 1, 0))}
              onNext={() => setPage(p => p + 1)}
            />
          </div>
      </PageContent>
    </Page>
  );
}
