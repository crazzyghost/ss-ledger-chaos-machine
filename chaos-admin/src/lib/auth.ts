const TOKEN_STORAGE_KEY = "chaos.token";
const TOKEN_EXPIRES_AT_STORAGE_KEY = "chaos.token.expiresAt";

export type JwtPayload = {
  exp?: number;
  email?: string;
  sub?: string;
  [key: string]: unknown;
};

export type StoredSession = {
  token: string;
  expiresAt: string | null;
};

export function decodeJwtPayload(token: string | undefined | null): JwtPayload | null {
  if (!token) return null;
  try {
    const [, payload] = token.split(".");
    if (!payload) return null;
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))) as JwtPayload;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function getTokenExpiresAt(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_EXPIRES_AT_STORAGE_KEY);
}

export function setToken(token: string, expiresInSeconds?: number | null): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
  if (typeof expiresInSeconds === "number" && Number.isFinite(expiresInSeconds) && expiresInSeconds > 0) {
    window.localStorage.setItem(
      TOKEN_EXPIRES_AT_STORAGE_KEY,
      new Date(Date.now() + expiresInSeconds * 1000).toISOString()
    );
  } else {
    window.localStorage.removeItem(TOKEN_EXPIRES_AT_STORAGE_KEY);
  }
}

export function clearToken(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(TOKEN_EXPIRES_AT_STORAGE_KEY);
}

export function getStoredSession(): StoredSession | null {
  const token = getToken();
  if (!token) return null;
  return { token, expiresAt: getTokenExpiresAt() };
}

export function isTokenExpired(
  token: string | null | undefined,
  expiresAt: string | null | undefined
): boolean {
  if (!token) return true;
  if (expiresAt) {
    const ms = Date.parse(expiresAt);
    if (!Number.isNaN(ms)) return Date.now() >= ms - 30_000;
  }
  const payload = decodeJwtPayload(token);
  if (typeof payload?.exp === "number") {
    return Date.now() >= payload.exp * 1000 - 30_000;
  }
  return false;
}
