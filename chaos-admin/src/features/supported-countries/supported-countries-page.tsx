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
import { Input } from "@/components/ui/input";
import { SortableTH } from "@/components/ui/sortable-header";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  createSupportedCountry,
  deleteSupportedCountry,
  listCountries,
  listSupportedCountries,
  type SupportedCountryResponse
} from "@/lib/api";
import { useListControls } from "@/lib/use-list-controls";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Globe2, Plus, Search, Trash2 } from "lucide-react";
import { useState } from "react";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Add Supported Country Dialog
// ---------------------------------------------------------------------------

function AddSupportedCountryDialog() {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [countryId, setCountryId] = useState("");
  const [countryLabel, setCountryLabel] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  function reset() {
    setCountryId("");
    setCountryLabel("");
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: () => createSupportedCountry(token!, { countryId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["supported-countries"] });
      setOpen(false);
      reset();
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function handleSave() {
    setFormError(null);
    if (!countryId) {
      setFormError("Select a country.");
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
      <DialogTrigger asChild>
        <Button>
          <Plus className="mr-1.5 h-4 w-4" />
          Add Supported Country
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add Supported Country</DialogTitle>
          <DialogDescription>
            Pick a country to make it available on the organization onboarding form.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Country</label>
            <AsyncSearchSelect
              value={countryId}
              selectedLabel={countryLabel}
              enabled={open}
              queryKey={["countries-select"]}
              fetchOptions={async (s) => {
                const res = await listCountries(token!, {
                  perPage: 50,
                  search: s || undefined,
                  sortBy: "name",
                  sortDir: "asc"
                });
                return res.items.map((c) => ({
                  value: c.countryId,
                  label: c.isoCode ? `${c.name} (${c.isoCode})` : c.name
                }));
              }}
              onChange={(v, opt) => {
                setCountryId(v);
                setCountryLabel(opt?.label ?? "");
              }}
              placeholder="Select country…"
              searchPlaceholder="Search countries…"
            />
          </div>
          {formError && <InlineNotice description={formError} tone="danger" />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={mutation.isPending}>
            {mutation.isPending ? "Adding…" : "Add"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Remove confirmation
// ---------------------------------------------------------------------------

function RemoveSupportedCountryDialog({ supported }: { supported: SupportedCountryResponse }) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => deleteSupportedCountry(token!, supported.supportedCountryId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["supported-countries"] });
      setOpen(false);
    },
    onError: (err) => setError(getErrorMessage(err))
  });

  const label = supported.country?.name ?? supported.countryId;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setError(null);
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Trash2 className="mr-1 h-3 w-3" />
          Remove
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Remove {label}?</DialogTitle>
          <DialogDescription>
            This removes {label} from the onboarding form. The country itself is not deleted.
          </DialogDescription>
        </DialogHeader>
        {error && <InlineNotice description={error} tone="danger" />}
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            {mutation.isPending ? "Removing…" : "Remove"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function SupportedCountriesPage() {
  const { token } = useSession();
  const { page, setPage, search, setSearch, debouncedSearch, sort, toggleSort, sortBy, sortDir } =
    useListControls();

  const query = useQuery({
    queryKey: ["supported-countries", { page, search: debouncedSearch, sortBy, sortDir }],
    queryFn: () =>
      listSupportedCountries(token!, {
        page,
        perPage: PER_PAGE,
        search: debouncedSearch || undefined,
        sortBy,
        sortDir
      })
  });

  const supported = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Supported Countries"
        description="Curate the countries available on the organization onboarding form."
        actions={<AddSupportedCountryDialog />}
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
          <div className="relative w-full md:max-w-xs">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by country, ISO, or currency"
            />
          </div>
          {query.isLoading ? (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Country</TH>
                    <TH>ISO Code</TH>
                    <TH>Primary Currency</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  <TableLoadingRows columns={4} rows={6} />
                </TBody>
              </Table>
            </TableContainer>
          ) : query.error ? (
            <StatePanel
              title="Failed to load supported countries"
              description={getErrorMessage(query.error)}
              tone="danger"
              icon="error"
              action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            />
          ) : supported.length === 0 ? (
            <StatePanel
              title="No supported countries yet"
              description="Add a supported country so operators can onboard organizations into it."
              iconNode={<Globe2 className="h-5 w-5" />}
            />
          ) : (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <SortableTH label="Country" field="country" sort={sort} onSort={toggleSort} />
                    <SortableTH label="ISO Code" field="isoCode" sort={sort} onSort={toggleSort} />
                    <TH>Primary Currency</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  {supported.map((row) => (
                    <TR key={row.supportedCountryId}>
                      <TD className="font-medium">{row.country?.name ?? row.countryId}</TD>
                      <TD className="font-mono text-xs text-muted-foreground">
                        {row.country?.isoCode ?? "—"}
                      </TD>
                      <TD className="text-muted-foreground">
                        {row.country?.primaryCurrency
                          ? `${row.country.primaryCurrency.code} — ${row.country.primaryCurrency.name}`
                          : "—"}
                      </TD>
                      <TD className="text-right">
                        <RemoveSupportedCountryDialog supported={row} />
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
            itemLabel="supported country"
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
