import { ListPagination } from "@/components/layout/list-pagination";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
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
import { EnumBadge } from "@/components/ui/enum-badge";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { SortableTH } from "@/components/ui/sortable-header";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { createCurrency, listCurrencies, updateCurrency, type CurrencyResponse } from "@/lib/api";
import { useListControls } from "@/lib/use-list-controls";
import { formatDate } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Coins, Pencil, Plus, Search } from "lucide-react";
import { useState, type ReactNode } from "react";

const PER_PAGE = 20;

const STATUS_OPTIONS = [
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" }
] as const;
type StatusValue = (typeof STATUS_OPTIONS)[number]["value"];

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Create / Edit Currency Dialog
// ---------------------------------------------------------------------------

function CurrencyFormDialog({
  currency,
  trigger
}: {
  currency?: CurrencyResponse;
  trigger: ReactNode;
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const isEdit = Boolean(currency);
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState(currency?.code ?? "");
  const [name, setName] = useState(currency?.name ?? "");
  const [symbol, setSymbol] = useState(currency?.symbol ?? "");
  const [status, setStatus] = useState<StatusValue>((currency?.status as StatusValue) ?? "ACTIVE");
  const [formError, setFormError] = useState<string | null>(null);

  function reset() {
    setCode(currency?.code ?? "");
    setName(currency?.name ?? "");
    setSymbol(currency?.symbol ?? "");
    setStatus((currency?.status as StatusValue) ?? "ACTIVE");
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: () => {
      const body = {
        code: code.trim().toUpperCase(),
        name: name.trim(),
        symbol: symbol.trim() || undefined,
        status
      };
      return isEdit
        ? updateCurrency(token!, currency!.currencyId, body)
        : createCurrency(token!, body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["currencies"] });
      setOpen(false);
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function handleSave() {
    setFormError(null);
    const trimmedCode = code.trim();
    if (trimmedCode.length !== 3) {
      setFormError("Code must be a 3-letter ISO-4217 code.");
      return;
    }
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) reset();
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? `Edit Currency — ${currency!.code}` : "Create Currency"}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update this currency's details."
              : "Add a currency for countries and organization onboarding."}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Code (ISO-4217)</label>
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="GHS"
                maxLength={3}
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Symbol (optional)</label>
              <Input value={symbol} onChange={(e) => setSymbol(e.target.value)} placeholder="₵" />
            </div>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Ghanaian cedi"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Status</label>
            <Select value={status} onChange={setStatus} options={STATUS_OPTIONS} />
          </div>
          {formError && <InlineNotice description={formError} tone="danger" />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : isEdit ? "Save" : "Create"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function CurrenciesPage() {
  const { token } = useSession();
  const { page, setPage, search, setSearch, debouncedSearch, sort, toggleSort, sortBy, sortDir } =
    useListControls();

  const query = useQuery({
    queryKey: ["currencies", { page, search: debouncedSearch, sortBy, sortDir }],
    queryFn: () =>
      listCurrencies(token!, {
        page,
        perPage: PER_PAGE,
        search: debouncedSearch || undefined,
        sortBy,
        sortDir
      })
  });

  const currencies = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Currencies"
        description="Manage currencies seeded from restcountries.com and used in onboarding."
        actions={
          <CurrencyFormDialog
            trigger={
              <Button>
                <Plus className="mr-1.5 h-4 w-4" />
                Create Currency
              </Button>
            }
          />
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
          <div className="relative w-full md:max-w-xs">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by code or name"
            />
          </div>
          {query.isLoading ? (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Code</TH>
                    <TH>Name</TH>
                    <TH>Symbol</TH>
                    <TH>Status</TH>
                    <TH>Updated</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  <TableLoadingRows columns={6} rows={6} />
                </TBody>
              </Table>
            </TableContainer>
          ) : query.error ? (
            <StatePanel
              title="Failed to load currencies"
              description={getErrorMessage(query.error)}
              tone="danger"
              icon="error"
              action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            />
          ) : currencies.length === 0 ? (
            <StatePanel
              title="No currencies yet"
              description="Create a currency or refresh reference data from the Countries page."
              iconNode={<Coins className="h-5 w-5" />}
            />
          ) : (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <SortableTH label="Code" field="code" sort={sort} onSort={toggleSort} />
                    <SortableTH label="Name" field="name" sort={sort} onSort={toggleSort} />
                    <TH>Symbol</TH>
                    <SortableTH label="Status" field="status" sort={sort} onSort={toggleSort} />
                    <SortableTH
                      label="Updated"
                      field="updatedAt"
                      sort={sort}
                      onSort={toggleSort}
                    />
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  {currencies.map((currency) => (
                    <TR key={currency.currencyId}>
                      <TD className="font-mono text-xs font-medium">{currency.code}</TD>
                      <TD>{currency.name}</TD>
                      <TD className="text-muted-foreground">{currency.symbol ?? "—"}</TD>
                      <TD>
                        <EnumBadge value={currency.status} />
                      </TD>
                      <TD>{formatDate(currency.updatedAt)}</TD>
                      <TD className="text-right">
                        <CurrencyFormDialog
                          currency={currency}
                          trigger={
                            <Button variant="outline" size="sm">
                              <Pencil className="mr-1 h-3 w-3" />
                              Edit
                            </Button>
                          }
                        />
                      </TD>
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
            itemLabel="currency"
            hasNextPage={hasNextPage}
            disabled={query.isFetching}
            onPrevious={() => setPage((p) => Math.max(p - 1, 0))}
            onNext={() => setPage((p) => p + 1)}
          />
        </div>
      </PageContent>
    </Page>
  );
}
