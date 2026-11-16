"""TEST 1 — Chống IDOR chéo tenant.

Dùng JWT của user thuộc Org A gọi THẲNG API (không qua UI) để đọc / sửa / xóa / export
Project & Task mang ID của Org B. Kỳ vọng: 100% bị từ chối (403/404), body không chứa dữ liệu Org B,
và dữ liệu Org B không bị thay đổi.

Thử cả 2 kiểu tấn công:
  * "tenant-swap": đổi tenantId trong path sang Org B     -> /orgs/{B}/...       (phải 403)
  * "id-swap":     giữ tenant A, chỉ đổi ID tài nguyên     -> /orgs/{A}/tasks/{B} (phải 403/404)
"""
import pytest

import api_paths as P

DENIED = (403, 404)


def assert_denied(resp, secrets_of_b: list[str]):
    assert resp.status_code in DENIED, (
        f"LỖ HỔNG IDOR: {resp.request.method} {resp.request.path_url} trả {resp.status_code} (kỳ vọng 403/404)")
    body = resp.text
    for s in secrets_of_b:
        assert s not in body, f"RÒ RỈ DỮ LIỆU: response chứa '{s}' của Org B"


def leak_markers(res) -> list[str]:
    return [res["task"]["title"], res["project"]["name"]]


# (tên kịch bản, method, path builder(org_a, org_b, res), body)
READ_CASES = [
    ("list B projects via B tenant path", "GET", lambda a, b, r: P.projects(b), None),
    ("read B project via B tenant path", "GET", lambda a, b, r: P.project(b, r["project"]["id"]), None),
    ("read B project via A tenant path", "GET", lambda a, b, r: P.project(a, r["project"]["id"]), None),
    ("list B tasks via B tenant path", "GET", lambda a, b, r: P.project_tasks(b, r["project"]["id"]), None),
    ("list B tasks via A tenant path", "GET", lambda a, b, r: P.project_tasks(a, r["project"]["id"]), None),
    ("read B task via B tenant path", "GET", lambda a, b, r: P.task(b, r["task"]["id"]), None),
    ("read B task via A tenant path", "GET", lambda a, b, r: P.task(a, r["task"]["id"]), None),
    ("list B members", "GET", lambda a, b, r: P.members(b), None),
    ("read B audit log", "GET", lambda a, b, r: P.audit_events(b), None),
    ("read B export job via B path", "GET", lambda a, b, r: P.export(b, r["job_id"]), None),
    ("read B export job via A path", "GET", lambda a, b, r: P.export(a, r["job_id"]), None),
    ("download B export via B path", "GET", lambda a, b, r: P.export_download(b, r["job_id"]), None),
    ("download B export via A path", "GET", lambda a, b, r: P.export_download(a, r["job_id"]), None),
]

WRITE_CASES = [
    ("update B task via B path", "PATCH", lambda a, b, r: P.task(b, r["task"]["id"]), {"title": "HACKED by A"}),
    ("update B task via A path", "PATCH", lambda a, b, r: P.task(a, r["task"]["id"]), {"title": "HACKED by A"}),
    ("update B project via A path", "PATCH", lambda a, b, r: P.project(a, r["project"]["id"]), {"name": "HACKED by A"}),
    ("create task in B project via A path", "POST", lambda a, b, r: P.project_tasks(a, r["project"]["id"]), {"title": "HACKED by A"}),
    ("create project in Org B", "POST", lambda a, b, r: P.projects(b), {"name": "HACKED by A"}),
    ("export Org B", "POST", lambda a, b, r: P.exports(b), {}),
    ("export B project via A path", "POST", lambda a, b, r: P.exports(a), lambda r: {"projectId": r["project"]["id"]}),
    ("invite self into Org B", "POST", lambda a, b, r: P.members(b), {"email": "attacker@org-a.example.com", "role": "OWNER"}),
    ("delete B task via A path", "DELETE", lambda a, b, r: P.task(a, r["task"]["id"]), None),
    ("delete B task via B path", "DELETE", lambda a, b, r: P.task(b, r["task"]["id"]), None),
    ("delete B project via A path", "DELETE", lambda a, b, r: P.project(a, r["project"]["id"]), None),
]


