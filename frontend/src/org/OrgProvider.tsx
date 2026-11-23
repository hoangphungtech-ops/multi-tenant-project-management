/** Tổ chức hiện tại (Chuyển Org). Danh sách org lấy từ /me — tức bảng memberships, không từ JWT. */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { createApi, describeError, type Api } from "../api/client";
import type { Me, Membership } from "../api/types";
import { useAuth } from "../auth/AuthProvider";

interface OrgContextValue {
  api: Api;
  me: Me | null;
  current: Membership | null;
  canWrite: boolean;
  isOwner: boolean;
  loading: boolean;
  error?: string;
  switchOrg: (tenantId: string) => void;
  refresh: () => Promise<void>;
}

const OrgContext = createContext<OrgContextValue | null>(null);
const KEY = "mtpm.currentTenant";

export function OrgProvider({ children }: { children: ReactNode }) {
  const { getToken } = useAuth();
  const api = useMemo(() => createApi(getToken), [getToken]);
  const [me, setMe] = useState<Me | null>(null);
  const [tenantId, setTenantId] = useState<string | null>(() => localStorage.getItem(KEY));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const m = await api.me();
      setMe(m);
      setError(undefined);
      setTenantId((prev) => (m.memberships.some((x) => x.tenantId === prev) ? prev : m.memberships[0]?.tenantId ?? null));
    } catch (e) {
      setError(describeError(e));
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (tenantId) localStorage.setItem(KEY, tenantId); }, [tenantId]);

  const current = me?.memberships.find((m) => m.tenantId === tenantId) ?? null;
  const value: OrgContextValue = {
    api, me, current, loading, error, refresh,
    canWrite: current?.role === "OWNER" || current?.role === "MEMBER",
    isOwner: current?.role === "OWNER",
    switchOrg: setTenantId,
  };
  return <OrgContext.Provider value={value}>{children}</OrgContext.Provider>;
}

export function useOrg(): OrgContextValue {
  const ctx = useContext(OrgContext);
  if (!ctx) throw new Error("useOrg phải nằm trong OrgProvider");
  return ctx;
}
