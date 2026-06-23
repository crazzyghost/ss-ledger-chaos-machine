import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { InlineNotice, StatePanel, TableLoadingRows } from "@/components/layout/state-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Table, TableContainer, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useSession } from "@/features/auth/session-provider";
import { getTrialBalance, type TrialBalanceResponse } from "@/lib/api";
import { formatEnumValue, formatMoney } from "@/lib/utils";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Scale } from "lucide-react";
import { useMemo, useState } from "react";

// A small static currency list — enough for an operator tool without coupling this page to the
// currencies feature query. "All currencies" omits the param (the report then aggregates).
const CURRENCY_OPTIONS = [
  { value: "", label: "All currencies" },
  { value: "GHS", label: "GHS — Ghanaian Cedi" },
  { value: "USD", label: "USD — US Dollar" },
  { value: "EUR", label: "EUR — Euro" },
  { value: "GBP", label: "GBP — British Pound" },
  { value: "NGN", label: "NGN — Nigerian Naira" },
  { value: "KES", label: "KES — Kenyan Shilling" },
  { value: "ZAR", label: "ZAR — South African Rand" }
] as const;

const MS_PER_DAY = 86_400_000;
const MAX_SPAN_DAYS = 366;

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

// First day (inclusive) and first-day-of-next-month (exclusive) for a "YYYY-MM" key — the ledger
// treats `to` as exclusive, so a single month spans [first, firstOfNextMonth).
function monthRange(ym: string): { from: string; to: string } {
  const [y, m] = ym.split("-").map(Number);
  const nextY = m === 12 ? y + 1 : y;
  const nextM = m === 12 ? 1 : m + 1;
  return { from: `${y}-${pad2(m)}-01`, to: `${nextY}-${pad2(nextM)}-01` };
}

// The current month plus the previous 11, newest first — the quick-picker's options.
function buildMonthOptions(now: Date): { value: string; label: string }[] {
  const opts: { value: string; label: string }[] = [];
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    opts.push({
      value: `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`,
      label: d.toLocaleString("en-GH", { month: "long", year: "numeric" })
    });
  }
  return opts;
}

