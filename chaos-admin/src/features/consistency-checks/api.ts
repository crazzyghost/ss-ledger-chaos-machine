import { appConfig } from "@/lib/env";
import type {
  ConsistencyCheck,
  ConsistencyCheckDiscrepancyListResponse,
  ConsistencyCheckListResponse,
  ConsistencyCheckTriggerResponse,
  ReconciliationMismatchPollResponse
} from "./types";

async function request<T>(
  path: string,
  options?: {
    token?: string;
    method?: string;
    body?: unknown;
    query?: Record<string, unknown>;
  }
): Promise<T> {
  const url = new URL(path, appConfig.apiBaseUrl);
  if (options?.query) {
    Object.entries(options.query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    });
  }

  const headers: Record<string, string> = {};
  if (options?.token) headers["Authorization"] = `Bearer ${options.token}`;
  if (options?.body) headers["Content-Type"] = "application/json";

  const response = await fetch(url.toString(), {
    method: options?.method ?? "GET",
    headers,
    body: options?.body ? JSON.stringify(options.body) : undefined
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed with status ${response.status}`);
  }

  return response.json();
}

export async function triggerConsistencyCheck(
  token: string,
  type?: string
): Promise<ConsistencyCheckTriggerResponse> {
  return request("/api/v0/ledger/consistency-checks", {
    token,
    method: "PUT",
    query: type ? { type } : undefined
  });
}

export async function getConsistencyChecks(
  token: string,
  params: {
    type?: string;
    status?: string;
    initiatorType?: string;
    page?: number;
    size?: number;
  }
): Promise<ConsistencyCheckListResponse> {
  return request("/api/v0/ledger/consistency-checks", {
    token,
    query: params
  });
}

export async function getConsistencyCheck(
  token: string,
  checkId: string
): Promise<ConsistencyCheck> {
  return request(`/api/v0/ledger/consistency-checks/${checkId}`, { token });
}

export async function getConsistencyCheckDiscrepancies(
  token: string,
  checkId: string,
  params: { code?: string; page?: number; size?: number }
): Promise<ConsistencyCheckDiscrepancyListResponse> {
  return request(`/api/v0/ledger/consistency-checks/${checkId}/discrepancies`, {
    token,
    query: params
  });
}

export async function pollReconciliationMismatches(
  token: string,
  since: string,
  size: number = 20
): Promise<ReconciliationMismatchPollResponse> {
  return request("/api/v0/reconciliation-mismatches", {
    token,
    query: { since, size }
  });
}

export async function cancelConsistencyCheck(
  token: string,
  checkId: string
): Promise<void> {
  const url = new URL(`/api/v0/ledger/consistency-checks/${checkId}`, appConfig.apiBaseUrl);
  const response = await fetch(url.toString(), {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Failed to cancel check: ${response.status}`);
  }
}
