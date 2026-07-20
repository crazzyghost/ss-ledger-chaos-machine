import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { formatDate, formatEnumValue } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, ShieldAlert, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { cancelConsistencyCheck, getConsistencyChecks, pollReconciliationMismatches, triggerConsistencyCheck } from "./api";
import type { ConsistencyCheckStatus, ConsistencyCheckType } from "./types";

const PER_PAGE = 20;

const TYPE_OPTIONS = [
  { value: "", label: "All types" },
  { value: "ALL", label: "All" },
  { value: "ACCOUNT_BALANCE_PROJECTION", label: "Account Balance Projection" },
  { value: "ENTRY_BALANCE", label: "Entry Balance" },
  { value: "SEQUENCE_INTEGRITY", label: "Sequence Integrity" }
];

const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "PENDING", label: "Pending" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "COMPLETED", label: "Completed" },
  { value: "FAILED", label: "Failed" }
];

const INITIATOR_TYPE_OPTIONS = [
  { value: "", label: "All initiators" },
  { value: "SYSTEM", label: "System" },
  { value: "PLATFORM_OPERATOR", label: "Platform Operator" }
];

type FilterState = { type: string; status: string; initiatorType: string };
const INITIAL_FILTERS: FilterState = { type: "", status: "", initiatorType: "" };

