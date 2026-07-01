import { appConfig } from "@/lib/env";

// ---------------------------------------------------------------------------
// ApiError
// ---------------------------------------------------------------------------

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

// ---------------------------------------------------------------------------
// Unauthorized callback — centralised 401 → signOut
// ---------------------------------------------------------------------------

type UnauthorizedCallback = () => void;
let _onUnauthorized: UnauthorizedCallback | null = null;

export function registerUnauthorizedHandler(cb: UnauthorizedCallback): void {
  _onUnauthorized = cb;
}

// ---------------------------------------------------------------------------
// Core request helper
// ---------------------------------------------------------------------------

type QueryValue = string | number | boolean | Array<string | number | boolean> | undefined | null;

function buildUrl(base: string, path: string, query?: Record<string, QueryValue>): string {
  const normalizedBase = base.endsWith("/") ? base : `${base}/`;
  const normalizedPath = path.replace(/^\/+/, "");
  const url = new URL(normalizedPath, normalizedBase);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === "") continue;
      if (Array.isArray(value)) {
        for (const entry of value) {
          if (entry !== undefined && entry !== null && entry !== "")
            url.searchParams.append(key, String(entry));
        }
        continue;
      }
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

async function safeJsonMessage(res: Response): Promise<string> {
  try {
    const text = await res.text();
    if (!text.trim()) return res.statusText || "Request failed";
    try {
      const json = JSON.parse(text) as Record<string, unknown>;
      return (
        (json.message as string) ||
        (json.error as string) ||
        text
      );
    } catch {
      return text;
    }
  } catch {
    return res.statusText || "Request failed";
  }
}

