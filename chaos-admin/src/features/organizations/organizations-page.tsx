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
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  listCountries,
  listOrganizations,
  listOrganizationTypes,
  onboardOrganization,
  type OrganizationResponse
} from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Building2, CheckCircle2, Plus, Trash2, X } from "lucide-react";
import { useState } from "react";

const PER_PAGE = 20;
const SELECT_PAGE = 200; // load reference data for selects

const STATUS_OPTIONS = [
  { value: "ACTIVE", label: "Active" },
  { value: "SUSPENDED", label: "Suspended" },
  { value: "DORMANT", label: "Dormant" },
  { value: "CLOSED", label: "Closed" }
] as const;
type StatusValue = (typeof STATUS_OPTIONS)[number]["value"];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

type OnboardSuccess = { name: string; eventId: string | null };

// ---------------------------------------------------------------------------
// Onboard Organization Dialog
// ---------------------------------------------------------------------------

function OnboardOrganizationDialog({ onSuccess }: { onSuccess: (result: OnboardSuccess) => void }) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [organizationTypeId, setOrganizationTypeId] = useState("");
  const [countryId, setCountryId] = useState("");
  const [primaryContactEmail, setPrimaryContactEmail] = useState("");
  const [phoneNumbers, setPhoneNumbers] = useState<string[]>([""]);
  const [status, setStatus] = useState<StatusValue>("ACTIVE");
  const [formError, setFormError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["organization-types", { perPage: SELECT_PAGE }],
    queryFn: () => listOrganizationTypes(token!, { perPage: SELECT_PAGE }),
    enabled: open
  });
  const countriesQuery = useQuery({
    queryKey: ["countries", { perPage: SELECT_PAGE }],
    queryFn: () => listCountries(token!, { perPage: SELECT_PAGE }),
    enabled: open
  });

  const typeOptions = (typesQuery.data?.items ?? []).map((t) => ({
    value: t.organizationTypeId,
    label: t.name
  }));
  const countryOptions = (countriesQuery.data?.items ?? []).map((c) => ({
    value: c.countryId,
    label: c.isoCode ? `${c.name} (${c.isoCode})` : c.name
  }));

  function reset() {
    setName("");
    setOrganizationTypeId("");
    setCountryId("");
    setPrimaryContactEmail("");
    setPhoneNumbers([""]);
    setStatus("ACTIVE");
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: () =>
      onboardOrganization(token!, {
        name: name.trim(),
        organizationTypeId,
        countryId,
        primaryContactEmail: primaryContactEmail.trim() || undefined,
        phoneNumbers: phoneNumbers.map((p) => p.trim()).filter(Boolean),
        status
      }),
    onSuccess: (org) => {
      void queryClient.invalidateQueries({ queryKey: ["organizations"] });
      setOpen(false);
      reset();
      onSuccess({ name: org.name, eventId: org.eventId });
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function updatePhone(index: number, value: string) {
    setPhoneNumbers((prev) => prev.map((p, i) => (i === index ? value : p)));
  }
  function addPhone() {
    setPhoneNumbers((prev) => [...prev, ""]);
  }
  function removePhone(index: number) {
    setPhoneNumbers((prev) => (prev.length === 1 ? [""] : prev.filter((_, i) => i !== index)));
  }

  function handleSubmit() {
    setFormError(null);
    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }
    if (!organizationTypeId) {
      setFormError("Organization type is required.");
      return;
    }
    if (!countryId) {
      setFormError("Country is required.");
      return;
    }
    if (primaryContactEmail.trim() && !EMAIL_PATTERN.test(primaryContactEmail.trim())) {
      setFormError("Primary contact email must be a valid email address.");
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
          Onboard Organization
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Onboard Organization</DialogTitle>
          <DialogDescription>
            Create an organization and publish an <span className="font-mono">organization.onboarded</span>{" "}
            event via the transactional outbox.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Acme Limited" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Organization Type</label>
              <Select
                value={organizationTypeId}
                onChange={setOrganizationTypeId}
                options={typeOptions}
                placeholder={typesQuery.isLoading ? "Loading…" : "Select type…"}
                searchable
                searchPlaceholder="Search types…"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Country</label>
              <Select
                value={countryId}
                onChange={setCountryId}
                options={countryOptions}
                placeholder={countriesQuery.isLoading ? "Loading…" : "Select country…"}
                searchable
                searchPlaceholder="Search countries…"
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Primary Contact Email (optional)</label>
            <Input
              value={primaryContactEmail}
              onChange={(e) => setPrimaryContactEmail(e.target.value)}
              placeholder="ops@acme.example"
              type="email"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Phone Numbers</label>
            <div className="space-y-2">
              {phoneNumbers.map((phone, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Input
                    value={phone}
                    onChange={(e) => updatePhone(index, e.target.value)}
                    placeholder="+233201234567"
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-9 w-9 shrink-0 p-0"
                    onClick={() => removePhone(index)}
                    aria-label="Remove phone number"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              <Button variant="outline" size="sm" onClick={addPhone}>
                <Plus className="mr-1 h-3 w-3" />
                Add phone
              </Button>
            </div>
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
          <Button onClick={handleSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? "Onboarding…" : "Onboard"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function OrganizationsPage() {
  const { token } = useSession();
  const [page, setPage] = useState(0);
  const [lastOnboard, setLastOnboard] = useState<OnboardSuccess | null>(null);

  const query = useQuery({
    queryKey: ["organizations", { page }],
    queryFn: () => listOrganizations(token!, { page, perPage: PER_PAGE })
  });

  const organizations = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Organizations"
        description="Onboard organizations and emit organization.onboarded events to the ledger."
        actions={<OnboardOrganizationDialog onSuccess={setLastOnboard} />}
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
          {lastOnboard && (
            <div className="flex items-start gap-2.5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-700">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
              <div className="flex-1">
                <p className="font-medium">Onboarded {lastOnboard.name}</p>
                <p className="text-emerald-700/80">
                  {lastOnboard.eventId
                    ? `organization.onboarded event ${lastOnboard.eventId} enqueued for publishing.`
                    : "organization.onboarded event enqueued for publishing."}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setLastOnboard(null)}
                className="rounded-md p-0.5 text-emerald-700/70 transition-colors hover:text-emerald-700"
                aria-label="Dismiss"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          {query.isLoading ? (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>Type</TH>
                    <TH>Country</TH>
                    <TH>Status</TH>
                    <TH>Created</TH>
                  </TR>
                </THead>
                <TBody>
                  <TableLoadingRows columns={5} rows={6} />
                </TBody>
              </Table>
            </TableContainer>
          ) : query.error ? (
            <StatePanel
              title="Failed to load organizations"
              description={getErrorMessage(query.error)}
              tone="danger"
              icon="error"
              action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            />
          ) : organizations.length === 0 ? (
            <StatePanel
              title="No organizations yet"
              description="Onboard an organization to publish its organization.onboarded event."
              iconNode={<Building2 className="h-5 w-5" />}
            />
          ) : (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>Type</TH>
                    <TH>Country</TH>
                    <TH>Status</TH>
                    <TH>Created</TH>
                  </TR>
                </THead>
                <TBody>
                  {organizations.map((org) => (
                    <TR key={org.organizationId}>
                      <TD className="font-medium">{org.name}</TD>
                      <TD className="text-muted-foreground">{org.typeName ?? "—"}</TD>
                      <TD className="text-muted-foreground">
                        {org.countryName
                          ? org.countryIsoCode
                            ? `${org.countryName} (${org.countryIsoCode})`
                            : org.countryName
                          : "—"}
                      </TD>
                      <TD>
                        <EnumBadge value={org.status} />
                      </TD>
                      <TD>{formatDate(org.createdAt)}</TD>
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
            itemLabel="organization"
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
