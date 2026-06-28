declare global {
  interface Window {
    __CHAOS_ADMIN_CONFIG__?: Partial<AppConfig>;
  }
}

export type AppConfig = {
  apiBaseUrl: string;
  // Run-page watch master switches (default on). Set to false via the runtime config to disable a
  // watch without a rebuild (Phases 017–019).
  failureWatchEnabled: boolean;
  balanceWatchEnabled: boolean;
  reservationWatchEnabled: boolean;
};

function getConfigValue(...values: Array<string | undefined>) {
  return values.map(value => value?.trim()).find(value => Boolean(value)) ?? "";
}

function getBool(value: unknown, fallback: boolean): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") return value.trim().toLowerCase() !== "false";
  return fallback;
}

const runtimeConfig =
  typeof window !== "undefined" ? window.__CHAOS_ADMIN_CONFIG__ : undefined;

export const appConfig: AppConfig = {
  apiBaseUrl: getConfigValue(
    runtimeConfig?.apiBaseUrl,
    import.meta.env.VITE_CHAOS_API_BASE_URL
  ),
  failureWatchEnabled: getBool(runtimeConfig?.failureWatchEnabled, true),
  balanceWatchEnabled: getBool(runtimeConfig?.balanceWatchEnabled, true),
  reservationWatchEnabled: getBool(runtimeConfig?.reservationWatchEnabled, true)
};

export function getMissingConfigKeys(): string[] {
  return appConfig.apiBaseUrl ? [] : ["VITE_CHAOS_API_BASE_URL"];
}
