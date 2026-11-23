import { useEffect, useState } from "react";
import { describeError } from "../api/client";
import type { ExportJob } from "../api/types";
import { ErrorNotice, GuestHint, fmtDate } from "../components/Notice";
import { useLoad } from "../components/useLoad";
import { useOrg } from "../org/OrgProvider";

// Trạng thái kết thúc; mọi trạng thái khác (PENDING/QUEUED/PROCESSING/…) coi là đang chạy và tiếp tục poll.
const TERMINAL = new Set(["COMPLETED", "FAILED", "REJECTED"]);

export function ExportsPage() {
  const { api, current, canWrite } = useOrg();
  const tenantId = current!.tenantId;
  const jobs = useLoad(() => api.listExports(tenantId), [api, tenantId]);
  const projects = useLoad(() => api.listProjects(tenantId), [api, tenantId]);
  const [projectId, setProjectId] = useState("");
  const [actionErr, setActionErr] = useState<string>();

  // Poll khi còn job đang chạy (Worker xử lý bất đồng bộ qua Azure Queue)
  const hasActive = jobs.data?.some((j) => !TERMINAL.has(j.status));
  useEffect(() => {
    if (!hasActive) return;
    const timer = setInterval(() => {
      api.listExports(tenantId).then(jobs.setData).catch(() => undefined);
    }, 2000);
    return () => clearInterval(timer);
  }, [hasActive, api, tenantId, jobs.setData]);

  async function create() {
    try {
      const job = await api.createExport(tenantId, projectId || undefined);
      jobs.setData((cur) => [{ ...job, projectId: projectId || null, createdAt: new Date().toISOString() }, ...(cur ?? [])]);
      setActionErr(undefined);
    } catch (err) {
      setActionErr(describeError(err));
    }
  }

  async function download(job: ExportJob) {
    try {
      // API (TV2) sinh SAS read-only ngắn hạn; UI không lưu, không log URL này.
      const { url } = await api.downloadLink(tenantId, job.jobId);
      const a = document.createElement("a");
      a.href = url;
      a.rel = "noopener noreferrer";
      a.download = `export-${job.jobId}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch (err) {
      setActionErr(describeError(err));
    }
  }

  const projectName = (id?: string | null) => (id ? projects.data?.find((p) => p.id === id)?.name ?? id.slice(0, 8) : "Tất cả dự án");

  return (
    <section>
      <h2>Export CSV · {current!.tenantName}</h2>
      <p className="muted small">
        Export chạy bất đồng bộ: API ghi <code>export_jobs</code> → đẩy message vào Azure Queue → Worker kiểm tra tenant & membership → ghi
        CSV vào Blob private <code>exports/{"{tenant_id}"}/{"{job_id}"}.csv</code> → tải qua SAS ngắn hạn.
      </p>
      {!canWrite && <GuestHint />}
      <ErrorNotice message={jobs.error ?? actionErr} onClose={() => setActionErr(undefined)} />
      {canWrite && (
        <div className="card row">
          <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            <option value="">Tất cả dự án</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <button className="btn btn-primary" onClick={() => void create()}>
            Tạo export
          </button>
        </div>
      )}
      <table className="table">
        <thead>
          <tr>
            <th>Job</th>
            <th>Phạm vi</th>
            <th>Trạng thái</th>
            <th>Tạo lúc</th>
            <th>Xong lúc</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {jobs.data?.map((j) => (
            <tr key={j.jobId}>
              <td>
                <code>{j.jobId.slice(0, 8)}</code>
              </td>
              <td>{projectName(j.projectId)}</td>
              <td>
                <span className={`status status-${j.status.toLowerCase()}`}>{j.status}</span>
                {j.errorCode && <span className="muted small"> {j.errorCode}</span>}
              </td>
              <td className="small">{fmtDate(j.createdAt)}</td>
              <td className="small">{fmtDate(j.completedAt)}</td>
              <td>
                {j.status === "COMPLETED" && canWrite && (
                  <button className="btn btn-xs" onClick={() => void download(j)}>
                    ⬇ Tải CSV
                  </button>
                )}
              </td>
            </tr>
          ))}
          {jobs.data?.length === 0 && (
            <tr>
              <td colSpan={6} className="muted">
                Chưa có export.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