async function request<T>(
  path: string,
  options?: {
    token?: string;
    method?: string;
    body?: unknown;
    query?: Record<string, QueryValue>;
    formData?: FormData;
  }
): Promise<T> {
  const url = buildUrl(appConfig.apiBaseUrl, path, options?.query);
  const headers: Record<string, string> = {};
  if (options?.token) headers["Authorization"] = `Bearer ${options.token}`;
  if (options?.body) headers["Content-Type"] = "application/json";

  const response = await fetch(url, {
    method: options?.method ?? "GET",
    headers,
    body: options?.formData
      ? options.formData
      : options?.body
        ? JSON.stringify(options.body)
        : undefined
  });

  if (!response.ok) {
    const message = await safeJsonMessage(response);
    if (response.status === 401) {
      _onUnauthorized?.();
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get("content-type") ?? "";
  const contentLength = response.headers.get("content-length");
  if (contentLength === "0") return undefined as T;

  const text = await response.text();
  if (!text.trim()) return undefined as T;

  if (contentType.includes("application/json")) {
    return JSON.parse(text) as T;
  }
  return text as unknown as T;
}

// ---------------------------------------------------------------------------
// Shared pagination type (matches backend PageResponse<T>)
// ---------------------------------------------------------------------------

export type PageResponse<T> = {
  items: T[];
  page: number;
  perPage: number;
  total: number;
};

// Cursor-paginated response wrapper (matches backend CursorPageResponse<T>). Used for
// append-only ledger streams that are keyset-paginated rather than offset-paginated.
export type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
  previousCursor: string | null;
  hasMore: boolean;
  size: number;
};

export type SortDirection = "asc" | "desc";

// Shared list params for paginated, searchable, sortable reference-data endpoints.
export type ListParams = {
  page?: number;
  perPage?: number;
  search?: string;
  sortBy?: string;
  sortDir?: SortDirection;
};

// ---------------------------------------------------------------------------
// Auth DTOs
// ---------------------------------------------------------------------------

export type LoginResponse = {
  access_token: string;
  token_type: string;
  expires_in: number | null;
  refresh_token: string | null;
};

export type UserInfoResponse = {
  subject: string;
  authorities: string[];
};

// ---------------------------------------------------------------------------
// Health DTO
// ---------------------------------------------------------------------------

export type HealthResponse = {
  status: string;
  timestamp: string;
  clusterLabel: string;
};

// ---------------------------------------------------------------------------
// Chart of Accounts DTOs
// ---------------------------------------------------------------------------

export type AccountRole =
  | "SETTLEMENT_ACCOUNT"
  | "PLATFORM_FLOAT"
  | "PLATFORM_FLOAT_MTN"
  | "PLATFORM_FLOAT_TELECEL"
  | "PLATFORM_FEE"
  | "PROVIDER_FEE"
  | string;

export type AccountCategory = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE" | "CONTRA" | string;

export type ProvisioningStatus = "PENDING" | "PROVISIONED" | "FAILED" | string;

export type ChartOfAccountsRoleResponse = {
  role: AccountRole;
  accountCode: string;
  category: AccountCategory;
  currency: string;
  channel: string | null;
  defaultVaId: string | null;
  vaId: string | null;
  provisioningStatus: ProvisioningStatus;
};

export type UpdateRoleRequest = {
  defaultVaId: string;
  currency: string;
};

export type BootstrapResult = {
  provisioned: number;
  pending: number;
  failed: number;
  errors: string[];
};

// ---------------------------------------------------------------------------
// Flow Config DTOs
// ---------------------------------------------------------------------------

export type SlotConfig = {
  slotName: string;
  accountRole: string | null;
  explicitVaId: string | null;
  effectiveVaId: string | null;
};

export type FlowConfigResponse = {
  flowType: string;
  slots: SlotConfig[];
};

export type SlotUpdate = {
  slotName: string;
  accountRole?: string | null;
  explicitVaId?: string | null;
};

export type UpdateFlowConfigRequest = {
  slots: SlotUpdate[];
};

// ---------------------------------------------------------------------------
// Virtual Account DTOs
// ---------------------------------------------------------------------------

export type AccountOwnershipType = "SYSTEM" | "ORGANIZATION" | string;
export type AccountStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED" | string;

export type VirtualAccountResponse = {
  vaId: string;
  name: string;
  ownershipType: AccountOwnershipType;
  organizationId: string | null;
  currency: string;
  status: AccountStatus;
  channel: string | null;
  accountRole: AccountRole | null;
  accountCategory: string | null;
  createdVia: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateVirtualAccountRequest = {
  name: string;
  ownershipType: string;
  currency: string;
  organizationId?: string;
  accountCode?: string;
  accountCategory?: string;
  parentAccountId?: string;
  overdraftLimit?: number;
  minimumBalance?: number;
};

/**
 * Body returned with HTTP 202 when a VA creation request has been forwarded to the ledger. The VA
 * itself materializes asynchronously once the ledger.account.created event is consumed.
 */
export type VirtualAccountRequestAccepted = {
  status: string;
  message: string;
  accountCode: string | null;
  organizationId: string | null;
  currency: string;
  ownershipType: string;
};

export type VirtualAccountFilters = {
  page?: number;
  perPage?: number;
  ownershipType?: string;
  organizationId?: string;
  currency?: string;
  status?: string;
  search?: string;
};

// ---------------------------------------------------------------------------
// Publish History DTOs
// ---------------------------------------------------------------------------

export type PublishRecordResponse = {
  id: string;
  eventId: string;
  eventType: string;
  topic: string;
  source: string;
  correlationId: string | null;
  idempotencyKey: string | null;
  tenantId: string | null;
  // The canonical request id; correlation key for the ledger Outcome column (Phase 017). Null for
  // non-transactional flows and historical rows.
  transactionRequestId: string | null;
  sourceVaId: string | null;
  destinationVaId: string | null;
  status: string;
  intentionalFailure: boolean;
  chaosStrategy: string | null;
  payloadJson: string | null;
  batchId: string | null;
  batchRowId: string | null;
  kafkaOffset: number | null;
  kafkaPartition: number | null;
  createdAt: string;
};

export type HistoryFilters = {
  page?: number;
  size?: number;
  vaId?: string;
  eventType?: string;
  correlationId?: string;
  batchId?: string;
  status?: string;
  from?: string;
  to?: string;
};

// ---------------------------------------------------------------------------
// Flow DTOs
// ---------------------------------------------------------------------------

export type FieldKind =
  | "TEXT"
  | "UUID"
  | "AMOUNT"
  | "INTEGER"
  | "DATETIME"
  | "SELECT"
  | "VA_REF"
  | "FEE_LIST"
  | "COUNTRY";
export type AutogenRule = "NONE" | "UUID_V4" | "ULID";
export type InferenceRule =
  | "NONE"
  | "ORG_FROM_SOURCE_VA"
  | "ORG_FROM_DEST_VA"
  | "CURRENCY_FROM_SOURCE_VA"
  | "TENANT_FROM_SOURCE_VA"
  | "CORRIDOR_FROM_COUNTRIES";
export type AccountKind = "ORGANIZATION" | "SYSTEM";

export type FlowFieldDescriptor = {
  name: string;
  label: string;
  kind: FieldKind;
  required: boolean;
  advanced: boolean;
  defaultValue: string | null;
  autogen: AutogenRule;
  inference: InferenceRule;
  accountKind: AccountKind | null;
  slotName: string | null;
  options: string[] | null;
};

// A single initiated→secondary field copy within a lifecycle. Applied only where the secondary
// form declares a descriptor named `toField`, so one list serves both completed and failed phases.
export type CarryOver = { fromField: string; toField: string };

// Groups a multi-step transaction lifecycle (Settlement, Disbursement) and its carry-over. Present
// (non-null) on the `initiated` catalog entry; null on single-shot flows.
export type FlowLifecycle = {
  label: string;
  initiated: string;
  completed: string;
  failed: string;
  carryOver: CarryOver[];
};

// Groups the four phases of a batch-disbursement fan-out (one reservation → N items, each item a
// request → completed|failed) and the carry-over maps the wizard/runner use. Present (non-null) on
// the reservation catalog entry; null otherwise. Mutually exclusive with `lifecycle`.
export type BatchDisbursementGroup = {
  label: string;
  reservation: string;
  itemRequest: string;
  itemCompleted: string;
  itemFailed: string;
  reservationToItem: CarryOver[];
  itemRequestToTerminal: CarryOver[];
};

export type FlowCatalogEntry = {
  flowType: string;
  topic: string;
  source: string;
  runnerVisible: boolean;
  fields: FlowFieldDescriptor[];
  requiredFields: string[];
  optionalFields: string[];
  csvColumns: string[];
  partitionKeyField: string;
  lifecycle: FlowLifecycle | null;
  batchGroup: BatchDisbursementGroup | null;
};

export type DuplicateOptions = { count: number };
export type OutOfOrderOptions = { order: number[] };
export type MalformedOptions = { mutations: string[] };
export type UnbalancedOptions = { delta: number };
export type BurstOptions = { count: number; ratePerSecond: number };
export type DelayOptions = { delayMs: number; jitterMs: number };

// N-Times: run a flow `count` times against the same accounts producing genuinely-distinct
// transactions (fresh *_request_id per iteration). Distinct from Burst (which is duplicate-keyed).
export type NTimesPacing = "BURST" | "LINEAR" | "RANDOM";
export type NTimesMode = "SYNC" | "ASYNC";
export type NTimesOptions = {
  count: number;
  pacing: NTimesPacing;
  mode: NTimesMode;
  fixedDelayMs?: number | null;
  minDelayMs?: number | null;
  maxDelayMs?: number | null;
};

export type ChaosOptions = {
  duplicate?: DuplicateOptions | null;
  outOfOrder?: OutOfOrderOptions | null;
  malformed?: MalformedOptions | null;
  unbalanced?: UnbalancedOptions | null;
  burst?: BurstOptions | null;
  delay?: DelayOptions | null;
  nTimes?: NTimesOptions | null;
};

// Aggregate result of a synchronous N-Times run (HTTP 200 from /n-times).
export type NTimesSyncResult = {
  flowType: string;
  count: number;
  succeeded: number;
  failed: number;
  correlationId: string;
  eventIds: string[];
  historyIds: string[];
  // Per-iteration request ids (server-minted by the N-Times expander), order-aligned with eventIds;
  // entries may be null for non-transactional flows (Phase 017).
  transactionRequestIds: (string | null)[];
};

// Discriminated result of publishNTimes: SYNC returns an aggregate, ASYNC returns a run handle.
export type PublishNTimesResponse =
  | { kind: "sync"; result: NTimesSyncResult }
  | { kind: "async"; run: BatchRunResponse };

// A typed fee row carried on a fee-bearing flow (collection, disbursement-completed). Amounts are
// decimal strings to preserve precision end-to-end; the backend maps these to TransactionFeeLine.
export type FeeInput = {
  feeType?: string | null;
  amount?: string | number | null;
  feeCode?: string | null;
  destinationVaId?: string | null;
};

export type PublishFlowRequest = {
  correlationId?: string | null;
  tenantId?: string | null;
  channel?: string | null;
  amount?: number | string | null;
  grossAmount?: number | string | null;
  netAmount?: number | string | null;
  currency?: string | null;
  slotOverrides?: Record<string, string>;
  chaos?: ChaosOptions | null;
  flowFields?: Record<string, unknown>;
  fees?: FeeInput[];
};

export type FlowResult = {
  eventId: string;
  topic: string;
  partition: number;
  offset: number;
  status: string;
  historyId: string;
  error: string | null;
  // The id the ledger files under transactionRequestId — the correlation key for the run-page
  // failure/reservation watches; null for non-transactional flows (Phase 017).
  transactionRequestId: string | null;
};

// ---------------------------------------------------------------------------
// Batch DTOs
// ---------------------------------------------------------------------------

export type BatchRunStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "COMPLETED_WITH_FAILURES"
  | "FAILED"
  | "CANCELLED"
  | string;

export type RunKind = "CSV" | "N_TIMES" | "LIFECYCLE" | "BATCH_DISBURSEMENT" | string;

export type BatchRunResponse = {
  id: string;
  flowType: string;
  filename: string | null;
  kind: RunKind;
  pacing: string | null;
  mode: string | null;
  total: number;
  succeeded: number;
  failed: number;
  invalid: number;
  status: BatchRunStatus;
  createdAt: string;
  completedAt: string | null;
  // Set for BATCH_DISBURSEMENT runs: the ledger batch_id (deep-link key) and resolved reservation_id.
  externalBatchId?: string | null;
  reservationId?: string | null;
};

export type BatchRowStatus = "PENDING" | "PUBLISHED" | "FAILED" | "INVALID" | string;

export type BatchRowResponse = {
  id: string;
  batchId: string;
  rowNumber: number;
  status: BatchRowStatus;
  eventId: string | null;
  error: string | null;
  createdAt: string;
};

// Coarse publish-status rollup for a run in the run-grouped feed (`GET /api/v0/runs`).
export type RunStatusRollup =
  | "RUNNING"
  | "ALL_PUBLISHED"
  | "HAS_FAILURES"
  | "FAILED"
  | string;

// One row of the run-grouped feed (ADR-031). A run is either a tracked `batch_run`
// (`tracked: true`, drilled down via `?batchId`) or an untracked `correlation_id` group of
// publish records (`tracked: false`, drilled down via `?correlationId`).
export type RunSummaryResponse = {
  runKey: string;
  tracked: boolean;
  kind: RunKind | "SINGLE" | "MANUAL_SEQUENCE" | string;
  flowTypes: string[];
  eventCount: number;
  status: RunStatusRollup;
  publishedCount: number;
  failedCount: number;
  intentionalFailure: boolean;
  firstActivityAt: string;
  lastActivityAt: string;
  externalBatchId: string | null;
  correlationId: string | null;
  batchId: string | null;
};

export type RunsFilters = {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
  kind?: string;
};

// ---------------------------------------------------------------------------
// Ledger Proxy DTOs
// ---------------------------------------------------------------------------

// Mirrors the ledger service's account response shape (camelCase), passed through by the proxy.
export type LedgerAccountDto = {
  accountId: string;
  accountCode: string | null;
  accountName: string | null;
  accountCategory: string | null;
  normalBalance: string | null;
  currency: string;
  status: string | null;
  accountOwnershipType: string | null;
  organizationId: string | null;
  parentAccountId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type LedgerBalanceDto = {
  accountId: string;
  total: number;
  available: number;
  currency: string;
  pending: number;
  reserved: number;
  lastEntrySequence: number;
  balanceAsOf: string | null;
};

// One row of the ledger's batch-balance lookup. Carries a per-account status so the list views can
// render a balance, an em dash (NOT_FOUND / FORBIDDEN), or a loading placeholder per row.
export type BatchBalanceItem = {
  accountId: string;
  status: "FOUND" | "NOT_FOUND" | "FORBIDDEN" | string;
  currency: string | null;
  availableBalance: number | null;
  pendingBalance: number | null;
  reservedBalance: number | null;
  totalBalance: number | null;
  lastEntrySequence: number | null;
  balanceAsOf: string | null;
};

// A counterparty leg of a transaction-history record — one line in the same journal entry posted
// against a different account than the one whose history is being viewed.
export type LedgerCounterpartyLine = {
  accountId: string | null;
  accountCode: string | null;
  accountName: string | null;
  direction: string | null;
  entryLineType: string | null;
  amount: number | null;
  memo: string | null;
};

// Mirrors the ledger's account transaction-history record (camelCase, passed through by the proxy).
// One row per journal-entry line, enriched with parent-entry header fields and counterparty legs.
export type LedgerTransactionHistoryRecord = {
  lineId: string;
  journalEntryId: string | null;
  postedAt: string | null;
  transactionRef: string | null;
  entryType: string | null;
  entryLineType: string | null;
  direction: string | null;
  amount: number | null;
  currency: string | null;
  runningBalance: number | null;
  runningReservedBalance: number | null;
  runningPendingBalance: number | null;
  // The account's total balance immediately before this line; null until the ledger emits it.
  totalBalanceBefore: number | null;
  narrative: string | null;
  memo: string | null;
  entrySequence: number;
  accountSequence: number;
  primaryCounterpartyAccountId: string | null;
  counterPartyJournalEntryLines: LedgerCounterpartyLine[] | null;
};

// Filters for the cursor-paginated account transaction-history endpoint. Note: this endpoint is
// keyset-paginated — pages are walked via `cursor`, never an offset `page`.
export type AccountTransactionHistoryFilters = {
  cursor?: string;
  size?: number;
  from?: string;
  to?: string;
  entryType?: string;
  direction?: string;
  transactionRef?: string;
};

// One leg of a single transaction (resolved by transaction reference across all participating
// accounts). Mirrors the ledger's camelCase TransactionReferenceHistoryRecord.
export type LedgerTransactionReferenceRecord = {
  lineId: string;
  journalEntryId: string | null;
  accountId: string | null;
  accountOwnershipType: string | null;
  accountOwnerId: string | null;
  postedAt: string | null;
  transactionRef: string | null;
  entryType: string | null;
  entryLineType: string | null;
  direction: string | null;
  amount: number | null;
  currency: string | null;
  runningBalance: number | null;
  runningReservedBalance: number | null;
  runningPendingBalance: number | null;
  narrative: string | null;
  memo: string | null;
  entrySequence: number;
  accountSequence: number;
};

// One per-account row of the trial balance. Mirrors the ledger's camelCase TrialBalanceEntry,
// passed through by the proxy. Money fields arrive as BigDecimal → string over the wire; format
// them with `formatMoney` (which accepts strings) rather than parsing to a number, to avoid drift.
export type TrialBalanceEntry = {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountOwnerId: string | null;
  accountOwnershipType: "SYSTEM" | "ORGANIZATION" | string;
  currency: string;
  totalDebits: string;
  totalCredits: string;
  netMovement: string;
};

// A ledger reservation, read-proxied through the chaos gateway (camelCase, passed through). `id` is
// the reservation id sourced into a disbursement's second event; `transactionRef` equals the
// disbursement's transaction_id.
export type ReservationResponse = {
  id: string;
  accountId: string;
  transactionRef: string | null;
  type: string | null;
  status: string | null;
  amount: number | null;
  amountCaptured: number | null;
  amountReleased: number | null;
  disbursementBatchId: string | null;
  expiresAt: string | null;
  createdAt: string | null;
  resolvedAt: string | null;
};

// The ledger's disbursement batch summary, read-proxied through the chaos gateway
// (`GET /ledger/disbursement-batches/{batchId}`). `reservationId` is null until the reservation
// lands; `status` is the ledger-derived batch status. Feeds the wizard progress panel and the
// automatic run-results Ledger Batch panel (ADR-023).
export type DisbursementBatchSummary = {
  batchId: string;
  reservationId: string | null;
  status: string;
  currency: string | null;
  itemCount: number;
  processedCount: number;
  failedCount: number;
  pendingCount: number;
  totalPrincipalAmount: number | string | null;
  totalFees: number | string | null;
  totalAmount: number | string | null;
  amountCaptured: number | string | null;
  amountReleased: number | string | null;
  createdAt: string | null;
};

// The unadjusted trial balance for a period. Mirrors the ledger's TrialBalanceResponse, read-proxied
// through the chaos gateway (`GET /ledger/reporting/trial-balance`). `to` is an exclusive upper
// bound; `currency` is null when the report aggregates all currencies.
export type TrialBalanceResponse = {
  from: string;
  to: string;
  currency: string | null;
  totalDebits: string;
  totalCredits: string;
  isBalanced: boolean;
  numberOfAccounts: number;
  accounts: TrialBalanceEntry[];
};

// One sibling leg of a reconciliation journal-entry record (the line shape minus its own
// `siblingLines`). Money fields arrive as decimal strings.
export type ReconciliationSiblingLine = {
  lineId: string | null;
  journalEntryId: string | null;
  postedAt: string | null;
  entrySequence: number | null;
  accountSequence: number | null;
  accountId: string | null;
  accountCode: string | null;
  organizationId: string | null;
  currency: string | null;
  direction: string | null;
  amount: string | null;
  runningBalance: string | null;
  runningReservedBalance: string | null;
  runningPendingBalance: string | null;
  totalBalanceBefore: string | null;
  transactionRef: string | null;
  entryType: string | null;
  narrative: string | null;
  memo: string | null;
  sourceService: string | null;
  sourceEventId: string | null;
  metadata: Record<string, unknown> | null;
};

// One row of the ledger's reconciliation export, read-proxied through the chaos
// gateway (`GET /ledger/reporting/reconciliation-export`). Each row is a journal-entry line with its
// sibling legs; the table links by `transactionRef` to the by-reference detail page (ADR-032).
export type ReconciliationEntryResponse = ReconciliationSiblingLine & {
  siblingLines: ReconciliationSiblingLine[] | null;
};

export type LedgerJournalEntriesFilters = {
  from: string;
  to: string;
  accountId?: string | string[];
  entryType?: string;
  transactionRef?: string;
  sourceService?: string;
  page?: number;
  size?: number;
};

// ---------------------------------------------------------------------------
// API functions — Auth
// ---------------------------------------------------------------------------

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: { email, password }
  });
}

