"""Đường dẫn endpoint — NƠI DUY NHẤT cần sửa khi TV2 chốt OpenAPI.

Xem docs/tv4/API_CONTRACT_PROPOSAL.md.
"""
API = "/api/v1"


def me() -> str:
    return f"{API}/me"


def members(t: str) -> str:
    return f"{API}/orgs/{t}/members"


def member(t: str, user_id: str) -> str:
    return f"{API}/orgs/{t}/members/{user_id}"


def projects(t: str) -> str:
    return f"{API}/orgs/{t}/projects"


def project(t: str, project_id: str) -> str:
    return f"{API}/orgs/{t}/projects/{project_id}"


def project_tasks(t: str, project_id: str) -> str:
    return f"{API}/orgs/{t}/projects/{project_id}/tasks"


def task(t: str, task_id: str) -> str:
    return f"{API}/orgs/{t}/tasks/{task_id}"


def audit_events(t: str) -> str:
    return f"{API}/orgs/{t}/audit-events"


def exports(t: str) -> str:
    return f"{API}/orgs/{t}/exports"


def export(t: str, job_id: str) -> str:
    return f"{API}/orgs/{t}/exports/{job_id}"


def export_download(t: str, job_id: str) -> str:
    return f"{API}/orgs/{t}/exports/{job_id}/download"
