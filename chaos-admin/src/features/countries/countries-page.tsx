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
import { AsyncSearchSelect } from "@/components/ui/async-search-select";
import { EnumBadge } from "@/components/ui/enum-badge";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { SortableTH } from "@/components/ui/sortable-header";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  createCountry,
  listCountries,
  listCurrencies,
  refreshCountries,
  updateCountry,
  type CountryResponse
} from "@/lib/api";
import { useListControls } from "@/lib/use-list-controls";
import { formatDate } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Globe, Pencil, Plus, RefreshCw, Search } from "lucide-react";
import { useState, type ReactNode } from "react";

const PER_PAGE = 20;

function currencyLabelOf(country?: CountryResponse): string {
  return country?.primaryCurrency
    ? `${country.primaryCurrency.code} — ${country.primaryCurrency.name}`
    : "None";
}

const STATUS_OPTIONS = [
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" }
] as const;
type StatusValue = (typeof STATUS_OPTIONS)[number]["value"];

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Create / Edit Country Dialog
// ---------------------------------------------------------------------------

function CountryFormDialog({
  country,
  trigger
}: {
  country?: CountryResponse;
  trigger: ReactNode;
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const isEdit = Boolean(country);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(country?.name ?? "");
  const [isoCode, setIsoCode] = useState(country?.isoCode ?? "");
  const [status, setStatus] = useState<StatusValue>((country?.status as StatusValue) ?? "ACTIVE");
  const [primaryCurrencyId, setPrimaryCurrencyId] = useState(country?.primaryCurrencyId ?? "");
  const [currencyLabel, setCurrencyLabel] = useState(currencyLabelOf(country));
  const [formError, setFormError] = useState<string | null>(null);

  function reset() {
    setName(country?.name ?? "");
    setIsoCode(country?.isoCode ?? "");
    setStatus((country?.status as StatusValue) ?? "ACTIVE");
    setPrimaryCurrencyId(country?.primaryCurrencyId ?? "");
    setCurrencyLabel(currencyLabelOf(country));
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: () => {
      const body = {
        name: name.trim(),
        isoCode: isoCode.trim().toUpperCase(),
        status,
        primaryCurrencyId: primaryCurrencyId || null
      };
      return isEdit ? updateCountry(token!, country!.countryId, body) : createCountry(token!, body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["countries"] });
      setOpen(false);
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function handleSave() {
    setFormError(null);
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    const iso = isoCode.trim();
    if (iso.length < 2 || iso.length > 3) {
      setFormError("ISO code must be 2 or 3 characters.");
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
          <DialogTitle>{isEdit ? `Edit Country — ${country!.name}` : "Create Country"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update this country's details."
              : "Add a new country to the organization onboarding reference data."}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Ghana" />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">ISO Code</label>
            <Input
              value={isoCode}
              onChange={(e) => setIsoCode(e.target.value.toUpperCase())}
              placeholder="GH or GHA"
              maxLength={3}
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Primary Currency</label>
            <AsyncSearchSelect
              value={primaryCurrencyId}
              selectedLabel={currencyLabel}
              enabled={open}
              queryKey={["currencies-select"]}
              fetchOptions={async (s) => {
                const res = await listCurrencies(token!, {
                  perPage: 50,
                  search: s || undefined,
                  sortBy: "code",
                  sortDir: "asc"
                });
                return [
                  { value: "", label: "None" },
                  ...res.items.map((c) => ({ value: c.currencyId, label: `${c.code} — ${c.name}` }))
                ];
              }}
              onChange={(v, opt) => {
                setPrimaryCurrencyId(v);
                setCurrencyLabel(opt?.label ?? "None");
              }}
              placeholder="Select currency…"
              searchPlaceholder="Search currencies…"
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

export function CountriesPage() {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const { page, setPage, search, setSearch, debouncedSearch, sort, toggleSort, sortBy, sortDir } =
    useListControls();
  const [refreshNotice, setRefreshNotice] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["countries", { page, search: debouncedSearch, sortBy, sortDir }],
    queryFn: () =>
      listCountries(token!, {
        page,
        perPage: PER_PAGE,
        search: debouncedSearch || undefined,
        sortBy,
        sortDir
      })
  });

  const refreshMutation = useMutation({
    mutationFn: () => refreshCountries(token!),
    onSuccess: (summary) => {
      void queryClient.invalidateQueries({ queryKey: ["countries"] });
      void queryClient.invalidateQueries({ queryKey: ["currencies"] });
      setRefreshNotice(
        summary.error
          ? `Refresh failed: ${summary.error}`
          : summary.skipped
            ? "Refresh skipped (seeding disabled)."
            : `Refreshed: ${summary.countriesUpserted} countries, ${summary.currenciesUpserted} currencies added.`
      );
    },
    onError: (err) => setRefreshNotice(getErrorMessage(err))
  });

  const countries = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Countries"
        description="Manage the countries available for organization onboarding."
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => refreshMutation.mutate()}
              disabled={refreshMutation.isPending}
            >
              <RefreshCw className="mr-1.5 h-4 w-4" />
              {refreshMutation.isPending ? "Refreshing…" : "Refresh"}
            </Button>
            <CountryFormDialog
              trigger={
                <Button>
                  <Plus className="mr-1.5 h-4 w-4" />
                  Create Country
                </Button>
              }
            />
          </div>
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
          {refreshNotice && (
            <InlineNotice
              description={refreshNotice}
              tone={refreshNotice.toLowerCase().includes("fail") ? "danger" : "default"}
            />
          )}
          <div className="relative w-full md:max-w-xs">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name or ISO code"
            />
          </div>
          {query.isLoading ? (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>ISO Code</TH>
                    <TH>Primary Currency</TH>
                    <TH>Status</TH>
                    <TH>Modified</TH>
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
              title="Failed to load countries"
              description={getErrorMessage(query.error)}
              tone="danger"
              icon="error"
              action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            />
          ) : countries.length === 0 ? (
            <StatePanel
              title="No countries yet"
              description="Create a country to make it available for organization onboarding."
              iconNode={<Globe className="h-5 w-5" />}
            />
          ) : (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <SortableTH label="Name" field="name" sort={sort} onSort={toggleSort} />
                    <SortableTH label="ISO Code" field="isoCode" sort={sort} onSort={toggleSort} />
                    <TH>Primary Currency</TH>
                    <SortableTH label="Status" field="status" sort={sort} onSort={toggleSort} />
                    <SortableTH
                      label="Modified"
                      field="modifiedDate"
                      sort={sort}
                      onSort={toggleSort}
                    />
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  {countries.map((country) => (
                    <TR key={country.countryId}>
                      <TD className="font-medium">{country.name}</TD>
                      <TD className="font-mono text-xs text-muted-foreground">{country.isoCode}</TD>
                      <TD className="text-muted-foreground">
                        {country.primaryCurrency ? country.primaryCurrency.code : "—"}
                      </TD>
                      <TD>
                        <EnumBadge value={country.status} />
                      </TD>
                      <TD>{formatDate(country.modifiedDate)}</TD>
                      <TD className="text-right">
                        <CountryFormDialog
                          country={country}
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
            itemLabel="country"
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
