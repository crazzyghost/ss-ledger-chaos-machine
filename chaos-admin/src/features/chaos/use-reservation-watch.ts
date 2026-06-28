import { useSession } from "@/features/auth/session-provider";
import { listReservations, type ReservationStateResponse } from "@/lib/api";
import { appConfig } from "@/lib/env";
import { formatMoney } from "@/lib/utils";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { RESERVATION_POLL_INTERVAL_MS, RESERVATION_POLL_WINDOW_MS } from "./watch-config";

const TERMINAL = new Set(["RELEASED", "EXPIRED", "CAPTURED"]);

/**
 * Bounded, request-id-scoped poll for reservation lifecycle transitions (Phase 019, ADR-028). After
 * publishing a reservation-creating *initiated* event, feed in that flow's request id (`transactionRef`
 * for disbursement/settlement, `batchId` for batch). Because the event carries the publisher's
 * `transactionRef`, the toast is a precise statement about *this* flow's reservation (unlike Part 2's
 * heuristic balance toast). Toasts dedupe by `(reservationId, status)` and the batch fan-out is
 * capped. Coexists with the wizard's ADR-018 read-proxy sourcing poll (this only adds toasts).
 */
export function useReservationWatch(
  ref: string | null,
  opts?: { kind?: "transactionRef" | "batchId" }
): { reservations: ReservationStateResponse[] } {
  const { token } = useSession();
  const kind = opts?.kind ?? "transactionRef";
  const scopeKey = `${kind}:${ref ?? ""}`;

  const deadlineRef = useRef<{ key: string; until: number }>({ key: "", until: 0 });
  if (ref && deadlineRef.current.key !== scopeKey) {
    deadlineRef.current = { key: scopeKey, until: Date.now() + RESERVATION_POLL_WINDOW_MS };
  }
  const toastedRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    toastedRef.current = new Set();
  }, [scopeKey]);

  const enabled = appConfig.reservationWatchEnabled && Boolean(token) && Boolean(ref);

  const query = useQuery({
    queryKey: ["reservation-watch", scopeKey],
    enabled,
    queryFn: async (): Promise<ReservationStateResponse[]> => {
      const page = await listReservations(
        token!,
        kind === "batchId" ? { batchId: ref! } : { transactionRef: ref! }
      );
      return page.items;
    },
    refetchInterval: () => {
      if (Date.now() >= deadlineRef.current.until) return false;
      return RESERVATION_POLL_INTERVAL_MS;
    }
  });

  const reservations = useMemo(() => query.data ?? [], [query.data]);

  useEffect(() => {
    for (const r of reservations) {
      const status = (r.status ?? "").toUpperCase();
      const key = `${r.reservationId}:${status}`;
      if (toastedRef.current.has(key)) continue;
      toastedRef.current.add(key);
      const currency = r.currency ?? undefined;
      if (status === "ACTIVE") {
        toast.success(`Reservation created — ${formatMoney(r.amount, currency)} held`, {
          description: `Reservation ${r.reservationId}`,
          duration: 8000
        });
      } else if (status === "PARTIALLY_RESOLVED") {
        toast(`Reservation resolving — ${r.reservationId}`, {
          description: `${r.releaseEventCount} release event(s) applied`,
          duration: 6000
        });
      } else if (TERMINAL.has(status)) {
        const verb =
          status === "CAPTURED" ? "captured" : status === "EXPIRED" ? "expired" : "released";
        toast(`Reservation ${verb}`, {
          description: `Reservation ${r.reservationId} (${formatMoney(r.amount, currency)})`,
          duration: 7000
        });
      }
    }
  }, [reservations]);

  return { reservations };
}
