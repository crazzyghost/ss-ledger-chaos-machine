import { ListPagination } from "@/components/layout/list-pagination";
import { JsonPanel } from "@/components/layout/json-panel";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { DetailPanelSkeleton, InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import { formatDate, formatEnumValue } from "@/lib/utils";
import { usePersistedTabs } from "@/lib/use-persisted-tabs";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ChevronDown, ChevronRight, XCircle } from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { cancelConsistencyCheck, getConsistencyCheck, getConsistencyCheckDiscrepancies } from "./api";
import type { ConsistencyCheckStatus } from "./types";

const PER_PAGE = 20;

function getStatusBadgeVariant(
  status: ConsistencyCheckStatus
): BadgeVariant {
  switch (status) {
    case "PENDING":
      return "secondary";
    case "IN_PROGRESS":
      return "default";
    case "COMPLETED":
      return "success";
    case "FAILED":
      return "destructive";
  }
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 break-all text-xs">{children}</dd>
    </div>
  );
}

export function ConsistencyCheckDetailPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { checkId } = useParams<{ checkId: string }>();
  const [tab, setTab] = usePersistedTabs("tab", "overview");
  const [codeFilter, setCodeFilter] = useState("");
  const [page, setPage] = useState(0);
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  const checkQuery = useQuery({
    queryKey: ["consistency-check", checkId],
    queryFn: () => getConsistencyCheck(token!, checkId!),
    enabled: Boolean(checkId)
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelConsistencyCheck(token!, checkId!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["consistency-check", checkId] });
      void queryClient.invalidateQueries({ queryKey: ["consistency-checks"] });
      toast.success("Consistency check cancelled");
    },
    onError: (error: Error) => {
      toast.error("Failed to cancel check", {
        description: error.message
      });
    }
  });

  const discrepanciesQuery = useQuery({
    queryKey: ["consistency-check-discrepancies", checkId, codeFilter, page],
    queryFn: () =>
      getConsistencyCheckDiscrepancies(token!, checkId!, {
        code: codeFilter || undefined,
        page,
        size: PER_PAGE
      }),
    enabled: Boolean(checkId) && tab === "discrepancies",
    placeholderData: keepPreviousData
  });

  const check = checkQuery.data;
  const discrepancies = discrepanciesQuery.data?.items ?? [];
  const hasNextPage = discrepanciesQuery.data?.hasNext ?? false;
  const total = discrepanciesQuery.data?.totalElements ?? 0;

  // Extract unique codes from discrepancies for filter dropdown
  const availableCodes = Array.from(new Set(discrepancies.map(d => d.code)));
  const codeOptions = [
    { value: "", label: "All codes" },
    ...availableCodes.map(code => ({ value: code, label: formatEnumValue(code) }))
  ];

  const isPending = check?.status === "PENDING";
  const isStale =
    isPending &&
    check?.initiatedAt &&
    Date.now() - new Date(check.initiatedAt).getTime() > 30000;

  const backButton = (
    <Button variant="ghost" size="sm" onClick={() => navigate("/ledger/consistency-checks")}>
      <ArrowLeft className="mr-1.5 h-4 w-4" />
      Back to list
    </Button>
  );

  const toggleRow = (id: string) => {
    setExpandedRows(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  return (
    <Page>
      <PageHeader
        title={check ? formatEnumValue(check.type) : "Consistency Check"}
        description={check ? `Check ${check.checkId}` : "Loading…"}
        leadingActions={backButton}
        actions={
          check && (check.status === "PENDING" || check.status === "IN_PROGRESS") ? (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => cancelMutation.mutate()}
              disabled={cancelMutation.isPending}
            >
              <XCircle className="mr-2 h-4 w-4" />
              {cancelMutation.isPending ? "Cancelling…" : "Cancel Check"}
            </Button>
          ) : null
        }
      />
      <PageContent>
        {checkQuery.isLoading ? (
          <DetailPanelSkeleton includeSidebar={false} />
        ) : checkQuery.error ? (
          <StatePanel
            title={
              checkQuery.error instanceof Error && /404|not found/i.test(checkQuery.error.message)
                ? "Check not found"
                : "Failed to load check"
            }
            description={
              checkQuery.error instanceof Error
                ? checkQuery.error.message
                : "Unknown error"
            }
            tone="danger"
            icon="error"
            action={<Button onClick={() => void checkQuery.refetch()}>Retry</Button>}
            secondaryAction={
              <Button variant="ghost" onClick={() => navigate("/ledger/consistency-checks")}>
                Back to list
              </Button>
            }
          />
        ) : !check ? (
          <StatePanel
            title="Check not found"
            description="It may have been deleted or never existed."
            action={
              <Button onClick={() => navigate("/ledger/consistency-checks")}>Back to list</Button>
            }
          />
        ) : (
          <>
            {isStale && (
              <InlineNotice
                tone="warning"
                className="mb-4"
                title="Task worker may not be running."
                description="This check has been pending for over 30 seconds. The ledger's task worker may not be enabled."
              />
            )}

            <Tabs value={tab} defaultValue="overview" onValueChange={setTab}>
              <TabsList>
                <TabsTrigger value="overview">Overview</TabsTrigger>
                <TabsTrigger value="discrepancies">
                  Discrepancies ({check.discrepancyCount})
                </TabsTrigger>
              </TabsList>

              <TabsContent value="overview" className="pt-5">
                <Card>
                  <CardHeader>
                    <CardTitle>Check Details</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <dl className="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-3">
                      <Field label="Type">{formatEnumValue(check.type)}</Field>
                      <Field label="Status">
                        <Badge variant={getStatusBadgeVariant(check.status)}>
                          {check.status}
                        </Badge>
                      </Field>
                      <Field label="Initiator Type">
                        {formatEnumValue(check.initiatorType)}
                      </Field>
                      <Field label="Initiated By">
                        {check.initiatedBy ? (
                          <span className="font-mono text-xs">{check.initiatedBy}</span>
                        ) : (
                          "—"
                        )}
                      </Field>
                      <Field label="As Of">
                        {check.asOf ? formatDate(check.asOf) : "—"}
                      </Field>
                      <Field label="Initiated At">
                        {formatDate(check.initiatedAt)}
                      </Field>
                      <Field label="Completed At">
                        {check.completedAt ? formatDate(check.completedAt) : "—"}
                      </Field>
                      <Field label="Errored At">
                        {check.erroredAt ? formatDate(check.erroredAt) : "—"}
                      </Field>
                      <Field label="Error Code">
                        {check.errorCode ? (
                          <Badge variant="destructive">{check.errorCode}</Badge>
                        ) : (
                          "—"
                        )}
                      </Field>
                      <Field label="Discrepancy Count">
                        {check.discrepancyCount === 0 ? (
                          <span className="text-muted-foreground">0</span>
                        ) : (
                          <span className="font-semibold text-destructive">
                            {check.discrepancyCount}
                          </span>
                        )}
                      </Field>
                    </dl>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="discrepancies" className="pt-5">
                {check.discrepancyCount > 0 && (
                  <>
                    <div className="mb-4 flex items-center gap-2">
                      <Select
                        className="w-52"
                        value={codeFilter}
                        onChange={value => {
                          setCodeFilter(value);
                          setPage(0);
                        }}
                        options={codeOptions}
                        placeholder="All codes"
                      />
                    </div>

                    {discrepanciesQuery.isLoading ? (
                      <TableContainer className="border-y border-border bg-card">
                        <Table>
                          <THead>
                            <TR>
                              <TH className="w-8"></TH>
                              <TH>Code</TH>
                              <TH>Account ID</TH>
                              <TH>Entry ID</TH>
                              <TH>Detected At</TH>
                            </TR>
                          </THead>
                          <TBody>
                            <TableLoadingRows columns={5} rows={6} />
                          </TBody>
                        </Table>
                      </TableContainer>
                    ) : discrepanciesQuery.error ? (
                      <StatePanel
                        title="Failed to load discrepancies"
                        description={
                          discrepanciesQuery.error instanceof Error
                            ? discrepanciesQuery.error.message
                            : "Unknown error"
                        }
                        tone="danger"
                        icon="error"
                        action={
                          <Button onClick={() => void discrepanciesQuery.refetch()}>
                            Retry
                          </Button>
                        }
                      />
                    ) : discrepancies.length === 0 ? (
                      <StatePanel
                        title="No discrepancies"
                        description="No findings match the current filters."
                      />
                    ) : (
                      <>
                        <TableContainer className="border-y border-border bg-card">
                          <Table>
                            <THead>
                              <TR>
                                <TH className="w-8"></TH>
                                <TH>Code</TH>
                                <TH>Account ID</TH>
                                <TH>Entry ID</TH>
                                <TH>Detected At</TH>
                              </TR>
                            </THead>
                            <TBody>
                              {discrepancies.map(discrepancy => {
                                const isExpanded = expandedRows.has(discrepancy.id);
                                return (
                                  <>
                                    <TR key={discrepancy.id}>
                                      <TD>
                                        <button
                                          onClick={() => toggleRow(discrepancy.id)}
                                          className="flex items-center text-muted-foreground hover:text-foreground"
                                        >
                                          {isExpanded ? (
                                            <ChevronDown className="h-4 w-4" />
                                          ) : (
                                            <ChevronRight className="h-4 w-4" />
                                          )}
                                        </button>
                                      </TD>
                                      <TD>
                                        <Badge variant="neutral">
                                          {formatEnumValue(discrepancy.code)}
                                        </Badge>
                                      </TD>
                                      <TD>
                                        {discrepancy.accountId ? (
                                          <Link
                                            to={`/virtual-accounts/${discrepancy.accountId}`}
                                            className="font-mono text-xs text-primary hover:underline"
                                            onClick={e => e.stopPropagation()}
                                          >
                                            {discrepancy.accountId.substring(0, 8)}...
                                          </Link>
                                        ) : (
                                          <span className="text-muted-foreground">—</span>
                                        )}
                                      </TD>
                                      <TD>
                                        {discrepancy.entryId ? (
                                          <span className="font-mono text-xs">
                                            {discrepancy.entryId.substring(0, 8)}...
                                          </span>
                                        ) : (
                                          <span className="text-muted-foreground">—</span>
                                        )}
                                      </TD>
                                      <TD>{formatDate(discrepancy.detectedAt)}</TD>
                                    </TR>
                                    {isExpanded && (
                                      <TR key={`${discrepancy.id}-details`}>
                                        <TD colSpan={5} className="bg-muted/30 p-4">
                                          <div className="text-xs">
                                            <p className="mb-2 font-semibold text-muted-foreground">
                                              Details
                                            </p>
                                            <JsonPanel
                                              title="Discrepancy Details"
                                              description="Raw JSON data from the consistency check"
                                              value={discrepancy.details}
                                            />
                                          </div>
                                        </TD>
                                      </TR>
                                    )}
                                  </>
                                );
                              })}
                            </TBody>
                          </Table>
                        </TableContainer>
                        <ListPagination
                          page={page}
                          total={total}
                          pageSize={PER_PAGE}
                          itemLabel="discrepancy"
                          hasNextPage={hasNextPage}
                          onPrevious={() => setPage(p => Math.max(0, p - 1))}
                          onNext={() => setPage(p => p + 1)}
                        />
                      </>
                    )}
                  </>
                )}
              </TabsContent>
            </Tabs>
          </>
        )}
      </PageContent>
    </Page>
  );
}
