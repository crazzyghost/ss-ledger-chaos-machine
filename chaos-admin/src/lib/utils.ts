import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import type { BadgeVariant } from "@/components/ui/badge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatMoney(
  amount: number | string | null | undefined,
  currency = "GHS"
): string {
  const num = typeof amount === "string" ? parseFloat(amount) : amount;
  if (num === null || num === undefined || Number.isNaN(num)) return "-";

  const normalized = currency?.trim().toUpperCase() ?? "GHS";
  const curr = normalized === "GHC" ? "GHS" : normalized;

  try {
    return new Intl.NumberFormat("en-GH", {
      style: "currency",
      currency: curr,
      currencyDisplay: "code",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(num);
  } catch {
    return `${curr} ${num.toFixed(2)}`;
  }
}

export function formatDate(value: string | Date | null | undefined): string {
  if (!value) return "-";
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "-";
  return new Intl.DateTimeFormat("en-GH", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(d);
}

export function toTitleCase(value: string): string {
  return value
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, letter => letter.toUpperCase());
}

export function formatEnumValue(value: string | null | undefined): string {
  if (!value) return "-";
  return value
    .toLowerCase()
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, letter => letter.toUpperCase());
}

export function getStatusBadgeVariant(status: string | null | undefined): BadgeVariant {
  switch (status?.toUpperCase()) {
    case "ACTIVE":
    case "PUBLISHED":
    case "SUCCEEDED":
    case "COMPLETED":
    case "UP":
      return "success";
    case "FAILED":
    case "ERROR":
    case "DOWN":
      return "destructive";
    case "PENDING":
    case "RUNNING":
    case "QUEUED":
      return "warning";
    case "INACTIVE":
    case "SUSPENDED":
    case "INVALID":
      return "neutral";
    default:
      return "secondary";
  }
}

export function getEnumBadgeVariant(value: string | null | undefined): BadgeVariant {
  return getStatusBadgeVariant(value ?? "");
}

/**
 * Color-codes a ledger entry type / entry-line type (the {@code EntryTypeEnum} values). Grouped by
 * economic meaning: inflows green, outflows red, transfers/settlement neutral-ish, fees amber,
 * reversals/adjustments distinct.
 */
export function getEntryTypeVariant(value: string | null | undefined): BadgeVariant {
  switch (value?.toUpperCase()) {
    case "COLLECTION":
    case "TOPUP":
    case "TREASURY_PREFUND":
      return "success";
    case "DISBURSEMENT":
    case "TREASURY_SWEEP":
      return "destructive";
    case "FEE":
      return "warning";
    case "REVERSAL":
      return "outline";
    case "ADJUSTMENT":
      return "neutral";
    case "TRANSFER":
    case "INTER_VA_TRANSFER":
    case "TREASURY_TRANSFER":
      return "secondary";
    case "SETTLEMENT":
      return "default";
    default:
      return "neutral";
  }
}

/**
 * Color-codes a journal-entry-line direction. CREDIT (typically funds-in for a normal-credit VA)
 * reads as positive/green, DEBIT as a neutral movement.
 */
export function getDirectionVariant(direction: string | null | undefined): BadgeVariant {
  switch (direction?.toUpperCase()) {
    case "CREDIT":
      return "success";
    case "DEBIT":
      return "neutral";
    default:
      return "secondary";
  }
}

/**
 * Color-codes an account category (ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE / CONTRA),
 * falling back to a variant derived from the ownership type when the category is unknown.
 */
export function getAccountCategoryVariant(
  category?: string | null,
  ownershipType?: string | null
): BadgeVariant {
  switch (category?.toUpperCase()) {
    case "ASSET":
      return "success";
    case "LIABILITY":
      return "warning";
    case "EQUITY":
      return "secondary";
    case "REVENUE":
      return "destructive";
    case "EXPENSE":
      return "outline";
    case "CONTRA":
      return "neutral";
    default:
      return ownershipType?.toUpperCase() === "SYSTEM" ? "destructive" : "secondary";
  }
}
