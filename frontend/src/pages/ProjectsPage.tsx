import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { describeError } from "../api/client";
import { ErrorNotice, GuestHint } from "../components/Notice";
import { useLoad } from "../components/useLoad";
import { useOrg } from "../org/OrgProvider";

export function ProjectsPage() {
  const { api, current, canWrite } = useOrg();
  const tenantId = current!.tenantId;
  const { data: projects, error, loading, reload } = useLoad(() => api.listProjects(tenantId), [api, tenantId]);
  const [name, setName] = useState("");
  const [desc, setDesc] = useState("");
  const [actionErr, setActionErr] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.createProject(tenantId, name.trim(), desc.trim() || undefined);
      setName("");
      setDesc("");
      setActionErr(undefined);
      reload();
    } catch (err) {
      setActionErr(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string, pname: string) {
    if (!confirm(`Xóa dự án "${pname}" và toàn bộ task?`)) return;
    try {
      await api.deleteProject(tenantId, id);
      reload();
    } catch (err) {
      setActionErr(describeError(err));
    }
  }

  return (
    <section>
      <h2>Dự án của {current!.tenantName}</h2>
      {!canWrite && <GuestHint />}
      <ErrorNotice message={error ?? actionErr} onClose={() => setActionErr(undefined)} />
      {canWrite && (
        <form className="card row" onSubmit={create}>
          <input
            required
            maxLength={120}
            placeholder="Tên dự án (duy nhất trong tổ chức)"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <input placeholder="Mô tả (tùy chọn)" value={desc} onChange={(e) => setDesc(e.target.value)} />
          <button className="btn btn-primary" disabled={busy || !name.trim()}>
            + Tạo dự án
          </button>
        </form>
      )}
      {loading && <p className="muted">Đang tải…</p>}
      <div className="grid">
        {projects?.map((p) => (
          <div key={p.id} className="card project">
            <Link to={`/projects/${p.id}`} className="project-name">
              {p.name}
            </Link>
            <p className="muted small">{p.description || "—"}</p>
            <div className="row">
              <Link className="btn btn-ghost" to={`/projects/${p.id}`}>
                Mở Task Board →
              </Link>
              {canWrite && (
                <button className="btn btn-danger-ghost" onClick={() => void remove(p.id, p.name)}>
                  Xóa
                </button>
              )}
            </div>
          </div>
        ))}
        {projects?.length === 0 && <p className="muted">Chưa có dự án.</p>}
      </div>
    </section>
  );
}