export function getMe(token: string): Promise<UserInfoResponse> {
  return request<UserInfoResponse>("/auth/me", { token });
}

// ---------------------------------------------------------------------------
// API functions — Health
// ---------------------------------------------------------------------------

export function getHealth(token?: string): Promise<HealthResponse> {
  return request<HealthResponse>("/health", { token });
}

// ---------------------------------------------------------------------------
// API functions — Chart of Accounts
// ---------------------------------------------------------------------------

export function listChartOfAccounts(token: string): Promise<ChartOfAccountsRoleResponse[]> {
  return request<ChartOfAccountsRoleResponse[]>("/chart-of-accounts", { token });
}

export function updateRole(
  token: string,
  role: string,
  body: UpdateRoleRequest
): Promise<ChartOfAccountsRoleResponse> {
  return request<ChartOfAccountsRoleResponse>(`/chart-of-accounts/${encodeURIComponent(role)}`, {
    token,
    method: "PUT",
    body
  });
}

export function triggerChartOfAccountsBootstrap(token: string): Promise<BootstrapResult> {
  return request<BootstrapResult>("/chart-of-accounts/bootstrap", { token, method: "POST" });
}

export function listFlowConfigs(token: string): Promise<FlowConfigResponse[]> {
  return request<FlowConfigResponse[]>("/flow-configs", { token });
}

