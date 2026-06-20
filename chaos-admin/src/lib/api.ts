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
  createdVia: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateVirtualAccountRequest = {
  name: string;
  ownershipType: string;
  currency: string;
  organizationId?: string;
  organizationName?: string;
  channel?: string;
  status?: string;
  vaId?: string;
  announce?: boolean;
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
  status?: string;
  from?: string;
  to?: string;
};

// ---------------------------------------------------------------------------
// Flow DTOs
// ---------------------------------------------------------------------------

export type FlowCatalogEntry = {
  flowType: string;
  topic: string;
  source: string;
  requiredFields: string[];
  optionalFields: string[];
  csvColumns: string[];
  partitionKeyField: string;
};

export type DuplicateOptions = { count: number };
export type OutOfOrderOptions = { order: number[] };
export type MalformedOptions = { mutations: string[] };
export type UnbalancedOptions = { delta: number };
export type BurstOptions = { count: number; ratePerSecond: number };
export type DelayOptions = { delayMs: number; jitterMs: number };

export type ChaosOptions = {
  duplicate?: DuplicateOptions | null;
  outOfOrder?: OutOfOrderOptions | null;
  malformed?: MalformedOptions | null;
  unbalanced?: UnbalancedOptions | null;
  burst?: BurstOptions | null;
  delay?: DelayOptions | null;
};

export type PublishFlowRequest = {
  correlationId?: string | null;
  tenantId?: string | null;
  channel?: string | null;
  amount?: number | null;
  grossAmount?: number | null;
  netAmount?: number | null;
  currency?: string | null;
  slotOverrides?: Record<string, string>;
  chaos?: ChaosOptions | null;
  flowFields?: Record<string, unknown>;
};

export type FlowResult = {
  eventId: string;
  topic: string;
  partition: number;
  offset: number;
  status: string;
  historyId: string;
  error: string | null;
};

// ---------------------------------------------------------------------------
// Batch DTOs
// ---------------------------------------------------------------------------

export type BatchRunStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | string;

export type BatchRunResponse = {
  id: string;
  flowType: string;
  filename: string | null;
  total: number;
  succeeded: number;
  failed: number;
  invalid: number;
  status: BatchRunStatus;
  createdAt: string;
  completedAt: string | null;
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

// ---------------------------------------------------------------------------
// Ledger Proxy DTOs
// ---------------------------------------------------------------------------

export type LedgerAccountDto = {
  account_id: string;
  account_code: string | null;
  account_name: string | null;
  account_category: string | null;
  normal_balance: string | null;
  currency: string;
  status: string | null;
  account_ownership_type: string | null;
  organization_id: string | null;
  parent_account_id: string | null;
};

export type LedgerBalanceDto = {
  account_id: string;
  balance: number;
  available_balance: number;
  currency: string;
  updated_at: string | null;
};

export type LedgerTransactionDto = {
  transaction_id: string;
  event_id: string | null;
  event_type: string | null;
  source_va_id: string | null;
  destination_va_id: string | null;
  amount: number | null;
  currency: string | null;
  status: string | null;
  correlation_id: string | null;
  created_at: string | null;
};

export type LedgerTransactionFilters = {
  page?: number;
  size?: number;
  vaId?: string;
  eventType?: string;
  correlationId?: string;
  status?: string;
  from?: string;
  to?: string;
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
): Promise<VirtualAccountResponse> {
  return request<VirtualAccountResponse>("/virtual-accounts", {
    token,
    method: "POST",
    body
  });
}

export function announceVirtualAccount(token: string, vaId: string): Promise<void> {
  return request<void>(`/virtual-accounts/${encodeURIComponent(vaId)}/publish`, {
    token,
    method: "POST"
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

// ---------------------------------------------------------------------------
// API functions — Ledger Proxy
// ---------------------------------------------------------------------------

export function getLedgerAccount(
  token: string,
  vaId: string
): Promise<LedgerAccountDto> {
  return request<LedgerAccountDto>(`/ledger/accounts/${encodeURIComponent(vaId)}`, { token });
}

export function getLedgerAccountBalances(
  token: string,
  vaId: string
): Promise<LedgerBalanceDto> {
  return request<LedgerBalanceDto>(
    `/ledger/accounts/${encodeURIComponent(vaId)}/balances`,
    { token }
  );
}

export function listLedgerTransactions(
  token: string,
  filters: LedgerTransactionFilters = {}
): Promise<PageResponse<LedgerTransactionDto>> {
  const { page = 0, size = 20, ...rest } = filters;
  return request<PageResponse<LedgerTransactionDto>>("/ledger/transactions", {
    token,
    query: { page, size, ...rest }
  });
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

// ---------------------------------------------------------------------------
// API functions — Batches
// ---------------------------------------------------------------------------

export function startBatch(token: string, formData: FormData): Promise<BatchRunResponse> {
  return request<BatchRunResponse>("/batches", {
    token,
    method: "POST",
    formData
  });
}

export function listBatches(
  token: string,
  params: { page?: number; size?: number } = {}
): Promise<PageResponse<BatchRunResponse>> {
  const { page = 0, size = 20 } = params;
  return request<PageResponse<BatchRunResponse>>("/batches", {
    token,
    query: { page, size }
  });
}

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
  return status === "COMPLETED" || status === "FAILED" || status === "CANCELLED";
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
