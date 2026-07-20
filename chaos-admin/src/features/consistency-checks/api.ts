import {appConfig} from "@/lib/env";
import type {
    ConsistencyCheck,
    ConsistencyCheckDiscrepancyListResponse,
    ConsistencyCheckListResponse,
    ConsistencyCheckTriggerResponse,
    ReconciliationMismatchPollResponse
} from "./types";
import {request} from "@/lib/api";

export async function triggerConsistencyCheck(
    token: string,
    type?: string
): Promise<ConsistencyCheckTriggerResponse> {
    return request("/ledger/consistency-checks", {
        token,
        method: "PUT",
        query: type ? {type} : undefined
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
    return request("/ledger/consistency-checks", {
        token,
        query: params
    });
}

export async function getConsistencyCheck(
    token: string,
    checkId: string
): Promise<ConsistencyCheck> {
    return request(`/ledger/consistency-checks/${checkId}`, {token});
}

export async function getConsistencyCheckDiscrepancies(
    token: string,
    checkId: string,
    params: { code?: string; page?: number; size?: number }
): Promise<ConsistencyCheckDiscrepancyListResponse> {
    return request(`/ledger/consistency-checks/${checkId}/discrepancies`, {
        token,
        query: params
    });
}

export async function pollReconciliationMismatches(
    token: string,
    since: string,
    size: number = 20
): Promise<ReconciliationMismatchPollResponse> {
    return request("/reconciliation-mismatches", {
        token,
        query: {since, size}
    });
}

export async function cancelConsistencyCheck(
    token: string,
    checkId: string
): Promise<void> {
    const url = new URL(`/ledger/consistency-checks/${checkId}`, appConfig.apiBaseUrl);
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