export function updateFlowConfig(
  token: string,
  flowType: string,
  body: UpdateFlowConfigRequest
): Promise<FlowConfigResponse> {
  return request<FlowConfigResponse>(`/flow-configs/${encodeURIComponent(flowType)}`, {
    token,
    method: "PUT",
    body
  });
}

// ---------------------------------------------------------------------------
// API functions — Virtual Accounts
// ---------------------------------------------------------------------------

export function listVirtualAccounts(
  token: string,
  filters: VirtualAccountFilters = {}
): Promise<PageResponse<VirtualAccountResponse>> {
  const { page = 0, perPage = 20, ...rest } = filters;
  return request<PageResponse<VirtualAccountResponse>>("/virtual-accounts", {
    token,
    query: { page, perPage, ...rest }
  });
}

export function getVirtualAccount(token: string, vaId: string): Promise<VirtualAccountResponse> {
  return request<VirtualAccountResponse>(`/virtual-accounts/${encodeURIComponent(vaId)}`, {
    token
  });
}

export function createVirtualAccount(
  token: string,
  body: CreateVirtualAccountRequest
): Promise<VirtualAccountRequestAccepted> {
  return request<VirtualAccountRequestAccepted>("/virtual-accounts", {
    token,
    method: "POST",
    body
  });
}

// ---------------------------------------------------------------------------
// API functions — History (sent by chaos machine)
// ---------------------------------------------------------------------------

export function listSentHistory(
  token: string,
  filters: HistoryFilters = {}
): Promise<PageResponse<PublishRecordResponse>> {
  const { page = 0, size = 20, vaId, ...rest } = filters;
  return request<PageResponse<PublishRecordResponse>>("/history", {
    token,
    query: {
      page,
      size,
      sourceVaId: vaId,
      ...rest
    }
  });
}

export function getSentHistoryRecord(token: string, id: string): Promise<PublishRecordResponse> {
  return request<PublishRecordResponse>(`/history/${encodeURIComponent(id)}`, { token });
}