// Client-side guard mirroring the ledger's own period rules so the obvious bad cases never
// round-trip. Returns an error message, or null when the period is acceptable.
function periodError(from: string, to: string): string | null {
  if (!from || !to) return "Select both a From and To date.";
  if (from >= to) return "The From date must be before the To date.";
  const days = (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / MS_PER_DAY;
  if (days > MAX_SPAN_DAYS) return `The selected period must not exceed ${MAX_SPAN_DAYS} days.`;
  return null;
}

// Renders the echoed period as a human label: a single calendar month ("June 2026") when the range
// is exactly one month, otherwise an inclusive date range (the exclusive `to` is shown as `to-1`).
function formatPeriod(fromIso: string, toIso: string): string {
  const from = fromIso.slice(0, 10);
  const to = toIso.slice(0, 10);
  if (!from || !to) return "—";
  const [fy, fm, fd] = from.split("-").map(Number);
  const [ty, tm, td] = to.split("-").map(Number);
  const wholeMonth =
    fd === 1 &&
    td === 1 &&
    ((ty === fy && tm === fm + 1) || (ty === fy + 1 && fm === 12 && tm === 1));
  if (wholeMonth) {
    return new Date(Date.UTC(fy, fm - 1, 1)).toLocaleString("en-GH", {
      month: "long",
      year: "numeric",
      timeZone: "UTC"
    });
  }
  const start = new Date(Date.UTC(fy, fm - 1, fd));
  const endInclusive = new Date(Date.UTC(ty, tm - 1, td) - MS_PER_DAY);
  const fmt = (d: Date) => d.toLocaleDateString("en-GH", { dateStyle: "medium", timeZone: "UTC" });
  return `${fmt(start)} – ${fmt(endInclusive)}`;
}

// Aggregate totals can span multiple currencies (when no currency scope is applied), so format with
// the currency code only when the report is scoped; otherwise show a plain 2-dp number.
function formatTotal(value: string, currency: string | null): string {
  if (currency) return formatMoney(value, currency);
  const n = Number(value);
  return new Intl.NumberFormat("en-GH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number.isNaN(n) ? 0 : n);
}

function netOf(report: TrialBalanceResponse): string {
  return String((Number(report.totalDebits) || 0) - (Number(report.totalCredits) || 0));
}

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

function isLedgerProxyUnavailable(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const status = (error as { status?: number }).status;
  return (
    status === 503 ||
    (status === 500 && error.message.toLowerCase().includes("temporarily unavailable"))
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-border bg-card px-4 py-3">
      <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-semibold tabular-nums text-foreground">{value}</p>
      {hint ? <p className="mt-0.5 text-[10px] text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

const TABLE_COLUMNS = (
  <TR>
    <TH>Account Code</TH>
    <TH>Account Name</TH>
    <TH>Ownership</TH>
    <TH>Currency</TH>
    <TH className="text-right">Debits</TH>
    <TH className="text-right">Credits</TH>
    <TH className="text-right">Net Movement</TH>
  </TR>
);

export function TrialBalancePage() {
  const { token } = useSession();

  const now = useMemo(() => new Date(), []);
  const monthOptions = useMemo(() => buildMonthOptions(now), [now]);
  const defaultMonth = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`;
  const defaultRange = useMemo(() => monthRange(defaultMonth), [defaultMonth]);

  // Draft filter state (the From/To inputs are the source of truth; the picker is a convenience).
  const [selectedMonth, setSelectedMonth] = useState<string>(defaultMonth);
  const [from, setFrom] = useState<string>(defaultRange.from);
  const [to, setTo] = useState<string>(defaultRange.to);
  const [currency, setCurrency] = useState<string>("");
  const [guardError, setGuardError] = useState<string | null>(null);

  // Applied filters — what the query is keyed by. Defaults to the current month so the report
  // loads on mount.
  const [applied, setApplied] = useState<{ from: string; to: string; currency: string }>({
    ...defaultRange,
    currency: ""
  });

  const monthPickerOptions = useMemo(
    () => [{ value: "", label: "Custom range" }, ...monthOptions],
    [monthOptions]
  );

  function onMonthChange(value: string) {
    setSelectedMonth(value);
    if (!value) return; // "Custom range" — keep the manually-entered dates
    const range = monthRange(value);
    setFrom(range.from);
    setTo(range.to);
    setGuardError(null);
  }

  function onFromChange(value: string) {
    setFrom(value);
    setSelectedMonth(""); // manual edits win over the picker
  }

  function onToChange(value: string) {
    setTo(value);
    setSelectedMonth("");
  }

  function applyFilters() {
    const error = periodError(from, to);
    setGuardError(error);
    if (error) return;
    setApplied({ from, to, currency });
  }

  const query = useQuery({
    queryKey: ["trial-balance", applied],
    queryFn: () =>
      getTrialBalance(token!, {
        from: `${applied.from}T00:00:00.000Z`,
        to: `${applied.to}T00:00:00.000Z`,
        currency: applied.currency || undefined
      }),
    placeholderData: keepPreviousData,
    retry: false
  });

  const report = query.data;
  const rows = report?.accounts ?? [];
  const is503 = isLedgerProxyUnavailable(query.error);

  return (
    <Page>
      <PageHeader
        title="Trial Balance"
        description="Unadjusted trial balance for a period, read-through from the ledger. Pick a period and currency, then Apply to reconcile debit/credit totals and per-account movement."
      />
      <PageContent>
        {/* Filter bar */}
        <Card>
          <CardContent className="flex flex-wrap items-end gap-3 p-4">
            <div className="flex flex-col gap-1">
              <label className="text-[10px] uppercase tracking-wide text-muted-foreground">
                Period
              </label>
              <Select
                value={selectedMonth}
                onChange={onMonthChange}
                options={monthPickerOptions}
                className="w-44"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="tb-from" className="text-[10px] uppercase tracking-wide text-muted-foreground">
                From
              </label>
              <Input
                id="tb-from"
                type="date"
                className="w-40"
                value={from}
                onChange={e => onFromChange(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="tb-to" className="text-[10px] uppercase tracking-wide text-muted-foreground">
                To (exclusive)
              </label>
              <Input
                id="tb-to"
                type="date"
                className="w-40"
                value={to}
                onChange={e => onToChange(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-[10px] uppercase tracking-wide text-muted-foreground">
                Currency
              </label>
              <Select
                value={currency}
                onChange={setCurrency}
                options={CURRENCY_OPTIONS}
                className="w-52"
                searchable
                searchPlaceholder="Search currencies…"
              />
            </div>
            <Button onClick={applyFilters} disabled={query.isFetching}>
              Apply
            </Button>
          </CardContent>
        </Card>

        {guardError ? (
          <InlineNotice title="Invalid period" description={guardError} tone="danger" />
        ) : null}

        {/* Summary header */}
        {report ? (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Period</p>
                <p className="text-sm font-semibold text-foreground">
                  {formatPeriod(report.from, report.to)}
                </p>
              </div>
              {report.isBalanced ? (
                <Badge variant="success">Balanced</Badge>
              ) : (
                <Badge variant="destructive">Out of balance</Badge>
              )}
            </div>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Stat label="Total Debits" value={formatTotal(report.totalDebits, report.currency)} />
              <Stat label="Total Credits" value={formatTotal(report.totalCredits, report.currency)} />
              <Stat label="Net Movement" value={formatTotal(netOf(report), report.currency)} />
              <Stat
                label="Accounts"
                value={String(report.numberOfAccounts)}
                hint={report.currency ?? "All currencies"}
              />
            </div>
          </div>
        ) : null}

        {/* Table / states */}
        {query.isLoading ? (
          <TableContainer className="rounded-lg border border-border bg-card">
            <Table>
              <THead>{TABLE_COLUMNS}</THead>
              <TBody>
                <TableLoadingRows columns={7} rows={6} />
              </TBody>
            </Table>
          </TableContainer>
        ) : query.error ? (
          <StatePanel
            title={is503 ? "Ledger proxy degraded" : "Failed to load trial balance"}
            description={
              is503
                ? "The ledger service is currently unavailable. Try again in a moment."
                : getErrorMessage(query.error)
            }
            tone={is503 ? "warning" : "danger"}
            icon={is503 ? "access" : "error"}
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          />
        ) : rows.length === 0 ? (
          <StatePanel
            title="No account activity"
            description="No accounts had movement in the selected period."
            iconNode={<Scale className="h-5 w-5" />}
          />
        ) : (
          <TableContainer className="rounded-lg border border-border bg-card">
            <Table>
              <THead>{TABLE_COLUMNS}</THead>
              <TBody>
                {rows.map(row => (
                  <TR key={row.accountId}>
                    <TD className="font-mono text-xs">{row.accountCode}</TD>
                    <TD className="max-w-[16rem] truncate">{row.accountName}</TD>
                    <TD>
                      <Badge variant={row.accountOwnershipType === "SYSTEM" ? "neutral" : "secondary"}>
                        {formatEnumValue(row.accountOwnershipType)}
                      </Badge>
                      <div className="mt-0.5 max-w-[12rem] truncate font-mono text-[10px] text-muted-foreground">
                        {row.accountOwnershipType === "ORGANIZATION" ? row.accountOwnerId ?? "—" : "—"}
                      </div>
                    </TD>
                    <TD>{row.currency}</TD>
                    <TD className="text-right tabular-nums">
                      {formatMoney(row.totalDebits, row.currency)}
                    </TD>
                    <TD className="text-right tabular-nums">
                      {formatMoney(row.totalCredits, row.currency)}
                    </TD>
                    <TD className="text-right tabular-nums">
                      {formatMoney(row.netMovement, row.currency)}
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </TableContainer>
        )}
      </PageContent>
    </Page>
  );
}
