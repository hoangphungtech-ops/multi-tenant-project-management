export type Role = "OWNER" | "MEMBER" | "GUEST";
export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export type ExportStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED" | "REJECTED";

export interface Membership { tenantId: string; tenantName: string; role: Role }
export interface Me { userId: string; email: string; displayName: string; memberships: Membership[] }
export interface Member { userId: string; email: string; displayName: string; role: Role; joinedAt?: string }
export interface Project { id: string; tenantId?: string; name: string; description?: string; createdAt?: string }
export interface Task {
  id: string; tenantId?: string; projectId: string; title: string; description?: string;
  status: TaskStatus; assigneeUserId?: string | null; createdAt?: string; updatedAt?: string;
}
export interface AuditEvent {
  id: string; actorUserId: string; actorEmail?: string; action: string; entityType: string;
  entityId: string; requestId?: string; createdAt: string; details?: Record<string, unknown>;
}
export interface ExportJob {
  jobId: string; status: ExportStatus; projectId?: string | null; requestedBy?: string;
  createdAt?: string; completedAt?: string | null; errorCode?: string | null;
}
export interface DownloadLink { url: string; expiresAt: string }