// The run-grouped feed that powers the Scenario Runner's Run History tab (ADR-031). Each item is a
// run (tracked `batch_run` or untracked `correlation_id` group), newest-first; expand a run by
// re-querying `/history` with `?batchId` (tracked) or `?correlationId` (untracked).
export function listRuns(
  token: string,
  filters: RunsFilters = {}
): Promise<PageResponse<RunSummaryResponse>> {
  const { page = 0, size = 20, ...rest } = filters;
  return request<PageResponse<RunSummaryResponse>>("/runs", {
    token,
    query: { page, size, ...rest }
  });
}

// ---------------------------------------------------------------------------
// Ledger Kafka Events — projections (Phases 017–020)
// ---------------------------------------------------------------------------

// A projected ledger.transaction.failed event (Phase 017). `transactionRequestId` is the chaos
// request id (correlation key); `ledgerTransactionId` is the ledger's own recording id — distinct.
export type TransactionFailureResponse = {
  id: string;
  eventId: string;
  transactionRequestId: string;
  ledgerTransactionId: string;
  transactionType: string;
  failureCode: string | null;
  failureReason: string | null;
  ledgerCorrelationId: string | null;
  idempotencyKey: string | null;
  tenantId: string | null;
  occurredAt: string;
  receivedAt: string;
  payloadJson: string | null;
};

// A projected ledger.balance.updated row (Phase 018) — an account's post-mutation snapshot. Field
// names mirror LedgerBalanceDto so the UI reuses its vocabulary. Not correlatable to a publish.
export type BalanceHistoryResponse = {
  eventId: string;
  accountId: string;
  available: number;
  pending: number;
  reserved: number;
  total: number;
  totalDebits: number | null;
  totalCredits: number | null;
  lastEntrySequence: number;
  balanceAsOf: string;
  currency: string | null;
  occurredAt: string;
  idempotencyKey: string | null;
};

// A projected reservation lifecycle row (Phase 019) — push-fed and event-faithful, distinct from the
// read-proxy ReservationResponse (which carries richer captured/released amounts + expiry).
export type ReservationStateResponse = {
  reservationId: string;
  accountId: string;
  transactionId: string;
  reservationType: string;
  disbursementBatchId: string | null;
  amount: number;
  currency: string | null;
  status: string;
  releaseEventCount: number;
  createdAt: string | null;
  updatedAt: string;
  terminalAt: string | null;
  payloadJson: string | null;
};

// A projected ledger inbound dead letter (Phase 020). Heavy fields (originalPayloadJson, rawDltJson)
// are populated only on the by-id detail path; null in list rows.
export type DeadLetterRecordResponse = {
  id: string;
  dltTopic: string;
  originalTopic: string;
  domain: string;
  source: string;
  eventType: string | null;
  eventId: string | null;
  transactionId: string | null;
  transactionType: string | null;
  failureClassification: string | null;
  errorType: string | null;
  errorMessage: string | null;
  retryCount: number | null;
  originalPartition: number | null;
  originalOffset: number | null;
  originalKey: string | null;
  deadLetteredAt: string | null;
  receivedAt: string;
  originalPayloadJson: string | null;
  rawDltJson: string | null;
};

export type DeadLetterFilters = {
  page?: number;
  size?: number;
  domain?: string;
  transactionId?: string;
  transactionType?: string;
  originalTopic?: string;
  failureClassification?: string;
  from?: string;
  to?: string;
};

// --- Transaction failures (Phase 017) ---

// Single-key poll for the run-page failure watch: returns the matching failure(s) for one request id.
export function getTransactionFailureByRequestId(
  token: string,
  transactionRequestId: string
): Promise<PageResponse<TransactionFailureResponse>> {
  return request<PageResponse<TransactionFailureResponse>>("/transaction-failures", {
    token,
    query: { transactionRequestId }
  });
}

// Batch lookup for the "Sent" history page: one call resolving outcomes for many request ids.
export function listTransactionFailuresByRequestIds(
  token: string,
  transactionRequestIds: string[]
): Promise<PageResponse<TransactionFailureResponse>> {
  const ids = Array.from(new Set(transactionRequestIds.filter(Boolean)));
  if (ids.length === 0) {
    return Promise.resolve({ items: [], page: 0, perPage: 0, total: 0 });
  }
  return request<PageResponse<TransactionFailureResponse>>("/transaction-failures", {
    token,
    query: { transactionRequestIds: ids }
  });
}

// --- Balance history (Phase 018) ---

export function getBalanceHistory(
  token: string,
  vaId: string,
  params: { from?: string; to?: string; page?: number; size?: number } = {}
): Promise<PageResponse<BalanceHistoryResponse>> {
  const { page = 0, size = 20, from, to } = params;
  return request<PageResponse<BalanceHistoryResponse>>(
    `/virtual-accounts/${encodeURIComponent(vaId)}/balance-history`,
    { token, query: { page, size, from, to } }
  );
}

// Flat/batch balance history across involved accounts (the run-page balance watch).
export function listBalanceHistoryByAccounts(
  token: string,
  accountIds: string[],
  params: { from?: string; to?: string; page?: number; size?: number } = {}
): Promise<PageResponse<BalanceHistoryResponse>> {
  const ids = Array.from(new Set(accountIds.filter(Boolean)));
  if (ids.length === 0) {
    return Promise.resolve({ items: [], page: 0, perPage: 0, total: 0 });
  }
  const { page = 0, size = 50, from, to } = params;
  return request<PageResponse<BalanceHistoryResponse>>("/balance-history", {
    token,
    query: { accountId: ids, page, size, from, to }
  });
}

// --- Reservation lifecycle projection (Phase 019). Distinct from getAccountReservations (read-proxy). ---

export function getVaReservations(
  token: string,
  vaId: string,
  params: { status?: string; from?: string; to?: string; page?: number; size?: number } = {}
): Promise<PageResponse<ReservationStateResponse>> {
  const { page = 0, size = 20, status, from, to } = params;
  return request<PageResponse<ReservationStateResponse>>(
    `/virtual-accounts/${encodeURIComponent(vaId)}/reservations`,
    { token, query: { page, size, status, from, to } }
  );
}

// Flat/batch reservation query for the wizard toast watch (by transactionRef or batchId).
export function listReservations(
  token: string,
  params: {
    transactionRef?: string;
    batchId?: string;
    accountId?: string[];
    status?: string;
    page?: number;
    size?: number;
  } = {}
): Promise<PageResponse<ReservationStateResponse>> {
  const { page = 0, size = 20, transactionRef, batchId, accountId, status } = params;
  return request<PageResponse<ReservationStateResponse>>("/reservations", {
    token,
    query: { transactionRef, batchId, accountId, status, page, size }
  });
}

// --- Dead Letter Queue (Phase 020) ---

