/**
 * Đăng nhập:
 *  - msal : Microsoft Entra External ID (OIDC Authorization Code + PKCE). Token chỉ lưu trong sessionStorage
 *           do MSAL quản lý; app không tự lưu/log token.
 *  - mock : chọn user giả lập của tests/mock_api (chỉ dùng dev local).
 */
import {
  InteractionRequiredAuthError,
  PublicClientApplication,
  type AccountInfo,
} from "@azure/msal-browser";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { config } from "../config";

type Status = "loading" | "signedOut" | "signedIn" | "error";

interface AuthContextValue {
  mode: "msal" | "mock";
  status: Status;
  error?: string;
  accountLabel?: string;
  login: (mockToken?: string) => Promise<void>;
  logout: () => Promise<void>;
  getToken: () => Promise<string>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const MOCK_KEY = "mtpm.mockToken";

let pca: PublicClientApplication | null = null;
function getPca(): PublicClientApplication {
  if (!pca) {
    const host = config.entra.authority ? new URL(config.entra.authority).host : "";
    pca = new PublicClientApplication({
      auth: {
        clientId: config.entra.clientId,
        authority: config.entra.authority,
        knownAuthorities: host ? [host] : [],
        redirectUri: window.location.origin,
        postLogoutRedirectUri: window.location.origin,
      },
      cache: { cacheLocation: "sessionStorage" },
      system: { loggerOptions: { piiLoggingEnabled: false } },
    });
  }
  return pca;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [error, setError] = useState<string>();
  const [account, setAccount] = useState<AccountInfo | null>(null);
  const [mockToken, setMockToken] = useState<string | null>(null);

  useEffect(() => {
    if (config.authMode === "mock") {
      const t = sessionStorage.getItem(MOCK_KEY);
      setMockToken(t);
      setStatus(t ? "signedIn" : "signedOut");
      return;
    }
    if (!config.entra.clientId || !config.entra.authority) {
      setError("Thiếu VITE_ENTRA_CLIENT_ID / VITE_ENTRA_AUTHORITY");
      setStatus("error");
      return;
    }
    (async () => {
      try {
        const app = getPca();
        await app.initialize();
        const result = await app.handleRedirectPromise();
        const acc = result?.account ?? app.getAllAccounts()[0] ?? null;
        if (acc) app.setActiveAccount(acc);
        setAccount(acc);
        setStatus(acc ? "signedIn" : "signedOut");
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
        setStatus("error");
      }
    })();
  }, []);

  const login = useCallback(async (token?: string) => {
    if (config.authMode === "mock") {
      if (!token) return;
      sessionStorage.setItem(MOCK_KEY, token);
      setMockToken(token);
      setStatus("signedIn");
      return;
    }
    await getPca().loginRedirect({ scopes: [config.entra.apiScope], prompt: "select_account" });
  }, []);

  const logout = useCallback(async () => {
    localStorage.removeItem("mtpm.currentTenant");
    if (config.authMode === "mock") {
      sessionStorage.removeItem(MOCK_KEY);
      setMockToken(null);
      setStatus("signedOut");
      return;
    }
    await getPca().logoutRedirect({ account: account ?? undefined });
  }, [account]);

  const getToken = useCallback(async () => {
    if (config.authMode === "mock") {
      if (!mockToken) throw new Error("Chưa đăng nhập");
      return mockToken;
    }
    const app = getPca();
    const acc = app.getActiveAccount() ?? account;
    if (!acc) throw new Error("Chưa đăng nhập");
    try {
      const r = await app.acquireTokenSilent({ scopes: [config.entra.apiScope], account: acc });
      return r.accessToken;
    } catch (e) {
      if (e instanceof InteractionRequiredAuthError) {
        await app.acquireTokenRedirect({ scopes: [config.entra.apiScope], account: acc });
      }
      throw e;
    }
  }, [account, mockToken]);

  const value = useMemo<AuthContextValue>(() => ({
    mode: config.authMode, status, error,
    accountLabel: account?.username || account?.name,
    login, logout, getToken,
  }), [status, error, account, login, logout, getToken]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải nằm trong AuthProvider");
  return ctx;
}