function getStatusBadgeVariant(
  status: ConsistencyCheckStatus
): "default" | "secondary" | "success" | "destructive" {
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

function useReconciliationMismatchPoller() {
  const { token } = useSession();
  const [lastPollTime, setLastPollTime] = useState(() => new Date().toISOString().slice(0, 19));
  const [isEnabled, setIsEnabled] = useState(true);
  const navigate = useNavigate();

  const pollQuery = useQuery({
    queryKey: ["reconciliation-mismatches", lastPollTime],
    queryFn: () => pollReconciliationMismatches(token!, lastPollTime),
    refetchInterval: isEnabled ? 500 : false,
    enabled: isEnabled && !!token
  });

  useEffect(() => {
    if (pollQuery.data?.items && pollQuery.data.items.length > 0) {
      pollQuery.data.items.forEach(mismatch => {
        toast.error(`Consistency check ${formatEnumValue(mismatch.type)} found ${mismatch.discrepancyCount} issue(s)`, {
          action: {
            label: "View",
            onClick: () => navigate(`/consistency-checks/${mismatch.checkId}`)
          },
          duration: 10000
        });
      });
      setLastPollTime(pollQuery.data.nextSince);
    }
  }, [pollQuery.data, navigate]);

  return { enablePoller: () => setIsEnabled(true), disablePoller: () => setIsEnabled(false) };
}

export function ConsistencyChecksPage() {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [filters, setFilters] = useState<FilterState>(INITIAL_FILTERS);
  const [page, setPage] = useState(0);
  const [showTriggerModal, setShowTriggerModal] = useState(false);
  const [selectedType, setSelectedType] = useState("ALL");

  const { enablePoller, disablePoller } = useReconciliationMismatchPoller();

  useEffect(() => {
    setPage(0);
  }, [filters.type, filters.status, filters.initiatorType]);

  const checksQuery = useQuery({
    queryKey: ["consistency-checks", { ...filters, page }],
    queryFn: () =>
      getConsistencyChecks(token!, {
        type: filters.type || undefined,
        status: filters.status || undefined,
        initiatorType: filters.initiatorType || undefined,
        page,
        size: PER_PAGE
      }),
    enabled: !!token
  });

  const triggerMutation = useMutation({
    mutationFn: (type: string) => triggerConsistencyCheck(token!, type === "ALL" ? undefined : type),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ["consistency-checks"] });
      toast.success(`Triggered ${data.checks.length} consistency check(s)`);
      setShowTriggerModal(false);
      enablePoller();
      setTimeout(() => disablePoller(), 60000); // Poll for 60s after trigger
    },
    onError: (error) => {
      toast.error(`Failed to trigger check: ${error instanceof Error ? error.message : "Unknown error"}`);
    }
  });

  const cancelMutation = useMutation({
    mutationFn: (checkId: string) => cancelConsistencyCheck(token!, checkId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["consistency-checks"] });
      toast.success("Consistency check cancelled");
    },
    onError: (error: Error) => {
      const status = (error as { status?: number }).status;
      toast.error(status === 409 ? "Too late to cancel" : "Failed to cancel check", {
        description: error.message
      });
    }
  });

  const checks = checksQuery.data?.items ?? [];
  const total = checksQuery.data?.totalElements ?? 0;
  const hasNextPage = checksQuery.data?.hasNext ?? false;

  const isLedgerUnavailable = checksQuery.error instanceof Error && checksQuery.error.message.includes("404");
  const isLedgerOverloaded = checksQuery.error instanceof Error && checksQuery.error.message.includes("503");

  return (
    <Page>
      <PageHeader
        title="Consistency Checks"
        description="Trigger and inspect the ledger's internal consistency checks. Each check verifies integrity (balance projections, entry sums, sequence numbers) after hostile scenarios."
        actions={
          <Button onClick={() => setShowTriggerModal(true)} disabled={isLedgerUnavailable}>
            <ShieldAlert className="mr-2 h-4 w-4" />
            Trigger Check
          </Button>
        }
      />
      <PageContent>
        {isLedgerUnavailable && (
          <InlineNotice
            tone="default"
            className="mb-4"
            description="Consistency checks are unavailable on the connected ledger."
          />
        )}
        {isLedgerOverloaded && (
          <InlineNotice
            tone="warning"
            className="mb-4"
            description="Ledger is temporarily unavailable (under load). Retry in a few moments."
          />
        )}

        {/* Filter bar */}
        <div className="flex flex-wrap items-center gap-2">
          <Select
            className="w-52"
            value={filters.type}
            onChange={value => setFilters(f => ({ ...f, type: value }))}
            options={TYPE_OPTIONS}
            placeholder="All types"
          />
          <Select
            className="w-44"
            value={filters.status}
            onChange={value => setFilters(f => ({ ...f, status: value }))}
            options={STATUS_OPTIONS}
            placeholder="All statuses"
          />
          <Select
            className="w-48"
            value={filters.initiatorType}
            onChange={value => setFilters(f => ({ ...f, initiatorType: value }))}
            options={INITIATOR_TYPE_OPTIONS}
            placeholder="All initiators"
          />
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setFilters(INITIAL_FILTERS)}
          >
            Clear
          </Button>
        </div>

        {checksQuery.isLoading ? (
          <TableContainer className="border-y border-border bg-card">
            <Table>
              <THead>
                <TR>
                  <TH>Check ID</TH>
                  <TH>Type</TH>
                  <TH>Status</TH>
                  <TH>Initiator</TH>
                  <TH>Initiated At</TH>
                  <TH>Discrepancies</TH>
                  <TH className="text-right">Actions</TH>
                </TR>
              </THead>
              <TBody>
                <TableLoadingRows columns={7} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : checksQuery.error && !isLedgerUnavailable ? (
          <StatePanel
            title="Failed to load checks"
            description={checksQuery.error instanceof Error ? checksQuery.error.message : "Unknown error"}
            tone="danger"
            icon="error"
            action={<Button onClick={() => void checksQuery.refetch()}>Retry</Button>}
          />
        ) : checks.length === 0 && !isLedgerUnavailable ? (
          <StatePanel
            title="No consistency checks"
            description="No checks match the current filters. Trigger a new check to get started."
            iconNode={<ShieldAlert className="h-5 w-5" />}
          />
        ) : !isLedgerUnavailable ? (
          <>
            <TableContainer className="border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Check ID</TH>
                    <TH>Type</TH>
                    <TH>Status</TH>
                    <TH>Initiator</TH>
                    <TH>Initiated At</TH>
                    <TH>Discrepancies</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  {checks.map(check => {
                    const isPending = check.status === "PENDING";
                    const isStale =
                      isPending &&
                      Date.now() - new Date(check.initiatedAt).getTime() > 30000;
                    const isCancellable = check.status === "PENDING" || check.status === "IN_PROGRESS";
                    const cancelling = cancelMutation.isPending && cancelMutation.variables === check.checkId;

                    return (
                      <TR
                        key={check.checkId}
                        className="cursor-pointer hover:bg-muted/50"
                        onClick={() => navigate(`/consistency-checks/${check.checkId}`)}
                      >
                        <TD className="font-mono text-xs">
                          <Link
                            to={`/consistency-checks/${check.checkId}`}
                            className="text-primary hover:underline"
                            onClick={e => e.stopPropagation()}
                          >
                            {check.checkId.substring(0, 8)}...
                          </Link>
                        </TD>
                        <TD>{formatEnumValue(check.type)}</TD>
                        <TD>
                          <div className="flex items-center gap-2">
                            <Badge variant={getStatusBadgeVariant(check.status)}>
                              {check.status}
                            </Badge>
                            {isStale && (
                              <span className="text-xs text-yellow-600">
                                ⚠ Task worker may not be running
                              </span>
                            )}
                          </div>
                        </TD>
                        <TD>{formatEnumValue(check.initiatorType)}</TD>
                        <TD>{formatDate(check.initiatedAt)}</TD>
                        <TD>
                          {check.discrepancyCount === 0 ? (
                            <span className="text-muted-foreground">—</span>
                          ) : (
                            <span className="font-semibold text-destructive">
                              {check.discrepancyCount}
                            </span>
                          )}
                        </TD>
                        <TD className="text-right" onClick={e => e.stopPropagation()}>
                          <div className="flex justify-end">
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={!isCancellable || cancelling}
                              onClick={() => cancelMutation.mutate(check.checkId)}
                            >
                              {cancelling ? (
                                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <X className="mr-1.5 h-3.5 w-3.5" />
                              )}
                              Cancel
                            </Button>
                          </div>
                        </TD>
                      </TR>
                    );
                  })}
                </TBody>
              </Table>
            </TableContainer>
            <ListPagination
              page={page}
              total={total}
              pageSize={PER_PAGE}
              itemLabel="check"
              hasNextPage={hasNextPage}
              onPrevious={() => setPage(p => Math.max(0, p - 1))}
              onNext={() => setPage(p => p + 1)}
            />
          </>
        ) : null}
      </PageContent>

      {/* Trigger Check Modal */}
      <Dialog open={showTriggerModal} onOpenChange={setShowTriggerModal}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Trigger Consistency Check</DialogTitle>
            <DialogDescription>
              Select a check type to trigger. The check will be processed asynchronously by the
              ledger's task queue.
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Select
              value={selectedType}
              onChange={setSelectedType}
              options={TYPE_OPTIONS.filter(opt => opt.value !== "")}
              placeholder="Select check type"
            />
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setShowTriggerModal(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => triggerMutation.mutate(selectedType)}
              disabled={triggerMutation.isPending}
            >
              {triggerMutation.isPending ? "Triggering..." : "Trigger"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Page>
  );
}