export function listDeadLetters(
  token: string,
  filters: DeadLetterFilters = {}
): Promise<PageResponse<DeadLetterRecordResponse>> {
  const { page = 0, size = 20, ...rest } = filters;
  return request<PageResponse<DeadLetterRecordResponse>>("/dlq", {
    token,
    query: { page, size, ...rest }
  });
}

export function getDeadLetter(token: string, id: string): Promise<DeadLetterRecordResponse> {
  return request<DeadLetterRecordResponse>(`/dlq/${encodeURIComponent(id)}`, { token });
}

// ---------------------------------------------------------------------------
// API functions — Ledger Proxy
// ---------------------------------------------------------------------------

export type LedgerAccountFilters = {
  page?: number;
  size?: number;
  ownershipType?: string;
  organizationId?: string;
  status?: string;
  currency?: string;
};

export function listLedgerAccounts(
  token: string,
  filters: LedgerAccountFilters = {}
): Promise<PageResponse<LedgerAccountDto>> {
  const { page = 0, size = 20, ...rest } = filters;
  return request<PageResponse<LedgerAccountDto>>("/ledger/accounts", {
    token,
    query: { page, size, ...rest }
  });
}

export function getLedgerAccount(
  token: string,
  vaId: string
): Promise<LedgerAccountDto> {
  return request<LedgerAccountDto>(`/ledger/accounts/${encodeURIComponent(vaId)}`, { token });
}

// Fetches an account's ledger balance, optionally as of a point in time. `asOf` is a zoneless ISO
// local date-time (e.g. "2026-06-01T12:00") interpreted in the ledger's zone — pass it verbatim, do
// NOT call Date.toISOString() (that emits a UTC "Z" suffix and shifts the wall-clock the operator
// picked). The ledger reconstructs the historical snapshot and rejects a future-dated `asOf`.
export function getLedgerAccountBalances(
  token: string,
  vaId: string,
  asOf?: string
): Promise<LedgerBalanceDto> {
  return request<LedgerBalanceDto>(
    `/ledger/accounts/${encodeURIComponent(vaId)}/balance`,
    { token, query: { asOf } }
  );
}

// Fetches balances for several accounts in a single call (one request per list page, not N). Ids are
// de-duplicated and the request is chunked to stay within the ledger's batch cap (100). Returns one
// item per id with a FOUND / NOT_FOUND / FORBIDDEN status.
export async function getBatchBalances(
  token: string,
  accountIds: string[]
): Promise<BatchBalanceItem[]> {
  const ids = Array.from(new Set(accountIds.filter(Boolean)));
  if (ids.length === 0) return [];
  const CHUNK = 100;
  const chunks: string[][] = [];
  for (let i = 0; i < ids.length; i += CHUNK) chunks.push(ids.slice(i, i + CHUNK));
  const results = await Promise.all(
    chunks.map(chunk =>
      request<BatchBalanceItem[]>("/ledger/balances", { token, query: { accountId: chunk } })
    )
  );
  return results.flat();
}

// Fetches a single cursor page of a virtual account's ledger transaction history. This is the
// correct per-account history endpoint; the global cross-account browse is listLedgerJournalEntries
// (`/ledger/reporting/reconciliation-export`). Pages are walked with the returned next/previous cursors.
export function getAccountTransactionHistory(
  token: string,
  accountId: string,
  filters: AccountTransactionHistoryFilters = {}
): Promise<CursorPage<LedgerTransactionHistoryRecord>> {
  return request<CursorPage<LedgerTransactionHistoryRecord>>(
    `/ledger/accounts/${encodeURIComponent(accountId)}/transactions`,
    { token, query: { ...filters } }
  );
}

// Resolves a single transaction by reference into its individual legs (one per participating
// account). Backs the transaction detail page.
export function getTransactionByReference(
  token: string,
  ref: string
): Promise<PageResponse<LedgerTransactionReferenceRecord>> {
  return request<PageResponse<LedgerTransactionReferenceRecord>>(
    `/ledger/transactions/${encodeURIComponent(ref)}`,
    { token }
  );
}

// Fetches the ledger's unadjusted trial balance for a period (read-proxied through the chaos
// gateway). `from`/`to` are ISO-8601 instants with `to` exclusive; an absent/empty `currency`
// aggregates all currencies.
export function getTrialBalance(
  token: string,
  params: { from: string; to: string; currency?: string }
): Promise<TrialBalanceResponse> {
  return request<TrialBalanceResponse>("/ledger/reporting/trial-balance", {
    token,
    query: { from: params.from, to: params.to, currency: params.currency || undefined }
  });
}

// Browses the ledger's reconciliation journal-entries export for a window (read-proxied through the
// chaos gateway, ADR-032). `from`/`to` are required ISO-8601 instants and the ledger caps the span
// (~7 days), so callers must default and clamp the window before calling. Offset-paginated; optional
// `accountId` (repeatable), `entryType`, `transactionRef`, `sourceService` filters pass through.
export function listLedgerJournalEntries(
  token: string,
  filters: LedgerJournalEntriesFilters
): Promise<PageResponse<ReconciliationEntryResponse>> {
  const { from, to, accountId, entryType, transactionRef, sourceService, page = 0, size = 20 } =
    filters;
  return request<PageResponse<ReconciliationEntryResponse>>("/ledger/reporting/reconciliation-export", {
    token,
    query: {
      from,
      to,
      accountId,
      entryType: entryType || undefined,
      transactionRef: transactionRef || undefined,
      sourceService: sourceService || undefined,
      page,
      size
    }
  });
}

// Lists an account's reservations, filtered by `transactionRef` (= a disbursement's transaction_id),
// read-proxied through the chaos gateway. Backs the disbursement wizard's reservation_id poll
// (ADR-018): poll this until the ledger-created reservation appears, else fall back to manual entry.
export function getAccountReservations(
  token: string,
  accountId: string,
  transactionRef?: string
): Promise<ReservationResponse[]> {
  return request<ReservationResponse[]>(
    `/ledger/accounts/${encodeURIComponent(accountId)}/reservations`,
    { token, query: { transactionRef } }
  );
}

// Fetches the ledger's disbursement batch summary by `batchId` (reservation_id + status + counters),
// read-proxied through the chaos gateway. Backs the batch wizard's reservation_id poll + progress
// panel and the automatic run-results Ledger Batch panel (ADR-023).
export function getDisbursementBatch(
  token: string,
  batchId: string
): Promise<DisbursementBatchSummary> {
  return request<DisbursementBatchSummary>(
    `/ledger/disbursement-batches/${encodeURIComponent(batchId)}`,
    { token }
  );
}

// ---------------------------------------------------------------------------
// API functions — Flows
// ---------------------------------------------------------------------------