@pytest.mark.parametrize("name,method,path,body", READ_CASES, ids=[c[0] for c in READ_CASES])
def test_org_a_cannot_read_org_b(a_owner, org_a, org_b, org_b_resources, name, method, path, body):
    resp = a_owner.request(method, path(org_a, org_b, org_b_resources), label=f"ATTACK {name}")
    assert_denied(resp, leak_markers(org_b_resources))


@pytest.mark.parametrize("name,method,path,body", WRITE_CASES, ids=[c[0] for c in WRITE_CASES])
def test_org_a_cannot_modify_org_b(a_owner, b_owner, org_a, org_b, org_b_resources, evidence,
                                   name, method, path, body):
    if callable(body):
        body = body(org_b_resources)
    kw = {"json": body} if body is not None else {}
    resp = a_owner.request(method, path(org_a, org_b, org_b_resources), label=f"ATTACK {name}", **kw)
    assert_denied(resp, leak_markers(org_b_resources))

    # Xác minh bằng token hợp lệ của Org B: dữ liệu KHÔNG bị thay đổi
    task = b_owner.get(P.task(org_b, org_b_resources["task"]["id"]), label="VERIFY B task intact")
    assert task.status_code == 200, "Task của Org B đã bị xóa bởi Org A!"
    assert task.json()["title"] == org_b_resources["task"]["title"], "Task của Org B đã bị Org A sửa!"
    proj = b_owner.get(P.project(org_b, org_b_resources["project"]["id"]), label="VERIFY B project intact")
    assert proj.status_code == 200 and proj.json()["name"] == org_b_resources["project"]["name"]
    members = b_owner.get(P.members(org_b), label="VERIFY B members unchanged")
    assert all(m["email"] != "attacker@org-a.example.com" for m in members.json())
    evidence.note("Org B data verified unchanged after attack", case=name)


def test_unauthenticated_requests_are_rejected(anonymous, org_b, org_b_resources):
    for path in (P.me(), P.projects(org_b), P.task(org_b, org_b_resources["task"]["id"])):
        resp = anonymous.get(path, label="ATTACK no token")
        assert resp.status_code == 401, f"{path} không có token mà trả {resp.status_code}"


def test_forged_token_is_rejected(settings, org_b):
    from evidence import ApiClient
    import conftest
    forged = ApiClient(settings["base_url"],
                       "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciIsInRpZCI6ImZvcmdlZCJ9.",
                       "forged", lambda: conftest._current_recorder)
    resp = forged.get(P.projects(org_b), label="ATTACK alg=none forged JWT")
    assert resp.status_code == 401


def test_guest_write_is_blocked_by_api(a_guest, org_a, a_owner):
    """Read-only Guest: mọi request ghi phải bị API chặn 403 (không chỉ ẩn nút trên UI)."""
    projects = a_owner.get(P.projects(org_a), label="setup: A projects").json()
    pid = projects[0]["id"]
    tasks = a_owner.get(P.project_tasks(org_a, pid), label="setup: A tasks").json()
    attempts = [
        ("POST", P.projects(org_a), {"name": "guest-should-fail"}),
        ("POST", P.project_tasks(org_a, pid), {"title": "guest-should-fail"}),
        ("PATCH", P.task(org_a, tasks[0]["id"]), {"status": "DONE"}),
        ("DELETE", P.task(org_a, tasks[0]["id"]), None),
        ("POST", P.exports(org_a), {}),
        ("POST", P.members(org_a), {"email": "guest-invite@x.example.com", "role": "OWNER"}),
    ]
    assert a_guest.get(P.projects(org_a), label="guest read OK").status_code == 200
    for method, path, body in attempts:
        kw = {"json": body} if body is not None else {}
        resp = a_guest.request(method, path, label=f"GUEST WRITE {method}", **kw)
        assert resp.status_code == 403, f"Guest {method} {path} trả {resp.status_code} (kỳ vọng 403)"
