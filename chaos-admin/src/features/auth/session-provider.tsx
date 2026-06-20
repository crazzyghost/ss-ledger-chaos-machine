import { getMe, getHealth, registerUnauthorizedHandler, type UserInfoResponse } from "@/lib/api";
import { clearToken, getStoredSession, isTokenExpired, setToken } from "@/lib/auth";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";

type SessionContextValue = {
  token: string | null;
  principal: UserInfoResponse | null;
  loading: boolean;
  clusterLabel: string | null;
  signIn: (token: string, expiresIn?: number | null) => void;
  signOut: () => void;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: PropsWithChildren) {
  const [token, setTokenState] = useState<string | null>(null);
  const [principal, setPrincipal] = useState<UserInfoResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [clusterLabel, setClusterLabel] = useState<string | null>(null);

  const signOut = useCallback(() => {
    clearToken();
    setTokenState(null);
    setPrincipal(null);
  }, []);

  // Register central 401 handler once
  useEffect(() => {
    registerUnauthorizedHandler(signOut);
  }, [signOut]);

  // Hydrate from storage on mount
  useEffect(() => {
    const stored = getStoredSession();
    if (!stored || isTokenExpired(stored.token, stored.expiresAt)) {
      clearToken();
      setLoading(false);
      return;
    }

    setTokenState(stored.token);

    getMe(stored.token)
      .then(info => setPrincipal(info))
      .catch(() => {
        // Token may be invalid; clear session
        clearToken();
        setTokenState(null);
      })
      .finally(() => setLoading(false));
  }, []);

  // Fetch cluster label once we have a token
  useEffect(() => {
    if (!token) {
      setClusterLabel(null);
      return;
    }
    getHealth(token)
      .then(h => setClusterLabel(h.clusterLabel ?? null))
      .catch(() => setClusterLabel(null));
  }, [token]);

  const signIn = useCallback((newToken: string, expiresIn?: number | null) => {
    setToken(newToken, expiresIn);
    setTokenState(newToken);
    setLoading(true);
    getMe(newToken)
      .then(info => setPrincipal(info))
      .catch(() => setPrincipal(null))
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo(
    () => ({ token, principal, loading, clusterLabel, signIn, signOut }),
    [token, principal, loading, clusterLabel, signIn, signOut]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return context;
}