export function getFlowCatalog(token: string): Promise<FlowCatalogEntry[]> {
  return request<FlowCatalogEntry[]>("/flows/catalog", { token });
}

export function runFlow(
  token: string,
  flowType: string,
  body: PublishFlowRequest
): Promise<FlowResult> {
  return request<FlowResult>(`/flows/${encodeURIComponent(flowType)}`, {
    token,
    method: "POST",
    body
  });
}

/**
 * Runs a flow N times (distinct transactions) via the dedicated `/n-times` endpoint, discriminating
 * on HTTP status: `200` → an aggregate {@link NTimesSyncResult} (SYNC); `202` → a run handle
 * (ASYNC). The shared `request` helper hides the status code, so this reads the response directly.
 */
export async function publishNTimes(
  token: string,
  flowType: string,
  body: PublishFlowRequest
): Promise<PublishNTimesResponse> {
  const url = buildUrl(appConfig.apiBaseUrl, `/flows/${encodeURIComponent(flowType)}/n-times`);
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const message = await safeJsonMessage(response);
    if (response.status === 401) _onUnauthorized?.();
    throw new ApiError(response.status, message);
  }

  const text = await response.text();
  const data = text.trim() ? JSON.parse(text) : {};
  return response.status === 202
    ? { kind: "async", run: data as BatchRunResponse }
    : { kind: "sync", result: data as NTimesSyncResult };
}

/**
 * Fires N distinct RANDOM-outcome lifecycles unattended on the backend (Settlement/Disbursement),
 * returning a `202` run handle to poll in the run-results view. `count`/pacing travel inside
 * `chaos.nTimes` (reusing the N-Times caps); the backend decides SUCCEED/FAIL per lifecycle.
 */
export function runRandomLifecycle(
  token: string,
  lifecycleType: string,
  body: PublishFlowRequest
): Promise<BatchRunResponse> {
  return request<BatchRunResponse>(
    `/flows/${encodeURIComponent(lifecycleType)}/random-lifecycle`,
    { token, method: "POST", body }
  );
}

// The per-item outcome policy for an automatic batch disbursement (mirrors the backend).
export type BatchOutcomeMode = "ALL_PASS" | "ALL_FAIL" | "COUNT" | "RANDOM";
export type BatchOutcomePolicy = {
  mode: BatchOutcomeMode;
  passCount?: number | null;
  seed?: number | null;
};

// Request body for `POST /flows/disbursement-batch/run` (automatic batch disbursement).
export type BatchDisbursementRunRequest = {
  sourceVaId: string;
  destinationVaId: string;
  merchantId: string;
  currency?: string | null;
  totalPrincipalAmount?: number | string | null;
  totalFees?: number | string | null;
  itemCount: number;
  disbursementSubtype?: string | null;
  merchantBatchRef?: string | null;
  callbackUrl?: string | null;
  authorisedUserId?: string | null;
  authorisedKeyFingerprint?: string | null;
  tenantId?: string | null;
  correlationId?: string | null;
  splitMode?: string | null;
  outcomePolicy: BatchOutcomePolicy;
  creditProviderId?: string | null;
  creditAccountId?: string | null;
  providerId?: string | null;
  sourceCountry?: string | null;
  destinationCountry?: string | null;
  feeVaId?: string | null;
  failureCode?: string | null;
  failureReason?: string | null;
  pacing?: NTimesOptions | null;
  chaos?: ChaosOptions | null;
};

// Runs a whole batch disbursement unattended on the backend (one reservation + N items split by an
// outcome policy), returning a `202` run handle to poll in the run-results view (carrying the ledger
// batch_id for the summary panel).
export function runDisbursementBatch(
  token: string,
  body: BatchDisbursementRunRequest
): Promise<BatchRunResponse> {
  return request<BatchRunResponse>("/flows/disbursement-batch/run", {
    token,
    method: "POST",
    body
  });
}

// ---------------------------------------------------------------------------
// API functions — Runs (tracked-run detail/progress)
// ---------------------------------------------------------------------------
//
// The CSV-batch ingest (`POST /batches`) and the run list (`GET /batches`) were retired in Phase 021
// (ADR-031); the run list moved to `listRuns` (`GET /runs`). The read-by-id endpoints below back the
// run-detail/progress page for all run kinds.

export function getBatch(token: string, id: string): Promise<BatchRunResponse> {
  return request<BatchRunResponse>(`/batches/${encodeURIComponent(id)}`, { token });
}

export function listBatchRows(
  token: string,
  id: string,
  params: { page?: number; size?: number } = {}
): Promise<PageResponse<BatchRowResponse>> {
  const { page = 0, size = 50 } = params;
  return request<PageResponse<BatchRowResponse>>(
    `/batches/${encodeURIComponent(id)}/rows`,
    { token, query: { page, size } }
  );
}

// ---------------------------------------------------------------------------
// Helper — is a batch run in a terminal state?
// ---------------------------------------------------------------------------

export function isBatchTerminal(status: BatchRunStatus): boolean {
  return (
    status === "COMPLETED" ||
    status === "COMPLETED_WITH_FAILURES" ||
    status === "FAILED" ||
    status === "CANCELLED"
  );
}

// ---------------------------------------------------------------------------
// Organization Onboarding DTOs — Countries
// ---------------------------------------------------------------------------

export type CountryStatus = "ACTIVE" | "INACTIVE" | string;

export type CurrencyRefResponse = {
  currencyId: string;
  code: string;
  name: string;
};

