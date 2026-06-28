import { useSession } from "@/features/auth/session-provider";
import {
  getTransactionFailureByRequestId,
  listTransactionFailuresByRequestIds,
  type TransactionFailureResponse
} from "@/lib/api";
import { appConfig } from "@/lib/env";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { FAILURE_POLL_INTERVAL_MS, FAILURE_POLL_WINDOW_MS } from "./watch-config";

/**
 * Bounded, request-id-scoped poll for ledger transaction failures (Phase 017, ADR-026). After a
 * publish, feed the emitted `transactionRequestId`(s) in; while the page is mounted this polls the
 * failures API and fires a single danger toast per failed request id. A clean window means
 * "no failure observed" — never a success guarantee (failures are asynchronous and the window is
 * finite). Non-transactional flows pass `[]` and arm nothing.
 */
export function useTransactionFailureWatch(requestIds: Array<string | null>): {
  failures: TransactionFailureResponse[];
} {
  const { token } = useSession();
  const ids = useMemo(
    () => Array.from(new Set(requestIds.filter((id): id is string => Boolean(id && id.trim())))),
    [requestIds]
  );
  const idsKey = ids.join(",");

  // Deadline + dedupe scoped to the current id set; reset when a fresh publish changes the ids.
  const deadlineRef = useRef<{ key: string; until: number }>({ key: "", until: 0 });
  if (idsKey && deadlineRef.current.key !== idsKey) {
    deadlineRef.current = { key: idsKey, until: Date.now() + FAILURE_POLL_WINDOW_MS };
  }
  const toastedRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    toastedRef.current = new Set();
  }, [idsKey]);

  const enabled = appConfig.failureWatchEnabled && Boolean(token) && ids.length > 0;

  const query = useQuery({
    queryKey: ["transaction-failure-watch", idsKey],
    enabled,
    queryFn: async (): Promise<TransactionFailureResponse[]> => {
      const page =
        ids.length === 1
          ? await getTransactionFailureByRequestId(token!, ids[0])
          : await listTransactionFailuresByRequestIds(token!, ids);
      return page.items;
    },
    refetchInterval: query => {
      const data = (query.state.data as TransactionFailureResponse[] | undefined) ?? [];
      const allResolved = ids.length > 0 && ids.every(id => data.some(f => f.transactionRequestId === id));
      if (Date.now() >= deadlineRef.current.until || allResolved) return false;
      return FAILURE_POLL_INTERVAL_MS;
    }
  });

  const failures = useMemo(() => query.data ?? [], [query.data]);

  useEffect(() => {
    for (const failure of failures) {
      const key = failure.transactionRequestId;
      if (!key || toastedRef.current.has(key)) continue;
      toastedRef.current.add(key);
      toast.error(`Failed at ledger${failure.failureCode ? `: ${failure.failureCode}` : ""}`, {
        description: failure.failureReason ?? "The ledger rejected this transaction.",
        duration: 10_000
      });
    }
  }, [failures]);

  return { failures };
}
