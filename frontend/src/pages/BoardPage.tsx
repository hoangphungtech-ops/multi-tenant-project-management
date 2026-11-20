import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { describeError } from "../api/client";
import type { Task, TaskStatus } from "../api/types";
import { ErrorNotice, GuestHint } from "../components/Notice";
import { useLoad } from "../components/useLoad";
import { useOrg } from "../org/OrgProvider";

const COLUMNS: { status: TaskStatus; label: string }[] = [
  { status: "TODO", label: "Cần làm" },
  { status: "IN_PROGRESS", label: "Đang làm" },
  { status: "DONE", label: "Hoàn thành" },
];

export function BoardPage() {
  const { projectId = "" } = useParams();
  const { api, current, canWrite } = useOrg();
  const tenantId = current!.tenantId;
  const project = useLoad(() => api.listProjects(tenantId).then((ps) => ps.find((p) => p.id === projectId)), [api, tenantId, projectId]);
  const members = useLoad(() => api.listMembers(tenantId), [api, tenantId]);
  const tasks = useLoad(() => api.listTasks(tenantId, projectId), [api, tenantId, projectId]);
  const [title, setTitle] = useState("");
  const [actionErr, setActionErr] = useState<string>();

  const nameOf = (userId?: string | null) =>
    members.data?.find((m) => m.userId === userId)?.displayName ?? (userId ? "—" : "Chưa giao");

  async function create(e: FormEvent) {
    e.preventDefault();
    try {
      const t = await api.createTask(tenantId, projectId, title.trim());
      tasks.setData([...(tasks.data ?? []), t]);
      setTitle("");
      setActionErr(undefined);
    } catch (err) {
      setActionErr(describeError(err));
    }
  }

  async function patch(task: Task, change: Partial<Task>) {
    const before = tasks.data ?? [];
    tasks.setData(before.map((t) => (t.id === task.id ? { ...t, ...change } : t))); // optimistic
    try {
      const saved = await api.updateTask(tenantId, task.id, change);
      tasks.setData((cur) => cur?.map((t) => (t.id === task.id ? saved : t)));
    } catch (err) {
      tasks.setData(before); // rollback khi API từ chối
      setActionErr(describeError(err));
    }
  }

  async function remove(task: Task) {
    if (!confirm(`Xóa task "${task.title}"?`)) return;
    try {
      await api.deleteTask(tenantId, task.id);
      tasks.setData((cur) => cur?.filter((t) => t.id !== task.id));
    } catch (err) {
      setActionErr(describeError(err));
    }
  }

  return (
    <section>
      <p className="small">
        <Link to="/projects">← Dự án</Link>
      </p>
      <h2>{project.data?.name ?? "Task Board"}</h2>
      {!canWrite && <GuestHint />}
      <ErrorNotice message={tasks.error ?? actionErr} onClose={() => setActionErr(undefined)} />
      {canWrite && !tasks.error && (
        <form className="row" onSubmit={create}>
          <input required maxLength={200} placeholder="Thêm task mới…" value={title} onChange={(e) => setTitle(e.target.value)} />
          <button className="btn btn-primary" disabled={!title.trim()}>
            + Thêm
          </button>
        </form>
      )}
      {tasks.loading && <p className="muted">Đang tải…</p>}
      {!tasks.error && (
        <div className="board">
          {COLUMNS.map((col, idx) => {
            const items = (tasks.data ?? []).filter((t) => t.status === col.status);
            return (
              <div key={col.status} className="column">
                <h3>
                  {col.label} <span className="muted">({items.length})</span>
                </h3>
                {items.map((t) => (
                  <article key={t.id} className="task">
                    <div className="task-title">{t.title}</div>
                    {canWrite ? (
                      <select
                        className="small"
                        value={t.assigneeUserId ?? ""}
                        onChange={(e) => void patch(t, { assigneeUserId: e.target.value || null })}
                      >
                        <option value="">Chưa giao</option>
                        {members.data
                          ?.filter((m) => m.role !== "GUEST")
                          .map((m) => (
                            <option key={m.userId} value={m.userId}>
                              {m.displayName}
                            </option>
                          ))}
                      </select>
                    ) : (
                      <div className="muted small">👤 {nameOf(t.assigneeUserId)}</div>
                    )}
                    {canWrite && (
                      <div className="task-actions">
                        <button className="btn btn-xs" disabled={idx === 0} onClick={() => void patch(t, { status: COLUMNS[idx - 1]?.status })}>
                          ←
                        </button>
                        <button
                          className="btn btn-xs"
                          disabled={idx === COLUMNS.length - 1}
                          onClick={() => void patch(t, { status: COLUMNS[idx + 1]?.status })}
                        >
                          →
                        </button>
                        <div className="spacer" />
                        <button className="btn btn-xs btn-danger-ghost" onClick={() => void remove(t)}>
                          Xóa
                        </button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
