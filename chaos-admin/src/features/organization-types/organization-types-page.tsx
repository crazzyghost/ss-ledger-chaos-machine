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
import { Input } from "@/components/ui/input";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import {
  createOrganizationType,
  listOrganizationTypes,
  updateOrganizationType,
  type OrganizationTypeResponse
} from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Tags } from "lucide-react";
import { useState, type ReactNode } from "react";

const PER_PAGE = 20;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Create / Edit Organization Type Dialog
// ---------------------------------------------------------------------------

function OrganizationTypeFormDialog({
  organizationType,
  trigger
}: {
  organizationType?: OrganizationTypeResponse;
  trigger: ReactNode;
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const isEdit = Boolean(organizationType);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(organizationType?.name ?? "");
  const [formError, setFormError] = useState<string | null>(null);

  function reset() {
    setName(organizationType?.name ?? "");
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: () => {
      const body = { name: name.trim() };
      return isEdit
        ? updateOrganizationType(token!, organizationType!.organizationTypeId, body)
        : createOrganizationType(token!, body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["organization-types"] });
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
            {isEdit ? `Edit Organization Type — ${organizationType!.name}` : "Create Organization Type"}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Rename this organization type."
              : "Add a new organization type for onboarding (e.g. BUSINESS, MERCHANT)."}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Name</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. BUSINESS"
              onKeyDown={(e) => e.key === "Enter" && handleSave()}
            />
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

export function OrganizationTypesPage() {
  const { token } = useSession();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["organization-types", { page }],
    queryFn: () => listOrganizationTypes(token!, { page, perPage: PER_PAGE })
  });

  const types = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const hasNextPage = (page + 1) * PER_PAGE < total;

  return (
    <Page>
      <PageHeader
        title="Organization Types"
        description="Manage the organization types available for onboarding."
        actions={
          <OrganizationTypeFormDialog
            trigger={
              <Button>
                <Plus className="mr-1.5 h-4 w-4" />
                Create Organization Type
              </Button>
            }
          />
        }
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
          {query.isLoading ? (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>Created</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  <TableLoadingRows columns={3} rows={6} />
                </TBody>
              </Table>
            </TableContainer>
          ) : query.error ? (
            <StatePanel
              title="Failed to load organization types"
              description={getErrorMessage(query.error)}
              tone="danger"
              icon="error"
              action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            />
          ) : types.length === 0 ? (
            <StatePanel
              title="No organization types yet"
              description="Create an organization type to make it available for onboarding."
              iconNode={<Tags className="h-5 w-5" />}
            />
          ) : (
            <TableContainer className="flex-1 border-y border-border bg-card">
              <Table>
                <THead>
                  <TR>
                    <TH>Name</TH>
                    <TH>Created</TH>
                    <TH className="text-right">Actions</TH>
                  </TR>
                </THead>
                <TBody>
                  {types.map((type) => (
                    <TR key={type.organizationTypeId}>
                      <TD className="font-medium">{type.name}</TD>
                      <TD>{formatDate(type.createdAt)}</TD>
                      <TD className="text-right">
                        <OrganizationTypeFormDialog
                          organizationType={type}
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
            itemLabel="organization type"
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
