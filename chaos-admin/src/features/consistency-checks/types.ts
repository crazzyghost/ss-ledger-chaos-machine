// Consistency Check Types

export type ConsistencyCheckType =
  | "ACCOUNT_BALANCE_PROJECTION"
  | "ENTRY_BALANCE"
  | "SEQUENCE_INTEGRITY";

export type ConsistencyCheckStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";

export type ConsistencyCheckInitiatorType = "SYSTEM" | "PLATFORM_OPERATOR";

export interface ConsistencyCheck {
  checkId: string;
  type: ConsistencyCheckType;
  status: ConsistencyCheckStatus;
  initiatorType: ConsistencyCheckInitiatorType;
  initiatedBy?: string;
  asOf?: string;
  discrepancyCount: number;
  initiatedAt: string;
  completedAt?: string;
  erroredAt?: string;
  errorCode?: string;
}

export interface ConsistencyCheckTriggerResponse {
  checks: Array<{
    type: string;
    checkId: string;
    status: string;
  }>;
}

export interface ConsistencyCheckListResponse {
  items: ConsistencyCheck[];
  totalElements: number;
  page: number;
  size: number;
  hasNext: boolean;
}

export interface ConsistencyCheckDiscrepancy {
  id: string;
  code: string;
  accountId?: string;
  entryId?: string;
  details: Record<string, unknown>;
  detectedAt: string;
}

export interface ConsistencyCheckDiscrepancyListResponse {
  items: ConsistencyCheckDiscrepancy[];
  totalElements: number;
  page: number;
  size: number;
  hasNext: boolean;
}

export interface ReconciliationMismatch {
  id: string;
  checkId: string;
  type: ConsistencyCheckType;
  initiatorType: ConsistencyCheckInitiatorType;
  asOf: string;
  initiatedAt: string;
  completedAt: string;
  discrepancyCount: number;
  consumedAt: string;
}

export interface ReconciliationMismatchPollResponse {
  items: ReconciliationMismatch[];
  totalElements: number;
  page: number;
  size: number;
  hasNext: boolean;
  nextSince: string;
}
