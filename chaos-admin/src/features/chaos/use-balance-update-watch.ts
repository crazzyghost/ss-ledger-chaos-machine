import { useSession } from "@/features/auth/session-provider";
import { listBalanceHistoryByAccounts, type BalanceHistoryResponse } from "@/lib/api";
import { appConfig } from "@/lib/env";
import { formatMoney } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { BALANCE_POLL_INTERVAL_MS, BALANCE_POLL_WINDOW_MS } from "./watch-config";

/**
 * Bounded, account-scoped poll for `ledger.balance.updated`-derived rows on the accounts a flow
 * targeted (Phase 018, ADR-027). The scope is heuristic — balance events carry no
 * `transaction_request_id` — so the copy says "Balance updated on {account}", never that the
 * operator's transaction succeeded or caused it. `since` is the publish instant (minus a skew slack)
 * as an ISO string; the caller computes it. Toasts dedupe by `event_id` and fire at most once per
 * involved account.
 */
export function useBalanceUpdateWatch(accountIds: string[], since: string | null): void {
  const { token } = useSession();
  const ids = useMemo(
    () => Array.from(new Set(accountIds.filter(id => Boolean(id && id.trim())))),
    [accountIds]
  );
  const idsKey = ids.join(",");
  const scopeKey = `${idsKey}|${since ?? ""}`;

  const deadlineRef = useRef<{ key: string; until: number }>({ key: "", until: 0 });
  if (scopeKey !== "|" && deadlineRef.current.key !== scopeKey) {
    deadlineRef.current = { key: scopeKey, until: Date.now() + BALANCE_POLL_WINDOW_MS };
  }
  const seenEventsRef = useRef<Set<string>>(new Set());
  const toastedAccountsRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    seenEventsRef.current = new Set();
    toastedAccountsRef.current = new Set();
  }, [scopeKey]);

  const enabled =
    appConfig.balanceWatchEnabled && Boolean(token) && ids.length > 0 && Boolean(since);

  const query = useQuery({
    queryKey: ["balance-update-watch", scopeKey],
    enabled,
    queryFn: async (): Promise<BalanceHistoryResponse[]> => {
      const page = await listBalanceHistoryByAccounts(token!, ids, { from: since ?? undefined });
      return page.items;
    },
    refetchInterval: () => {
      const allToasted = ids.length > 0 && ids.every(id => toastedAccountsRef.current.has(id));
      if (Date.now() >= deadlineRef.current.until || allToasted) return false;
      return BALANCE_POLL_INTERVAL_MS;
    }
  });

  const rows = useMemo(() => query.data ?? [], [query.data]);

  useEffect(() => {
    for (const row of rows) {
      if (row.eventId && seenEventsRef.current.has(row.eventId)) continue;
      if (row.eventId) seenEventsRef.current.add(row.eventId);
      // One toast per involved account (cap), so a fan-out (transfer → N accounts) doesn't spam.
      if (toastedAccountsRef.current.has(row.accountId)) continue;
      toastedAccountsRef.current.add(row.accountId);
      const currency = row.currency ?? undefined;
      toast(`Balance updated on ${row.accountId}`, {
        description: `Total ${formatMoney(row.total, currency)} · Available ${formatMoney(
          row.available,
          currency
        )}`,
        duration: 8000
      });
    }
  }, [rows]);
}
