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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import {
  ApiError,
  listChartOfAccounts,
  listFlowConfigs,
  listVirtualAccounts,
  updateFlowConfig,
  updateRole,
  type ChartOfAccountsRoleResponse,
  type FlowConfigResponse,
  type SlotUpdate
} from "@/lib/api";
import { formatEnumValue } from "@/lib/utils";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, RefreshCw } from "lucide-react";
import { useState } from "react";

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

// ---------------------------------------------------------------------------
// Edit Role Dialog
// ---------------------------------------------------------------------------

function EditRoleDialog({
  role,
  vaOptions
}: {
  role: ChartOfAccountsRoleResponse;
  vaOptions: { value: string; label: string }[];
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [defaultVaId, setDefaultVaId] = useState(role.defaultVaId ?? "");
  const [currency, setCurrency] = useState(role.currency ?? "GHS");
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => updateRole(token!, role.role, { defaultVaId, currency }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["chart-of-accounts"] });
      setOpen(false);
      setFormError(null);
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function handleSave() {
    setFormError(null);
    if (!defaultVaId.trim()) {
      setFormError("Default VA ID is required.");
      return;
    }
    if (!currency.trim()) {
      setFormError("Currency is required.");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          Edit
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Edit Role — {role.role}</DialogTitle>
          <DialogDescription>
            Update the default virtual account and currency for this chart-of-accounts role.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Default VA ID</label>
            {vaOptions.length > 0 ? (
              <Select
                value={defaultVaId as string & { __brand: "select" }}
                onChange={(v) => setDefaultVaId(v)}
                options={vaOptions as { value: string & { __brand: "select" }; label: string }[]}
                placeholder="Select VA…"
                searchable
                searchPlaceholder="Search VAs…"
              />
            ) : (
              <Input
                value={defaultVaId}
                onChange={e => setDefaultVaId(e.target.value)}
                placeholder="Enter VA ID"
              />
            )}
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
          {formError && <InlineNotice description={formError} tone="danger" />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Roles Tab
// ---------------------------------------------------------------------------

function RolesTab({
  vaOptions
}: {
  vaOptions: { value: string; label: string }[];
}) {
  const { token } = useSession();
  const query = useQuery({
    queryKey: ["chart-of-accounts"],
    queryFn: () => listChartOfAccounts(token!)
  });

  const roles = query.data ?? [];

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
      {query.isLoading ? (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Role</TH>
                <TH>Code</TH>
                <TH>Category</TH>
                <TH>Currency</TH>
                <TH>Channel</TH>
                <TH>VA ID</TH>
                <TH>Status</TH>
                <TH className="text-right">Actions</TH>
              </TR>
            </THead>
            <TBody>
              <TableLoadingRows columns={8} rows={6} />
            </TBody>
          </Table>
        </TableContainer>
      ) : query.error ? (
        <StatePanel
          title="Failed to load chart of accounts"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : roles.length === 0 ? (
        <StatePanel
          title="No roles configured"
          description="Chart of accounts roles will appear here after backend provisioning."
          iconNode={<BookOpen className="h-5 w-5" />}
        />
      ) : (
        <TableContainer className="border-y border-border bg-card">
          <Table>
            <THead>
              <TR>
                <TH>Role</TH>
                <TH>Code</TH>
                <TH>Category</TH>
                <TH>Currency</TH>
                <TH>Channel</TH>
                <TH>VA ID</TH>
                <TH>Status</TH>
                <TH className="text-right">Actions</TH>
              </TR>
            </THead>
            <TBody>
              {roles.map(role => (
                <TR key={role.role}>
                  <TD className="font-mono text-xs font-medium">{role.role}</TD>
                  <TD className="font-mono text-xs text-muted-foreground">{role.accountCode}</TD>
                  <TD>
                    <Badge variant="secondary">{role.category}</Badge>
                  </TD>
                  <TD>{role.currency}</TD>
                  <TD>{role.channel ? formatEnumValue(role.channel) : "—"}</TD>
                  <TD className="max-w-[14rem] truncate font-mono text-xs text-muted-foreground">
                    {role.vaId ?? role.defaultVaId ?? "—"}
                  </TD>
                  <TD>
                    <Badge
                      variant={
                        role.provisioningStatus === "PROVISIONED"
                          ? "success"
                          : role.provisioningStatus === "FAILED"
                            ? "destructive"
                            : "warning"
                      }
                    >
                      {formatEnumValue(role.provisioningStatus)}
                    </Badge>
                  </TD>
                  <TD className="text-right">
                    <EditRoleDialog role={role} vaOptions={vaOptions} />
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Flow Config Slot Editor
// ---------------------------------------------------------------------------

function FlowSlotEditor({
  config,
  vaOptions
}: {
  config: FlowConfigResponse;
  vaOptions: { value: string; label: string }[];
}) {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [slots, setSlots] = useState<SlotUpdate[]>(
    config.slots.map(s => ({
      slotName: s.slotName,
      accountRole: s.accountRole ?? null,
      explicitVaId: s.explicitVaId ?? null
    }))
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const mutation = useMutation({
    mutationFn: () => updateFlowConfig(token!, config.flowType, { slots }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["flow-configs"] });
      setSaveSuccess(true);
      setFormError(null);
      setTimeout(() => setSaveSuccess(false), 3000);
    },
    onError: (err) => setFormError(getErrorMessage(err))
  });

  function updateSlot(index: number, field: keyof SlotUpdate, value: string | null) {
    setSlots(prev =>
      prev.map((s, i) => (i === index ? { ...s, [field]: value || null } : s))
    );
    setSaveSuccess(false);
  }

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold">{formatEnumValue(config.flowType)}</p>
          <p className="text-[10px] text-muted-foreground font-mono">{config.flowType}</p>
        </div>
        <Button
          size="sm"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
        >
          {mutation.isPending ? (
            <>
              <RefreshCw className="mr-1.5 h-3 w-3 animate-spin" />
              Saving…
            </>
          ) : saveSuccess ? (
            "Saved ✓"
          ) : (
            "Save"
          )}
        </Button>
      </div>

      {config.slots.length === 0 ? (
        <p className="text-xs text-muted-foreground">No configurable slots for this flow.</p>
      ) : (
        <div className="space-y-3">
          {slots.map((slot, index) => (
            <div key={slot.slotName} className="grid grid-cols-3 items-center gap-3">
              <span className="text-xs font-medium">{slot.slotName}</span>
              <div>
                <p className="mb-1 text-[10px] text-muted-foreground">Account Role</p>
                <Input
                  value={slot.accountRole ?? ""}
                  onChange={e => updateSlot(index, "accountRole", e.target.value)}
                  placeholder="e.g. PLATFORM_FLOAT"
                  className="text-xs"
                />
              </div>
              <div>
                <p className="mb-1 text-[10px] text-muted-foreground">Explicit VA ID</p>
                {vaOptions.length > 0 ? (
                  <Select
                    value={(slot.explicitVaId ?? "") as string & { __brand: "select" }}
                    onChange={v => updateSlot(index, "explicitVaId", v)}
                    options={[
                      { value: "" as string & { __brand: "select" }, label: "Use role default" },
                      ...(vaOptions as { value: string & { __brand: "select" }; label: string }[])
                    ]}
                    placeholder="Use role default"
                  />
                ) : (
                  <Input
                    value={slot.explicitVaId ?? ""}
                    onChange={e => updateSlot(index, "explicitVaId", e.target.value)}
                    placeholder="Leave blank to use role"
                    className="text-xs"
                  />
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {formError && <InlineNotice description={formError} tone="danger" className="mt-3" />}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Flow Configs Tab
// ---------------------------------------------------------------------------

function FlowConfigsTab({
  vaOptions
}: {
  vaOptions: { value: string; label: string }[];
}) {
  const { token } = useSession();
  const query = useQuery({
    queryKey: ["flow-configs"],
    queryFn: () => listFlowConfigs(token!)
  });

  const configs = query.data ?? [];

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 py-4 md:px-8">
      {query.isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div
              key={i}
              className="h-24 animate-pulse rounded-lg border border-border bg-muted/40"
            />
          ))}
        </div>
      ) : query.error ? (
        <StatePanel
          title="Failed to load flow configs"
          description={getErrorMessage(query.error)}
          tone="danger"
          icon="error"
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
        />
      ) : configs.length === 0 ? (
        <StatePanel
          title="No flow configurations"
          description="Flow configurations will appear here once flows are registered."
          iconNode={<BookOpen className="h-5 w-5" />}
        />
      ) : (
        <div className="space-y-3">
          {configs.map(config => (
            <FlowSlotEditor key={config.flowType} config={config} vaOptions={vaOptions} />
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

const PER_PAGE = 200; // load all VAs for selects

export function ChartOfAccountsPage() {
  const { token } = useSession();

  // Load VAs for select options in dialogs
  const vasQuery = useQuery({
    queryKey: ["virtual-accounts", { perPage: PER_PAGE }],
    queryFn: () => listVirtualAccounts(token!, { perPage: PER_PAGE })
  });

  const vaOptions = (vasQuery.data?.items ?? []).map(va => ({
    value: va.vaId,
    label: va.name ? `${va.name} (${va.vaId})` : va.vaId
  }));

  return (
    <Page>
      <PageHeader
        title="Chart of Accounts"
        description="Configure account roles and flow slot assignments for the chaos machine."
      />
      <PageContent className="min-h-full grid-rows-[minmax(0,1fr)] px-0 py-0 md:px-0 md:py-0">
        <Tabs defaultValue="roles" className="flex min-h-0 flex-1 flex-col">
          <TabsList>
            <TabsTrigger value="roles">Roles</TabsTrigger>
            <TabsTrigger value="flow-configs">Flow Slots</TabsTrigger>
          </TabsList>
          <TabsContent value="roles" className="flex-1">
            <RolesTab vaOptions={vaOptions} />
          </TabsContent>
          <TabsContent value="flow-configs" className="flex-1">
            <FlowConfigsTab vaOptions={vaOptions} />
          </TabsContent>
        </Tabs>
      </PageContent>
    </Page>
  );
}
