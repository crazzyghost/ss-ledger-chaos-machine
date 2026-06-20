declare global {
  interface Window {
    __CHAOS_ADMIN_CONFIG__?: Partial<AppConfig>;
  }
}

export type AppConfig = {
  apiBaseUrl: string;
};

function getConfigValue(...values: Array<string | undefined>) {
  return values.map(value => value?.trim()).find(value => Boolean(value)) ?? "";
}

const runtimeConfig =
  typeof window !== "undefined" ? window.__CHAOS_ADMIN_CONFIG__ : undefined;

export const appConfig: AppConfig = {
  apiBaseUrl: getConfigValue(
    runtimeConfig?.apiBaseUrl,
    import.meta.env.VITE_CHAOS_API_BASE_URL
  )
};

export function getMissingConfigKeys(): string[] {
  return appConfig.apiBaseUrl ? [] : ["VITE_CHAOS_API_BASE_URL"];
}
