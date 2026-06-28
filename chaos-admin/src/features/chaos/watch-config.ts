// Tunable cadence/window for the run-page + wizard watches (Phases 017–019). Kept as front-end
// constants (mirroring the lifecycle-wizard reservation-poll constants) so the cadence is
// adjustable without a backend redeploy. Master on/off switches live in appConfig (lib/env.ts).

export const FAILURE_POLL_INTERVAL_MS = 1500;
export const FAILURE_POLL_WINDOW_MS = 25_000;

export const BALANCE_POLL_INTERVAL_MS = 1500;
export const BALANCE_POLL_WINDOW_MS = 25_000;
// Slack subtracted from the publish instant so a balance update landing a touch early (clock skew)
// is still inside the `from` watermark.
export const BALANCE_SKEW_MS = 2000;

export const RESERVATION_POLL_INTERVAL_MS = 1500;
export const RESERVATION_POLL_WINDOW_MS = 25_000;
