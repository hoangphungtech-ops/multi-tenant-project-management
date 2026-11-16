import { ErrorNotice, fmtDate } from "../components/Notice";
import { useLoad } from "../components/useLoad";
import { useOrg } from "../org/OrgProvider";

export function AuditPage() {
  const { api, current } = useOrg();
  const tenantId = current!.tenantId;
  const { data: events, error, loading, reload } = useLoad(() => api.listAudit(tenantId, 100), [api, tenantId]);
  const members = useLoad(() => api.listMembers(tenantId), [api, tenantId]);
  const who = (id: string, email?: string) => members.data?.find((m) => m.userId === id)?.email ?? email ?? id.slice(0, 8);

  return (
    <section>
      <div className="row">
        <h2>Lịch sử hoạt động · {current!.tenantName}</h2>
        <div className="spacer" />
        <button className="btn btn-ghost" onClick={reload}>
          ↻ Làm mới
        </button>
      </div>
      <p className="muted small">Mỗi sự kiện có request_id để truy vết sang log Application Insights.</p>
      <ErrorNotice message={error} />
      {loading && <p className="muted">Đang tải…</p>}
      <table className="table">
        <thead>
          <tr>
            <th>Thời điểm</th>
            <th>Người thực hiện</th>
            <th>Hành động</th>
            <th>Đối tượng</th>
            <th>Chi tiết</th>
            <th>request_id</th>
          </tr>
        </thead>
        <tbody>
          {events?.map((e) => (
            <tr key={e.id}>
              <td className="small nowrap">{fmtDate(e.createdAt)}</td>
              <td>{who(e.actorUserId, e.actorEmail)}</td>
              <td>
                <code>{e.action}</code>
              </td>
              <td className="small">
                {e.entityType} <span className="muted">{e.entityId.slice(0, 8)}</span>
              </td>
              <td className="small">{e.details && Object.keys(e.details).length ? JSON.stringify(e.details) : "—"}</td>
              <td className="small">
                <code>{e.requestId ?? "—"}</code>
              </td>
            </tr>
          ))}
          {events?.length === 0 && (
            <tr>
              <td colSpan={6} className="muted">
                Chưa có sự kiện.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
