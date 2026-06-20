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
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  ApiError,
  createVirtualAccount,
  listVirtualAccounts,
  type VirtualAccountFilters
} from "@/lib/api";
import { formatDate, formatEnumValue, getStatusBadgeVariant } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Wallet } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Create VA Dialog
// ---------------------------------------------------------------------------

function CreateVirtualAccountDialog() {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [ownershipType, setOwnershipType] = useState("ORGANIZATION");
  const [currency, setCurrency] = useState("GHS");
  const [organizationId, setOrganizationId] = useState("");
  const [channel, setChannel] = useState("");
  const [announce, setAnnounce] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const ownershipOptions = [
    { value: "ORGANIZATION", label: "Organization" },
    { value: "SYSTEM", label: "System" }
  ] as const;

  const mutation = useMutation({
    mutationFn: () =>
      createVirtualAccount(token!, {
        name: name.trim(),
        ownershipType,
        currency: currency.trim().toUpperCase(),
        organizationId: organizationId.trim() || undefined,
        channel: channel.trim() || undefined,
        announce
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["virtual-accounts"] });
      setOpen(false);
      resetForm();
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function resetForm() {
    setName("");
    setOwnershipType("ORGANIZATION");
    setCurrency("GHS");
    setOrganizationId("");
    setChannel("");
    setAnnounce(false);
    setFormError(null);
  }

  function handleCreate() {
    setFormError(null);
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    if (!currency.trim()) {
      setFormError("Currency is required.");
      return;
    }
    if (ownershipType === "ORGANIZATION" && !organizationId.trim()) {
      setFormError("Organization ID is required for ORGANIZATION type accounts.");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        setOpen(next);
        if (!next) resetForm();
      }}
    >
      <DialogTrigger asChild>
        <Button>
          <Plus className="mr-1.5 h-4 w-4" />
          Create Virtual Account
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create Virtual Account</DialogTitle>
          <DialogDescription>
            Register a new virtual account in the chaos machine registry.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="e.g. Platform Float Account"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Ownership Type</label>
            <Select
              value={ownershipType as typeof ownershipOptions[number]["value"]}
              onChange={v => setOwnershipType(v)}
              options={ownershipOptions}
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Currency</label>
            <Input
              value={currency}
              onChange={e => setCurrency(e.target.value.toUpperCase())}
              placeholder="GHS"
              maxLength={3}
            />
          </div>
          {ownershipType === "ORGANIZATION" && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Organization ID</label>
              <Input
                value={organizationId}
                onChange={e => setOrganizationId(e.target.value)}
                placeholder="org-ulid"
              />
            </div>
          )}
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Channel (optional)</label>
            <Input
              value={channel}
              onChange={e => setChannel(e.target.value)}
              placeholder="MTN, TELECEL, etc."
            />
          </div>
          <label className="flex cursor-pointer items-center gap-2 text-xs">
            <input
              type="checkbox"
              checked={announce}
              onChange={e => setAnnounce(e.target.checked)}
              className="rounded border-input"
            />
            Announce VA creation to Kafka
          </label>
          {formError && <InlineNotice description={formError} tone="danger" />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreate} disabled={mutation.isPending}>
            {mutation.isPending ? "Creating…" : "Create"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Filters
// ---------------------------------------------------------------------------

const OWNERSHIP_OPTIONS = [
  { value: "" as const, label: "All ownership" },
  { value: "ORGANIZATION" as const, label: "Organization" },
  { value: "SYSTEM" as const, label: "System" }
] as const;

const STATUS_OPTIONS = [
  { value: "" as const, label: "All statuses" },
  { value: "ACTIVE" as const, label: "Active" },
  { value: "SUSPENDED" as const, label: "Suspended" },
  { value: "FROZEN" as const, label: "Frozen" },
  { value: "DORMANT" as const, label: "Dormant" },
  { value: "CLOSED" as const, label: "Closed" }
] as const;

type Filters = {
  ownershipType: string;
  currency: string;
  status: string;
  search: string;
};

const INITIAL_FILTERS: Filters = {
  ownershipType: "",
  currency: "",
  status: "",
  search: ""
};

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function VirtualAccountsPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<Filters>(INITIAL_FILTERS);
  const [applied, setApplied] = useState<Filters>(INITIAL_FILTERS);

  const filters: VirtualAccountFilters = {
    page,
    perPage: PER_PAGE,
    ownershipType: applied.ownershipType || undefined,
    currency: applied.currency || undefined,
    status: applied.status || undefined,
    search: applied.search || undefined
  };

  const query = useQuery({
    queryKey: ["virtual-accounts", filters],
    queryFn: () => listVirtualAccounts(token!, filters)
  });

  const accounts = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  function applyFilters() {
    setPage(0);
    setApplied(draft);
  }

  function clearFilters() {
    setPage(0);
    setDraft(INITIAL_FILTERS);
    setApplied(INITIAL_FILTERS);
  }

  return (
    <Page>
      <PageHeader
        title="Virtual Accounts"
        description="Browse and create virtual accounts in the chaos machine registry."
        actions={<CreateVirtualAccountDialog />}
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-col">
          {/* Filter bar */}
          <div className="border-b border-border bg-muted/30 px-6 py-3 md:px-8">
            <div className="flex flex-wrap gap-2">
              <Input
                className="w-full md:max-w-xs"
                value={draft.search}
                onChange={e => setDraft(d => ({ ...d, search: e.target.value }))}
                placeholder="Search by name or ID"
                onKeyDown={e => e.key === "Enter" && applyFilters()}
              />
              <Select
                value={draft.ownershipType as typeof OWNERSHIP_OPTIONS[number]["value"]}
                onChange={v => setDraft(d => ({ ...d, ownershipType: v }))}
                options={OWNERSHIP_OPTIONS}
                className="w-36"
              />
              <Input
                className="w-24"
                value={draft.currency}
                onChange={e => setDraft(d => ({ ...d, currency: e.target.value.toUpperCase() }))}
                placeholder="Currency"
                maxLength={3}
              />
              <Select
                value={draft.status as typeof STATUS_OPTIONS[number]["value"]}
                onChange={v => setDraft(d => ({ ...d, status: v }))}
                options={STATUS_OPTIONS}
                className="w-36"
              />
              <Button variant="outline" size="sm" onClick={applyFilters}>
                Apply
              </Button>
              <Button variant="ghost" size="sm" onClick={clearFilters}>
                Clear
              </Button>
            </div>
          </div>

          {/* Table */}
          <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
            {query.isLoading ? (
              <TableContainer className="flex-1 border-y border-border bg-card">
                <Table>
                  <THead>
                    <TR>
                      <TH>Name</TH>
                      <TH>VA ID</TH>
                      <TH>Ownership</TH>
                      <TH>Currency</TH>
                      <TH>Status</TH>
                      <TH>Channel</TH>
                      <TH>Created</TH>
                    </TR>
                  </THead>
                  <TBody>
                    <TableLoadingRows columns={7} rows={6} />
                  </TBody>
                </Table>
              </TableContainer>
            ) : query.error ? (
              <StatePanel
                title="Failed to load virtual accounts"
                description={getErrorMessage(query.error)}
                tone="danger"
                icon="error"
                action={<Button onClick={() => void query.refetch()}>Retry</Button>}
              />
            ) : accounts.length === 0 ? (
              <StatePanel
                title="No virtual accounts found"
                description="No accounts match the current filter criteria."
                iconNode={<Wallet className="h-5 w-5" />}
              />
            ) : (
              <TableContainer className="flex-1 border-y border-border bg-card">
                <Table>
                  <THead>
                    <TR>
                      <TH>Name</TH>
                      <TH>VA ID</TH>
                      <TH>Ownership</TH>
                      <TH>Currency</TH>
                      <TH>Status</TH>
                      <TH>Channel</TH>
                      <TH>Created</TH>
                    </TR>
                  </THead>
                  <TBody>
                    {accounts.map(account => (
                      <TR
                        key={account.vaId}
                        role="button"
                        tabIndex={0}
                        className="cursor-pointer transition-colors hover:bg-muted/40 focus:bg-muted/40 focus:outline-none"
                        onClick={() => navigate(`/virtual-accounts/${account.vaId}`)}
                        onKeyDown={e => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            navigate(`/virtual-accounts/${account.vaId}`);
                          }
                        }}
                      >
                        <TD className="font-medium">{account.name}</TD>
                        <TD className="max-w-[12rem] truncate font-mono text-muted-foreground">
                          {account.vaId}
                        </TD>
                        <TD>
                          <Badge variant="secondary">
                            {formatEnumValue(account.ownershipType)}
                          </Badge>
                        </TD>
                        <TD>{account.currency}</TD>
                        <TD>
                          <Badge variant={getStatusBadgeVariant(account.status)}>
                            {formatEnumValue(account.status)}
                          </Badge>
                        </TD>
                        <TD className="text-muted-foreground">
                          {account.channel ? formatEnumValue(account.channel) : "—"}
                        </TD>
                        <TD>{formatDate(account.createdAt)}</TD>
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
              itemLabel="account"
              hasNextPage={hasNextPage}
              disabled={query.isFetching}
              onPrevious={() => setPage(p => Math.max(p - 1, 0))}
              onNext={() => setPage(p => p + 1)}
            />
          </div>
        </div>
      </PageContent>
    </Page>
  );
}
