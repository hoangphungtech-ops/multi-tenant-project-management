import { useState, type FormEvent } from "react";
import { describeError } from "../api/client";
import type { Role } from "../api/types";
import { ErrorNotice, RoleBadge, fmtDate } from "../components/Notice";
import { useLoad } from "../components/useLoad";
import { useOrg } from "../org/OrgProvider";

const ROLES: Role[] = ["OWNER", "MEMBER", "GUEST"];

export function MembersPage() {
  const { api, current, isOwner, me, refresh } = useOrg();
  const tenantId = current!.tenantId;
  const { data: members, error, loading, reload } = useLoad(() => api.listMembers(tenantId), [api, tenantId]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("MEMBER");
  const [actionErr, setActionErr] = useState<string>();
  const [info, setInfo] = useState<string>();

  async function run(fn: () => Promise<unknown>, ok: string) {
    try {
      await fn();
      setActionErr(undefined);
      setInfo(ok);
      reload();
    } catch (err) {
      setInfo(undefined);
      setActionErr(describeError(err));
    }
  }

  async function invite(e: FormEvent) {
    e.preventDefault();
    await run(() => api.addMember(tenantId, email.trim(), role), `Đã mời ${email.trim()} với vai trò ${role}`);
    setEmail("");
  }

  return (
    <section>
      <h2>Thành viên · {current!.tenantName}</h2>
      <p className="muted small">
        Chỉ <b>Owner</b> được mời, đổi vai trò, xóa thành viên. Thay đổi có hiệu lực ngay ở request kế tiếp vì API kiểm tra bảng
        memberships mỗi lần gọi.
      </p>
      <ErrorNotice message={error ?? actionErr} onClose={() => setActionErr(undefined)} />
      {info && <div className="notice notice-ok">{info}</div>}
      {isOwner && (
        <form className="card row" onSubmit={invite}>
          <input type="email" required placeholder="email@congty.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
          <button className="btn btn-primary">Mời thành viên</button>
        </form>
      )}
      {loading && <p className="muted">Đang tải…</p>}
      <table className="table">
        <thead>
          <tr>
            <th>Tên</th>
            <th>Email</th>
            <th>Vai trò</th>
            <th>Tham gia</th>
            {isOwner && <th />}
          </tr>
        </thead>
        <tbody>
          {members?.map((m) => {
            const self = m.userId === me?.userId;
            return (
              <tr key={m.userId}>
                <td>
                  {m.displayName}
                  {self && <span className="muted"> (bạn)</span>}
                </td>
                <td>{m.email}</td>
                <td>
                  {isOwner && !self ? (
                    <select
                      value={m.role}
                      onChange={(e) =>
                        void run(() => api.changeRole(tenantId, m.userId, e.target.value as Role), `Đã đổi vai trò ${m.email} → ${e.target.value}`)
                      }
                    >
                      {ROLES.map((r) => (
                        <option key={r}>{r}</option>
                      ))}
                    </select>
                  ) : (
                    <RoleBadge role={m.role} />
                  )}
                </td>
                <td className="small">{fmtDate(m.joinedAt)}</td>
                {isOwner && (
                  <td>
                    {!self && (
                      <button
                        className="btn btn-xs btn-danger-ghost"
                        onClick={() => {
                          if (confirm(`Thu hồi quyền của ${m.email}?`))
                            void run(() => api.removeMember(tenantId, m.userId), `Đã thu hồi quyền của ${m.email}`).then(refresh);
                        }}
                      >
                        Thu hồi
                      </button>
                    )}
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
