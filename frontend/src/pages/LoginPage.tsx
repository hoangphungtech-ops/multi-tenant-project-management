import { useEffect, useState } from "react";
import { config } from "../config";
import { useAuth } from "../auth/AuthProvider";
import { ErrorNotice } from "../components/Notice";

interface MockUser {
  token: string;
  email: string;
  displayName: string;
}

export function LoginPage() {
  const { mode, login, status, error } = useAuth();
  const [users, setUsers] = useState<MockUser[]>([]);
  const [loadErr, setLoadErr] = useState<string>();

  useEffect(() => {
    if (mode !== "mock") return;
    fetch(config.apiBaseUrl + "/mock/users")
      .then((r) => r.json())
      .then(setUsers)
      .catch(() => setLoadErr("Không gọi được mock API — chạy: python tests/mock_api/server.py"));
  }, [mode]);

  return (
    <div className="login">
      <div className="card login-card">
        <h1>▦ TaskCloud</h1>
        <p className="muted">Quản lý dự án đa tổ chức (Multi-Tenant SaaS) trên Microsoft Azure</p>
        <ErrorNotice message={error ?? loadErr} />
        {mode === "msal" ? (
          <button className="btn btn-primary btn-block" disabled={status === "loading"} onClick={() => void login()}>
            Đăng nhập với Microsoft Entra External ID
          </button>
        ) : (
          <>
            <p className="small">
              <b>Chế độ mock (dev local)</b> — chọn một user mẫu:
            </p>
            <div className="mock-users">
              {users.map((u) => (
                <button key={u.token} className="btn btn-ghost" onClick={() => void login(u.token)}>
                  <b>{u.displayName}</b>
                  <br />
                  <span className="muted small">{u.email}</span>
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