export type CountryResponse = {
  countryId: string;
  name: string;
  isoCode: string;
  status: CountryStatus;
  primaryCurrencyId: string | null;
  primaryCurrency: CurrencyRefResponse | null;
  modifiedDate: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateCountryRequest = {
  name: string;
  isoCode: string;
  status?: string;
  primaryCurrencyId?: string | null;
  modifiedDate?: string;
};

export type UpdateCountryRequest = CreateCountryRequest;

export type SeedSummary = {
  fetched: number;
  currenciesUpserted: number;
  countriesUpserted: number;
  skipped: boolean;
  error: string | null;
};

// ---------------------------------------------------------------------------
// Currency DTOs
// ---------------------------------------------------------------------------

export type CurrencyStatus = "ACTIVE" | "INACTIVE" | string;

export type CurrencyResponse = {
  currencyId: string;
  code: string;
  name: string;
  symbol: string | null;
  status: CurrencyStatus;
  createdAt: string;
  updatedAt: string;
};

export type CreateCurrencyRequest = {
  code: string;
  name: string;
  symbol?: string | null;
  status?: string;
};

export type UpdateCurrencyRequest = CreateCurrencyRequest;

// ---------------------------------------------------------------------------
// Supported Country DTOs
// ---------------------------------------------------------------------------

export type SupportedCountryStatus = "ACTIVE" | "INACTIVE" | string;

export type SupportedCountryResponse = {
  supportedCountryId: string;
  countryId: string;
  status: SupportedCountryStatus;
  country: {
    countryId: string;
    name: string;
    isoCode: string;
    primaryCurrency: CurrencyRefResponse | null;
  } | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateSupportedCountryRequest = {
  countryId: string;
  status?: string;
};

// ---------------------------------------------------------------------------
// Organization Type DTOs
// ---------------------------------------------------------------------------

export type OrganizationTypeResponse = {
  organizationTypeId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateOrganizationTypeRequest = { name: string };
export type UpdateOrganizationTypeRequest = { name: string };

// ---------------------------------------------------------------------------
// Organization DTOs
// ---------------------------------------------------------------------------

export type OrganizationStatus =
  | "ACTIVE"
  | "SUSPENDED"
  | "DORMANT"
  | "CLOSED"
  | string;

export type OrganizationResponse = {
  organizationId: string;
  name: string;
  organizationTypeId: string | null;
  countryId: string | null;
  typeName: string | null;
  countryName: string | null;
  countryIsoCode: string | null;
  countryStatus: string | null;
  countryModifiedDate: string | null;
  primaryCurrencyId: string | null;
  primaryCurrencyCode: string | null;
  primaryContactEmail: string | null;
  phoneNumbers: string[];
  status: OrganizationStatus;
  createdAt: string;
  updatedAt: string;
  eventId: string | null;
};

export type CreateOrganizationRequest = {
  name: string;
  organizationTypeId: string;
  countryId: string;
  primaryContactEmail?: string;
  phoneNumbers?: string[];
  status?: string;
};

// ---------------------------------------------------------------------------
// API functions — Countries
// ---------------------------------------------------------------------------

export function listCountries(
  token: string,
  params: ListParams = {}
): Promise<PageResponse<CountryResponse>> {
  const { page = 0, perPage = 20, search, sortBy, sortDir } = params;
  return request<PageResponse<CountryResponse>>("/countries", {
    token,
    query: { page, perPage, search, sortBy, sortDir }
  });
}

export function createCountry(
  token: string,
  body: CreateCountryRequest
): Promise<CountryResponse> {
  return request<CountryResponse>("/countries", { token, method: "POST", body });
}

export function updateCountry(
  token: string,
  countryId: string,
  body: UpdateCountryRequest
): Promise<CountryResponse> {
  return request<CountryResponse>(`/countries/${encodeURIComponent(countryId)}`, {
    token,
    method: "PUT",
    body
  });
}

export function refreshCountries(token: string): Promise<SeedSummary> {
  return request<SeedSummary>("/countries/refresh", { token, method: "POST" });
}

// ---------------------------------------------------------------------------
// API functions — Currencies
// ---------------------------------------------------------------------------

export function listCurrencies(
  token: string,
  params: ListParams = {}
): Promise<PageResponse<CurrencyResponse>> {
  const { page = 0, perPage = 20, search, sortBy, sortDir } = params;
  return request<PageResponse<CurrencyResponse>>("/currencies", {
    token,
    query: { page, perPage, search, sortBy, sortDir }
  });
}

export function createCurrency(
  token: string,
  body: CreateCurrencyRequest
): Promise<CurrencyResponse> {
  return request<CurrencyResponse>("/currencies", { token, method: "POST", body });
}

export function updateCurrency(
  token: string,
  currencyId: string,
  body: UpdateCurrencyRequest
): Promise<CurrencyResponse> {
  return request<CurrencyResponse>(`/currencies/${encodeURIComponent(currencyId)}`, {
    token,
    method: "PUT",
    body
  });
}

// ---------------------------------------------------------------------------
// API functions — Supported Countries
// ---------------------------------------------------------------------------

export function listSupportedCountries(
  token: string,
  params: ListParams = {}
): Promise<PageResponse<SupportedCountryResponse>> {
  const { page = 0, perPage = 20, search, sortBy, sortDir } = params;
  return request<PageResponse<SupportedCountryResponse>>("/supported-countries", {
    token,
    query: { page, perPage, search, sortBy, sortDir }
  });
}

export function createSupportedCountry(
  token: string,
  body: CreateSupportedCountryRequest
): Promise<SupportedCountryResponse> {
  return request<SupportedCountryResponse>("/supported-countries", {
    token,
    method: "POST",
    body
  });
}

export function deleteSupportedCountry(token: string, supportedCountryId: string): Promise<void> {
  return request<void>(`/supported-countries/${encodeURIComponent(supportedCountryId)}`, {
    token,
    method: "DELETE"
  });
}

// ---------------------------------------------------------------------------
// API functions — Organization Types
// ---------------------------------------------------------------------------

export function listOrganizationTypes(
  token: string,
  params: ListParams = {}
): Promise<PageResponse<OrganizationTypeResponse>> {
  const { page = 0, perPage = 20, search, sortBy, sortDir } = params;
  return request<PageResponse<OrganizationTypeResponse>>("/organization-types", {
    token,
    query: { page, perPage, search, sortBy, sortDir }
  });
}

export function createOrganizationType(
  token: string,
  body: CreateOrganizationTypeRequest
): Promise<OrganizationTypeResponse> {
  return request<OrganizationTypeResponse>("/organization-types", {
    token,
    method: "POST",
    body
  });
}

export function updateOrganizationType(
  token: string,
  organizationTypeId: string,
  body: UpdateOrganizationTypeRequest
): Promise<OrganizationTypeResponse> {
  return request<OrganizationTypeResponse>(
    `/organization-types/${encodeURIComponent(organizationTypeId)}`,
    { token, method: "PUT", body }
  );
}

// ---------------------------------------------------------------------------
// API functions — Organizations
// ---------------------------------------------------------------------------

export function listOrganizations(
  token: string,
  params: ListParams = {}
): Promise<PageResponse<OrganizationResponse>> {
  const { page = 0, perPage = 20, search, sortBy, sortDir } = params;
  return request<PageResponse<OrganizationResponse>>("/organizations", {
    token,
    query: { page, perPage, search, sortBy, sortDir }
  });
}

export function getOrganization(token: string, organizationId: string): Promise<OrganizationResponse> {
  return request<OrganizationResponse>(`/organizations/${encodeURIComponent(organizationId)}`, {
    token
  });
}

export function onboardOrganization(
  token: string,
  body: CreateOrganizationRequest
): Promise<OrganizationResponse> {
  return request<OrganizationResponse>("/organizations", { token, method: "POST", body });
}
