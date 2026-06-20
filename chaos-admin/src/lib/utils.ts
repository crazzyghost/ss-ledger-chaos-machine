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
