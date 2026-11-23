/**
 * API client. Đường dẫn endpoint theo docs/tv4/API_CONTRACT_PROPOSAL.ipynb — khi TV2 chốt OpenAPI chỉ sửa file này.
 * Bảo mật: UI chỉ ẩn nút theo role cho tiện; quyền thật luôn do API kiểm tra (403/404).
 */
import { config } from "../config";
import type { AuditEvent, DownloadLink, ExportJob, Me, Member, Project, Role, Task } from "./types";

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public requestId?: string) {
    super(message);
  }
}

export type TokenProvider = () => Promise<string>;

function newRequestId(): string {
  return "web-" + (crypto.randomUUID?.() ?? Math.random().toString(36).slice(2));
}

export function createApi(getToken: TokenProvider) {
  async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
    const token = await getToken();
    const requestId = newRequestId();
    let res: Response;
    try {
      res = await fetch(config.apiBaseUrl + "/api/v1" + path, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
          "X-Request-Id": requestId,
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new ApiError(0, "NETWORK", "Không kết nối được API", requestId);
    }
    const rid = res.headers.get("X-Request-Id") ?? requestId;
    if (!res.ok) {
      let code = String(res.status), message = res.statusText;
      try {
        const err = await res.json();
        code = err.error ?? code;
        message = err.message ?? message;
      } catch { /* body không phải JSON */ }
      if (res.status === 403) message = message || "Bạn không có quyền thực hiện thao tác này";
      throw new ApiError(res.status, code, message, rid);
    }
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }

  const org = (t: string) => `/orgs/${encodeURIComponent(t)}`;
  const id = encodeURIComponent;

  return {
    me: () => call<Me>("GET", "/me"),

    listMembers: (t: string) => call<Member[]>("GET", `${org(t)}/members`),
    addMember: (t: string, email: string, role: Role) => call<Member>("POST", `${org(t)}/members`, { email, role }),
    changeRole: (t: string, userId: string, role: Role) => call<Member>("PATCH", `${org(t)}/members/${id(userId)}`, { role }),
    removeMember: (t: string, userId: string) => call<void>("DELETE", `${org(t)}/members/${id(userId)}`),

    listProjects: (t: string) => call<Project[]>("GET", `${org(t)}/projects`),
    createProject: (t: string, name: string, description?: string) =>
      call<Project>("POST", `${org(t)}/projects`, { name, description }),
    deleteProject: (t: string, projectId: string) => call<void>("DELETE", `${org(t)}/projects/${id(projectId)}`),

    listTasks: (t: string, projectId: string) => call<Task[]>("GET", `${org(t)}/projects/${id(projectId)}/tasks`),
    createTask: (t: string, projectId: string, title: string, description?: string) =>
      call<Task>("POST", `${org(t)}/projects/${id(projectId)}/tasks`, { title, description }),
    updateTask: (t: string, taskId: string, patch: Partial<Pick<Task, "title" | "description" | "status" | "assigneeUserId">>) =>
      call<Task>("PATCH", `${org(t)}/tasks/${id(taskId)}`, patch),
    deleteTask: (t: string, taskId: string) => call<void>("DELETE", `${org(t)}/tasks/${id(taskId)}`),

    listAudit: (t: string, limit = 50) => call<AuditEvent[]>("GET", `${org(t)}/audit-events?limit=${limit}`),

    createExport: (t: string, projectId?: string) =>
      call<ExportJob>("POST", `${org(t)}/exports`, projectId ? { projectId } : {}),
    listExports: (t: string) => call<ExportJob[]>("GET", `${org(t)}/exports`),
    getExport: (t: string, jobId: string) => call<ExportJob>("GET", `${org(t)}/exports/${id(jobId)}`),
    downloadLink: (t: string, jobId: string) => call<DownloadLink>("GET", `${org(t)}/exports/${id(jobId)}/download`),
  };
}

export type Api = ReturnType<typeof createApi>;

export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    const ref = e.requestId ? ` (request_id: ${e.requestId})` : "";
    if (e.status === 403) return `403 — ${e.message}${ref}`;
    if (e.status === 404) return `404 — Không tìm thấy${ref}`;
    if (e.status === 409) return `409 — ${e.message}${ref}`;
    return `${e.status || "Lỗi"} — ${e.message}${ref}`;
  }
  return e instanceof Error ? e.message : String(e);
}
