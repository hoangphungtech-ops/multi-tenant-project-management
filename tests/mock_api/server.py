"""Mock API cho TV4 — CHỈ dùng để phát triển Frontend và tự kiểm chứng bộ test bảo mật.

KHÔNG phải backend thật (TV2 sở hữu backend Spring Boot). Server này cài đặt đúng contract
trong docs/tv4/API_CONTRACT_PROPOSAL.md, chỉ dùng Python standard library.

Chạy:
    python tests/mock_api/server.py                 # an toàn (mặc định)
    python tests/mock_api/server.py --vuln idor     # cố tình có lỗ hổng để chứng minh test bắt được lỗi
    python tests/mock_api/server.py --vuln stale-membership
    python tests/mock_api/server.py --vuln global-unique
    python tests/mock_api/server.py --vuln shared-cache

Token giả lập: "Bearer mock-<userKey>", ví dụ mock-a-owner, mock-b-member1, mock-a-guest.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import hmac
import io
import json
import re
import secrets
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

NS = uuid.UUID("6f1c1d8e-6a43-4c3e-9a4f-2b7b0e5d0001")
ROLES = ("OWNER", "MEMBER", "GUEST")
WRITE_ROLES = ("OWNER", "MEMBER")
TASK_STATUSES = ("TODO", "IN_PROGRESS", "DONE")
SAS_TTL_SECONDS = 300
EXPORT_DELAY_SECONDS = 2.0
SIGNING_KEY = secrets.token_bytes(32)  # chỉ tồn tại trong RAM của mock


def uid(name: str) -> str:
    return str(uuid.uuid5(NS, name))


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Store:
    """Dữ liệu trong RAM, seed lặp lại được (UUID xác định theo tên)."""

    ORGS = (("a", "Org A - Contoso"), ("b", "Org B - Fabrikam"), ("c", "Org C - Northwind"))
    USER_ROLES = (("owner", "OWNER"), ("member1", "MEMBER"), ("member2", "MEMBER"),
                  ("member3", "MEMBER"), ("guest", "GUEST"))

    def __init__(self) -> None:
        self.lock = threading.RLock()
        self.reset()

    def reset(self) -> None:
        with self.lock:
            self.tenants: dict[str, dict] = {}
            self.users: dict[str, dict] = {}          # userId -> user
            self.tokens: dict[str, str] = {}          # token -> userId
            self.memberships: dict[tuple[str, str], dict] = {}  # (tenantId,userId) -> m
            self.projects: dict[str, dict] = {}
            self.tasks: dict[str, dict] = {}
            self.audit: list[dict] = []
            self.exports: dict[str, dict] = {}
            self.cache: dict[str, list] = {}
            for key, name in self.ORGS:
                tid = uid(f"tenant:{key}")
                self.tenants[tid] = {"id": tid, "name": name, "key": key}
                for ukey, role in self.USER_ROLES:
                    user_key = f"{key}-{ukey}"
                    user_id = uid(f"user:{user_key}")
                    self.users[user_id] = {
                        "userId": user_id,
                        "email": f"{ukey}@org-{key}.example.com",
                        "displayName": f"{ukey.capitalize()} ({key.upper()})",
                        "key": user_key,
                    }
                    self.tokens[f"mock-{user_key}"] = user_id
                    self.memberships[(tid, user_id)] = {"role": role, "joinedAt": now_iso()}
                for p in range(2):
                    pid = uid(f"project:{key}:{p}")
                    self.projects[pid] = {
                        "id": pid, "tenantId": tid, "name": f"Project {p + 1} of {key.upper()}",
                        "description": f"Seed project {p + 1}", "createdAt": now_iso(),
                    }
                    for t in range(10):  # 2 project x 10 task = 20 task / org
                        task_id = uid(f"task:{key}:{p}:{t}")
                        self.tasks[task_id] = {
                            "id": task_id, "tenantId": tid, "projectId": pid,
                            "title": f"[{key.upper()}] Task {p * 10 + t + 1}",
                            "description": f"Seed task of {name}",
                            "status": TASK_STATUSES[t % 3],
                            "assigneeUserId": uid(f"user:{key}-member{t % 3 + 1}"),
                            "createdAt": now_iso(), "updatedAt": now_iso(),
                        }
            # Một user ở 2 org để demo "Chuyển Org"
            shared = uid("user:a-member1")
            self.memberships[(uid("tenant:b"), shared)] = {"role": "GUEST", "joinedAt": now_iso()}


STORE = Store()
VULNS: set[str] = set()


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


ROUTES: list[tuple[str, re.Pattern, str]] = []


def route(method: str, pattern: str):
    def deco(fn):
        regex = re.compile("^" + re.sub(r"\{(\w+)\}", r"(?P<\1>[^/]+)", pattern) + "$")
        ROUTES.append((method, regex, fn.__name__))
        return fn
    return deco


class Handler(BaseHTTPRequestHandler):
    server_version = "TV4MockAPI/1.0"

    # ---------- plumbing ----------
    def log_message(self, fmt: str, *args) -> None:  # không log header Authorization
        print(f"[mock-api] {self.request_id} {self.command} {urlparse(self.path).path} -> {args[1] if len(args) > 1 else ''}")

    def _cors(self) -> None:
        origin = self.headers.get("Origin")
        if origin and re.match(r"^http://(localhost|127\.0\.0\.1):\d+$", origin):
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
            self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
            self.send_header("Access-Control-Expose-Headers", "X-Request-Id")

    def _send(self, status: int, body=None, content_type: str = "application/json") -> None:
        payload = b""
        if body is not None:
            payload = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(status)
        self._cors()
        self.send_header("X-Request-Id", self.request_id)
        if payload:
            self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if payload:
            self.wfile.write(payload)

    def _json_body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            data = json.loads(self.rfile.read(length))
        except json.JSONDecodeError as exc:
            raise ApiError(400, "BAD_REQUEST", "Body không phải JSON hợp lệ") from exc
        if not isinstance(data, dict):
            raise ApiError(400, "BAD_REQUEST", "Body phải là JSON object")
        return data

    def do_OPTIONS(self) -> None:
        self.request_id = self.headers.get("X-Request-Id") or f"req-{uuid.uuid4().hex[:12]}"
        self._send(204)

    def do_GET(self) -> None: self._dispatch("GET")
    def do_POST(self) -> None: self._dispatch("POST")
    def do_PATCH(self) -> None: self._dispatch("PATCH")
    def do_DELETE(self) -> None: self._dispatch("DELETE")

    def _dispatch(self, method: str) -> None:
        incoming = self.headers.get("X-Request-Id") or ""
        self.request_id = incoming if re.fullmatch(r"[\w.\-]{1,128}", incoming) else f"req-{uuid.uuid4().hex[:12]}"
        parsed = urlparse(self.path)
        self.query = parse_qs(parsed.query)
        try:
            for m, regex, name in ROUTES:
                match = regex.match(parsed.path)
                if m == method and match:
                    with STORE.lock:
                        result = getattr(self, name)(**match.groupdict())
                    if isinstance(result, tuple):
                        self._send(*result)
                    else:
                        self._send(200, result)
                    return
            raise ApiError(404, "NOT_FOUND", "Không có endpoint này")
        except ApiError as err:
            self._send(err.status, {"error": err.code, "message": err.message, "requestId": self.request_id})
        except Exception:  # pragma: no cover - không lộ stack trace ra client
            self._send(500, {"error": "INTERNAL", "message": "Lỗi nội bộ", "requestId": self.request_id})

    # ---------- security helpers ----------
    def _user(self) -> dict:
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            raise ApiError(401, "UNAUTHORIZED", "Thiếu Bearer token")
        user_id = STORE.tokens.get(auth[7:].strip())
        if not user_id:
            raise ApiError(401, "UNAUTHORIZED", "Token không hợp lệ")
        return STORE.users[user_id]

    def _member(self, tenant_id: str, roles: tuple[str, ...] = ROLES) -> tuple[dict, str]:
        """Tương đương TenantMembershipInterceptor: kiểm tra bảng memberships MỖI request."""
        user = self._user()
        if tenant_id not in STORE.tenants:
            raise ApiError(403, "FORBIDDEN", "Không phải thành viên tổ chức này")
        m = STORE.memberships.get((tenant_id, user["userId"]))
        if m is None and "stale-membership" in VULNS:
            m = getattr(self.server, "revoked", {}).get((tenant_id, user["userId"]))  # LỖ HỔNG cố ý
        if m is None:
            raise ApiError(403, "FORBIDDEN", "Không phải thành viên tổ chức này")
        if m["role"] not in roles:
            raise ApiError(403, "FORBIDDEN", f"Role {m['role']} không được phép thao tác này")
        return user, m["role"]

    def _owned(self, table: dict, entity_id: str, tenant_id: str, label: str) -> dict:
        row = table.get(entity_id)
        if row is None or (row["tenantId"] != tenant_id and "idor" not in VULNS):
            raise ApiError(404, "NOT_FOUND", f"{label} không tồn tại")  # không tiết lộ tenant khác
        return row

    def _audit(self, tenant_id: str, user: dict, action: str, entity_type: str, entity_id: str, details=None) -> None:
        STORE.audit.append({
            "id": str(uuid.uuid4()), "tenantId": tenant_id, "actorUserId": user["userId"],
            "actorEmail": user["email"], "action": action, "entityType": entity_type,
            "entityId": entity_id, "requestId": self.request_id, "createdAt": now_iso(),
            "details": details or {},
        })

    def _invalidate(self, tenant_id: str) -> None:
        STORE.cache.pop(f"tenant:{tenant_id}:projects", None)
        STORE.cache.pop("projects", None)

    # ---------- endpoints ----------
    @route("GET", "/health")
    def health(self):
        return {"status": "UP", "mock": True, "vulns": sorted(VULNS)}

    @route("GET", "/mock/users")
    def mock_users(self):
        """Chỉ có trong mock: danh sách user để Frontend chọn đăng nhập giả lập."""
        out = []
        for token, user_id in STORE.tokens.items():
            u = STORE.users[user_id]
            out.append({"token": token, "email": u["email"], "displayName": u["displayName"]})
        return out

    @route("POST", "/mock/reset")
    def mock_reset(self):
        STORE.reset()
        self.server.revoked = {}
        return {"reset": True}

    @route("GET", "/api/v1/me")
    def me(self):
        user = self._user()
        ms = [
            {"tenantId": tid, "tenantName": STORE.tenants[tid]["name"], "role": m["role"]}
            for (tid, user_id), m in STORE.memberships.items() if user_id == user["userId"]
        ]
        return {"userId": user["userId"], "email": user["email"], "displayName": user["displayName"],
                "memberships": sorted(ms, key=lambda x: x["tenantName"])}

    # members
    @route("GET", "/api/v1/orgs/{tenant_id}/members")
    def list_members(self, tenant_id):
        self._member(tenant_id)
        return [
            {**{k: STORE.users[u][k] for k in ("userId", "email", "displayName")}, **m}
            for (tid, u), m in STORE.memberships.items() if tid == tenant_id
        ]

    @route("POST", "/api/v1/orgs/{tenant_id}/members")
    def add_member(self, tenant_id):
        actor, _ = self._member(tenant_id, ("OWNER",))
        body = self._json_body()
        email, role = (body.get("email") or "").strip().lower(), body.get("role")
        if role not in ROLES or "@" not in email:
            raise ApiError(400, "BAD_REQUEST", "Cần email hợp lệ và role OWNER/MEMBER/GUEST")
        user = next((u for u in STORE.users.values() if u["email"] == email), None)
        if user is None:
            user_key = "invited-" + hashlib.sha1(email.encode()).hexdigest()[:8]
            user = {"userId": uid(f"user:{user_key}"), "email": email,
                    "displayName": email.split("@")[0], "key": user_key}
            STORE.users[user["userId"]] = user
            STORE.tokens[f"mock-{user_key}"] = user["userId"]
        if (tenant_id, user["userId"]) in STORE.memberships:
            raise ApiError(409, "CONFLICT", "User đã là thành viên")
        STORE.memberships[(tenant_id, user["userId"])] = {"role": role, "joinedAt": now_iso()}
        getattr(self.server, "revoked", {}).pop((tenant_id, user["userId"]), None)
        self._audit(tenant_id, actor, "MEMBER_ADDED", "membership", user["userId"], {"role": role})
        return 201, {"userId": user["userId"], "email": email, "role": role}

    @route("PATCH", "/api/v1/orgs/{tenant_id}/members/{user_id}")
    def change_role(self, tenant_id, user_id):
        actor, _ = self._member(tenant_id, ("OWNER",))
        role = self._json_body().get("role")
        if role not in ROLES:
            raise ApiError(400, "BAD_REQUEST", "Role không hợp lệ")
        m = STORE.memberships.get((tenant_id, user_id))
        if m is None:
            raise ApiError(404, "NOT_FOUND", "Thành viên không tồn tại")
        old, m["role"] = m["role"], role
        self._audit(tenant_id, actor, "MEMBER_ROLE_CHANGED", "membership", user_id, {"from": old, "to": role})
        return {"userId": user_id, "role": role}

    @route("DELETE", "/api/v1/orgs/{tenant_id}/members/{user_id}")
    def remove_member(self, tenant_id, user_id):
        actor, _ = self._member(tenant_id, ("OWNER",))
        if user_id == actor["userId"]:
            raise ApiError(400, "BAD_REQUEST", "Owner không thể tự xóa chính mình")
        m = STORE.memberships.pop((tenant_id, user_id), None)
        if m is None:
            raise ApiError(404, "NOT_FOUND", "Thành viên không tồn tại")
        if not hasattr(self.server, "revoked"):
            self.server.revoked = {}
        self.server.revoked[(tenant_id, user_id)] = m
        self._audit(tenant_id, actor, "MEMBER_REMOVED", "membership", user_id)
        return 204, None

    # projects
    @route("GET", "/api/v1/orgs/{tenant_id}/projects")
    def list_projects(self, tenant_id):
        self._member(tenant_id)
        key = "projects" if "shared-cache" in VULNS else f"tenant:{tenant_id}:projects"  # LỖ HỔNG cố ý
        if key not in STORE.cache:
            STORE.cache[key] = sorted(
                (p for p in STORE.projects.values() if p["tenantId"] == tenant_id),
                key=lambda p: p["name"])
        return STORE.cache[key]

    @route("POST", "/api/v1/orgs/{tenant_id}/projects")
    def create_project(self, tenant_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        body = self._json_body()
        name = (body.get("name") or "").strip()
        if not name or len(name) > 120:
            raise ApiError(400, "BAD_REQUEST", "Tên project 1–120 ký tự")
        scope = STORE.projects.values() if "global-unique" in VULNS else \
            (p for p in STORE.projects.values() if p["tenantId"] == tenant_id)
        if any(p["name"].lower() == name.lower() for p in scope):
            raise ApiError(409, "CONFLICT", "Tên project đã tồn tại trong tổ chức")
        pid = str(uuid.uuid4())
        STORE.projects[pid] = {"id": pid, "tenantId": tenant_id, "name": name,
                               "description": body.get("description") or "", "createdAt": now_iso()}
        self._invalidate(tenant_id)
        self._audit(tenant_id, user, "PROJECT_CREATED", "project", pid, {"name": name})
        return 201, STORE.projects[pid]

    @route("GET", "/api/v1/orgs/{tenant_id}/projects/{project_id}")
    def get_project(self, tenant_id, project_id):
        self._member(tenant_id)
        return self._owned(STORE.projects, project_id, tenant_id, "Project")

    @route("PATCH", "/api/v1/orgs/{tenant_id}/projects/{project_id}")
    def update_project(self, tenant_id, project_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        p = self._owned(STORE.projects, project_id, tenant_id, "Project")
        body = self._json_body()
        for k in ("name", "description"):
            if k in body:
                p[k] = body[k]
        self._invalidate(tenant_id)
        self._audit(tenant_id, user, "PROJECT_UPDATED", "project", project_id)
        return p

    @route("DELETE", "/api/v1/orgs/{tenant_id}/projects/{project_id}")
    def delete_project(self, tenant_id, project_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        self._owned(STORE.projects, project_id, tenant_id, "Project")
        del STORE.projects[project_id]
        for tid in [t for t, v in STORE.tasks.items() if v["projectId"] == project_id]:
            del STORE.tasks[tid]
        self._invalidate(tenant_id)
        self._audit(tenant_id, user, "PROJECT_DELETED", "project", project_id)
        return 204, None

    # tasks
    @route("GET", "/api/v1/orgs/{tenant_id}/projects/{project_id}/tasks")
    def list_tasks(self, tenant_id, project_id):
        self._member(tenant_id)
        self._owned(STORE.projects, project_id, tenant_id, "Project")
        return sorted((t for t in STORE.tasks.values() if t["projectId"] == project_id),
                      key=lambda t: t["createdAt"] + t["title"])

    @route("POST", "/api/v1/orgs/{tenant_id}/projects/{project_id}/tasks")
    def create_task(self, tenant_id, project_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        self._owned(STORE.projects, project_id, tenant_id, "Project")
        body = self._json_body()
        title = (body.get("title") or "").strip()
        status = body.get("status") or "TODO"
        if not title or status not in TASK_STATUSES:
            raise ApiError(400, "BAD_REQUEST", "Cần title và status hợp lệ")
        tid = str(uuid.uuid4())
        STORE.tasks[tid] = {"id": tid, "tenantId": tenant_id, "projectId": project_id, "title": title,
                            "description": body.get("description") or "", "status": status,
                            "assigneeUserId": body.get("assigneeUserId"),
                            "createdAt": now_iso(), "updatedAt": now_iso()}
        self._audit(tenant_id, user, "TASK_CREATED", "task", tid, {"title": title})
        return 201, STORE.tasks[tid]

    @route("GET", "/api/v1/orgs/{tenant_id}/tasks/{task_id}")
    def get_task(self, tenant_id, task_id):
        self._member(tenant_id)
        return self._owned(STORE.tasks, task_id, tenant_id, "Task")

    @route("PATCH", "/api/v1/orgs/{tenant_id}/tasks/{task_id}")
    def update_task(self, tenant_id, task_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        t = self._owned(STORE.tasks, task_id, tenant_id, "Task")
        body = self._json_body()
        if "status" in body and body["status"] not in TASK_STATUSES:
            raise ApiError(400, "BAD_REQUEST", "Status không hợp lệ")
        changed = {k: body[k] for k in ("title", "description", "status", "assigneeUserId") if k in body}
        t.update(changed)
        t["updatedAt"] = now_iso()
        self._audit(tenant_id, user, "TASK_UPDATED", "task", task_id, changed)
        return t

    @route("DELETE", "/api/v1/orgs/{tenant_id}/tasks/{task_id}")
    def delete_task(self, tenant_id, task_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        self._owned(STORE.tasks, task_id, tenant_id, "Task")
        del STORE.tasks[task_id]
        self._audit(tenant_id, user, "TASK_DELETED", "task", task_id)
        return 204, None

    # audit
    @route("GET", "/api/v1/orgs/{tenant_id}/audit-events")
    def list_audit(self, tenant_id):
        self._member(tenant_id)
        try:
            limit = max(1, min(200, int(self.query.get("limit", ["50"])[0])))
        except ValueError:
            limit = 50
        rows = [e for e in reversed(STORE.audit) if e["tenantId"] == tenant_id]
        return rows[:limit]

    # exports
    def _job_view(self, job: dict) -> dict:
        if job["status"] == "QUEUED" and time.time() - job["_t"] > EXPORT_DELAY_SECONDS / 2:
            job["status"] = "PROCESSING"
        if job["status"] == "PROCESSING" and time.time() - job["_t"] > EXPORT_DELAY_SECONDS:
            job["status"], job["completedAt"] = "COMPLETED", now_iso()
            job["blobPath"] = f"exports/{job['tenantId']}/{job['jobId']}.csv"
        return {k: v for k, v in job.items() if not k.startswith("_") and k != "blobPath"}

    @route("POST", "/api/v1/orgs/{tenant_id}/exports")
    def create_export(self, tenant_id):
        user, _ = self._member(tenant_id, WRITE_ROLES)
        project_id = self._json_body().get("projectId")
        if project_id:
            self._owned(STORE.projects, project_id, tenant_id, "Project")
        job_id = str(uuid.uuid4())
        STORE.exports[job_id] = {"jobId": job_id, "tenantId": tenant_id, "projectId": project_id,
                                 "status": "QUEUED", "requestedBy": user["userId"],
                                 "createdAt": now_iso(), "completedAt": None, "errorCode": None,
                                 "_t": time.time()}
        self._audit(tenant_id, user, "EXPORT_REQUESTED", "export_job", job_id, {"projectId": project_id})
        return 202, {"jobId": job_id, "status": "QUEUED"}

    @route("GET", "/api/v1/orgs/{tenant_id}/exports")
    def list_exports(self, tenant_id):
        self._member(tenant_id)
        jobs = [self._job_view(j) for j in STORE.exports.values() if j["tenantId"] == tenant_id]
        return sorted(jobs, key=lambda j: j["createdAt"], reverse=True)

    @route("GET", "/api/v1/orgs/{tenant_id}/exports/{job_id}")
    def get_export(self, tenant_id, job_id):
        self._member(tenant_id)
        return self._job_view(self._owned(STORE.exports, job_id, tenant_id, "Export job"))

    @route("GET", "/api/v1/orgs/{tenant_id}/exports/{job_id}/download")
    def download_export(self, tenant_id, job_id):
        self._member(tenant_id, WRITE_ROLES)
        job = self._owned(STORE.exports, job_id, tenant_id, "Export job")
        self._job_view(job)
        if job["status"] != "COMPLETED":
            raise ApiError(409, "NOT_READY", "Export chưa hoàn tất")
        exp = int(time.time()) + SAS_TTL_SECONDS
        path = job["blobPath"]
        sig = hmac.new(SIGNING_KEY, f"{path}|{exp}".encode(), hashlib.sha256).hexdigest()
        host = self.headers.get("Host", "localhost:8080")
        return {"url": f"http://{host}/mock-blob/{path}?se={exp}&sp=r&sig={sig}",
                "expiresAt": datetime.fromtimestamp(exp, timezone.utc).isoformat(timespec="seconds")}

    @route("GET", "/mock-blob/exports/{tenant_id}/{file_name}")
    def mock_blob(self, tenant_id, file_name):
        """Giả lập Blob private + SAS: thiếu/sai/hết hạn chữ ký -> 403."""
        path = f"exports/{tenant_id}/{file_name}"
        try:
            exp = int(self.query.get("se", ["0"])[0])
        except ValueError:
            exp = 0
        sig = self.query.get("sig", [""])[0]
        good = hmac.new(SIGNING_KEY, f"{path}|{exp}".encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, good) or exp < time.time():
            raise ApiError(403, "AUTHENTICATION_FAILED", "SAS không hợp lệ hoặc đã hết hạn")
        job = STORE.exports.get(file_name.removesuffix(".csv"))
        if not job or job["tenantId"] != tenant_id:
            raise ApiError(404, "BLOB_NOT_FOUND", "Không có blob")
        buf = io.StringIO()
        w = csv.writer(buf)
        w.writerow(["task_id", "project_id", "title", "status", "assignee_user_id", "updated_at"])
        for t in STORE.tasks.values():
            if t["tenantId"] == tenant_id and (not job["projectId"] or t["projectId"] == job["projectId"]):
                title = t["title"]
                if title[:1] in ("=", "+", "-", "@"):
                    title = "'" + title  # chống CSV formula injection
                w.writerow([t["id"], t["projectId"], title, t["status"], t["assigneeUserId"] or "", t["updatedAt"]])
        return 200, buf.getvalue().encode("utf-8-sig"), "text/csv; charset=utf-8"


def main() -> None:
    parser = argparse.ArgumentParser(description="TV4 mock API (chỉ dùng local)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--vuln", action="append", default=[],
                        choices=["idor", "stale-membership", "global-unique", "shared-cache"],
                        help="Cố tình bật lỗ hổng để chứng minh bộ test phát hiện được")
    args = parser.parse_args()
    VULNS.update(args.vuln)
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.revoked = {}
    print(f"[mock-api] listening on http://{args.host}:{args.port}  vulns={sorted(VULNS) or 'none'}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
