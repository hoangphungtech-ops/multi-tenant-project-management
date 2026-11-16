import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useOrg } from "../org/OrgProvider";
import { ErrorNotice, RoleBadge } from "./Notice";

export function Layout() {
  const { logout, mode, accountLabel } = useAuth();
  const { me, current, switchOrg, loading, error } = useOrg();
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">▦ TaskCloud</div>
        {me && me.memberships.length > 0 && (
          <label className="org-switch">
            <span className="muted small">Tổ chức</span>
            <select
              value={current?.tenantId ?? ""}
              onChange={(e) => {
                switchOrg(e.target.value);
                navigate("/projects");
              }}
            >
              {me.memberships.map((m) => (
                <option key={m.tenantId} value={m.tenantId}>
                  {m.tenantName} · {m.role}
                </option>
              ))}
            </select>
          </label>
        )}
        {current && <RoleBadge role={current.role} />}
        <div className="spacer" />
        <span className="muted small" title={me?.email}>
          {me?.displayName ?? accountLabel}
          {mode === "mock" && " (mock)"}
        </span>
        <button className="btn btn-ghost" onClick={() => void logout()}>
          Đăng xuất
        </button>
      </header>
      <nav className="tabs">
        <NavLink to="/projects">Dự án & Task Board</NavLink>
        <NavLink to="/members">Thành viên</NavLink>
        <NavLink to="/audit">Lịch sử (Audit)</NavLink>
        <NavLink to="/exports">Export CSV</NavLink>
      </nav>
      <main className="content">
        <ErrorNotice message={error} />
        {loading && !me && <p className="muted">Đang tải…</p>}
        {me && me.memberships.length === 0 && (
          <div className="card">
            <h2>Chưa thuộc tổ chức nào</h2>
            <p className="muted">
              Hãy nhờ Owner của tổ chức mời bạn bằng email <b>{me.email}</b>.
            </p>
          </div>
        )}
        {current && <Outlet key={current.tenantId} />}
      </main>
    </div>
  );
}
