"""Security Test 1–3 chạy trên BACKEND THẬT của TV2 (backend-core-api), bản đang có trên main.

Chạy:
    python tests/backend_local/tv2_backend.py start
    API_TARGET=tv2-local python -m pytest tests/security/backend_tv2   (chạy trong tests/security)

Backend hiện mới có CRUD `workspaces` (đóng vai trò project) và export, nên:
  * Test 1 (IDOR)  : trên workspace + export job của Org B.
  * Test 2 (thu hồi): chưa có API xóa thành viên -> thu hồi trực tiếp trong DB (status=INACTIVE),
                      rồi kiểm tra API chặn NGAY request kế tiếp với cùng danh tính.
  * Test 3 (trùng tên): `UNIQUE(tenant_id, name)` trên workspaces + danh sách không lẫn tenant.
Danh tính lấy từ seed `R__seed_data.sql` qua header demo (chỉ hợp lệ trên máy local).
"""
from __future__ import annotations

import os
import subprocess
import uuid

import pytest

API = "/api/v1/tenants"
DENIED = (403, 404)


def ws(t, wid=None):
    return f"{API}/{t}/workspaces" + (f"/{wid}" if wid else "")


def exports(t, job=None, download=False):
    return f"{API}/{t}/exports" + (f"/{job}" if job else "") + ("/download" if download else "")


def assert_denied(resp, secret: str):
    assert resp.status_code in DENIED, (
        f"LỖ HỔNG: {resp.request.method} {resp.request.path_url} trả {resp.status_code} (kỳ vọng 403/404)")
    assert secret not in resp.text, "RÒ RỈ: response chứa dữ liệu của Org B"


DB_CONTAINER = os.environ.get("TV2_DB_CONTAINER", "tv4-qa-postgres")


def psql(sql: str) -> str:
    """Chạy SQL trong DB container (chỉ để chuẩn bị/dọn dữ liệu test, không phải đối tượng kiểm thử)."""
    res = subprocess.run(["docker", "exec", DB_CONTAINER, "psql", "-U", "backend", "-d", "backenddb", "-tAc", sql],
                         capture_output=True, text=True)
    if res.returncode != 0:
        pytest.skip(f"Không truy cập được DB container '{DB_CONTAINER}': {res.stderr.strip()[:200]}")
    return res.stdout.strip()


def db_create_workspace(tenant: str, name: str, description: str = "") -> dict:
    wid = psql(f"INSERT INTO workspaces (tenant_id, name, description) VALUES ('{tenant}', '{name}', '{description}') "
               f"RETURNING id;").splitlines()[0]
    return {"id": wid, "name": name, "tenantId": tenant}


def db_delete_workspace(wid: str) -> None:
    psql(f"DELETE FROM workspaces WHERE id = '{wid}';")


# ---------- dữ liệu của Org B ----------
# Ghi chú: tạo workspace qua API đang lỗi 500 (xem test_3); dữ liệu chuẩn bị được tạo thẳng trong DB
# để các kịch bản TẤN CÔNG (đi qua API) vẫn kiểm tra được.
@pytest.fixture(scope="module")
def b_data(b_owner, org_b):
    secret = f"SECRET_ORG_B_{uuid.uuid4().hex[:8]}"
    w = db_create_workspace(org_b, f"TV4-QA {secret}", secret)
    assert b_owner.get(ws(org_b, w["id"]), label="setup: B-owner reads own workspace").status_code == 200
    r = b_owner.post(exports(org_b), label="setup: B-owner creates export job")
    assert r.status_code == 202, f"Không tạo được export Org B: {r.status_code} {r.text[:200]}"
    data = {"ws": w, "job": r.json()["jobId"], "secret": secret}
    yield data
    db_delete_workspace(w["id"])


# =========================== TEST 1: IDOR chéo tenant ===========================
READ = [
    ("list B workspaces via B path",  lambda a, b, d: ws(b)),
    ("read B workspace via B path",   lambda a, b, d: ws(b, d["ws"]["id"])),
    ("read B workspace via A path",   lambda a, b, d: ws(a, d["ws"]["id"])),
    ("read B export job via B path",  lambda a, b, d: exports(b, d["job"])),
    ("read B export job via A path",  lambda a, b, d: exports(a, d["job"])),
    ("download B export via B path",  lambda a, b, d: exports(b, d["job"], True)),
    ("download B export via A path",  lambda a, b, d: exports(a, d["job"], True)),
]
WRITE = [
    ("update B workspace via B path", "PUT",    lambda a, b, d: ws(b, d["ws"]["id"]), {"name": "HACKED by A"}),
    ("update B workspace via A path", "PUT",    lambda a, b, d: ws(a, d["ws"]["id"]), {"name": "HACKED by A"}),
    ("delete B workspace via B path", "DELETE", lambda a, b, d: ws(b, d["ws"]["id"]), None),
    ("delete B workspace via A path", "DELETE", lambda a, b, d: ws(a, d["ws"]["id"]), None),
    ("create workspace in Org B",     "POST",   lambda a, b, d: ws(b), {"name": "HACKED by A"}),
    ("export Org B",                  "POST",   lambda a, b, d: exports(b), None),
]


@pytest.mark.parametrize("name,path", READ, ids=[c[0] for c in READ])
def test_1_org_a_cannot_read_org_b(a_owner, org_a, org_b, b_data, name, path):
    resp = a_owner.get(path(org_a, org_b, b_data), label=f"ATTACK {name}")
    assert_denied(resp, b_data["secret"])


@pytest.mark.parametrize("name,method,path,body", WRITE, ids=[c[0] for c in WRITE])
def test_1_org_a_cannot_modify_org_b(a_owner, b_owner, org_a, org_b, b_data, name, method, path, body):
    kw = {"json": body} if body is not None else {}
    resp = a_owner.request(method, path(org_a, org_b, b_data), label=f"ATTACK {name}", **kw)
    assert_denied(resp, b_data["secret"])
    check = b_owner.get(ws(org_b, b_data["ws"]["id"]), label="VERIFY B workspace intact")
    assert check.status_code == 200, "Workspace của Org B đã bị Org A xóa!"
    assert check.json()["name"] == b_data["ws"]["name"], "Workspace của Org B đã bị Org A sửa!"


def test_1_guest_cannot_write(a_guest, a_owner, org_a):
    assert a_guest.get(ws(org_a), label="guest read OK").status_code == 200
    wid = db_create_workspace(org_a, f"TV4-QA guest-target {uuid.uuid4().hex[:6]}")["id"]
    try:
        for method, path, body in [("POST", ws(org_a), {"name": "guest-should-fail"}),
                                   ("PUT", ws(org_a, wid), {"name": "guest-should-fail"}),
                                   ("DELETE", ws(org_a, wid), None),
                                   ("POST", exports(org_a), None)]:
            kw = {"json": body} if body is not None else {}
            resp = a_guest.request(method, path, label=f"GUEST WRITE {method}", **kw)
            assert resp.status_code == 403, f"Guest {method} {path} trả {resp.status_code} (kỳ vọng 403)"
    finally:
        db_delete_workspace(wid)


def test_1_no_identity_is_rejected(anonymous, org_a, evidence):
    resp = anonymous.get(ws(org_a), label="ATTACK no identity")
    assert resp.status_code in (401, 403), f"LỖ HỔNG: không có danh tính mà trả {resp.status_code}"
    if resp.status_code != 401:
        evidence.note("Bị chặn đúng, nhưng mã là 403 thay vì 401 như contract đề xuất", status=resp.status_code)


# =========================== TEST 2: thu hồi membership ===========================
def set_membership_status(user_key: str, tenant: str, status: str) -> None:
    out = psql(f"UPDATE tenant_memberships SET status='{status}' WHERE tenant_id='{tenant}' "
               f"AND user_id=(SELECT id FROM users WHERE entra_object_id='{user_key}');")
    assert "UPDATE 1" in out, f"Không tìm thấy membership để cập nhật: {out!r}"


def test_2_revoked_membership_blocked_on_next_request(user_x, org_a, evidence):
    assert user_x.get(ws(org_a), label="BEFORE revoke: X lists Org A").status_code == 200
    set_membership_status("orga-user-x", org_a, "INACTIVE")
    evidence.note("DB: tenant_memberships.status = INACTIVE cho orga-user-x @ Org A")
    try:
        r = user_x.get(ws(org_a), label="AFTER revoke: X lists Org A (same identity)")
        assert r.status_code == 403, f"LỖ HỔNG: X vẫn đọc được Org A sau khi bị thu hồi ({r.status_code})"
        w = user_x.post(ws(org_a), json={"name": "written after revoke"}, label="AFTER revoke: X writes")
        assert w.status_code == 403
        e = user_x.post(exports(org_a), label="AFTER revoke: X exports")
        assert e.status_code == 403
    finally:
        set_membership_status("orga-user-x", org_a, "ACTIVE")
        evidence.note("DB: khôi phục status = ACTIVE")
    assert user_x.get(ws(org_a), label="AFTER restore: X lists Org A").status_code == 200


# =========================== TEST 3: trùng tên & tách biệt ===========================
def ids(resp):
    assert resp.status_code == 200
    return {w["id"] for w in resp.json()}


def test_3_same_workspace_name_in_two_tenants(a_owner, b_owner, org_a, org_b, settings, evidence):
    name = f"{settings['cloud_project_name']} {uuid.uuid4().hex[:6]}"
    a_owner.get(ws(org_a), label="warm A")
    b_owner.get(ws(org_b), label="warm B")
    ra = a_owner.post(ws(org_a), json={"name": name}, label=f"A creates '{name}'")
    rb = b_owner.post(ws(org_b), json={"name": name}, label=f"B creates '{name}'")
    created = [(a_owner, org_a, ra), (b_owner, org_b, rb)]
    try:
        assert ra.status_code == 201, f"A không tạo được: {ra.status_code}"
        assert rb.status_code == 201, f"B bị chặn tạo trùng tên ({rb.status_code}) — UNIQUE đang là toàn cục?"
        wa, wb = ra.json(), rb.json()
        assert wa["id"] != wb["id"] and wa["tenantId"] == org_a and wb["tenantId"] == org_b
        evidence.note("Two distinct DB records", a=wa["id"], b=wb["id"])
        for n in (1, 2):
            la = ids(a_owner.get(ws(org_a), label=f"A lists (read #{n})"))
            lb = ids(b_owner.get(ws(org_b), label=f"B lists (read #{n})"))
            assert wa["id"] in la and wb["id"] not in la
            assert wb["id"] in lb and wa["id"] not in lb
        up = a_owner.put(ws(org_a, wa["id"]), json={"name": name, "description": "changed by A"}, label="A updates")
        assert up.status_code == 200
        assert b_owner.get(ws(org_b, wb["id"]), label="B reads own").json().get("description") != "changed by A"
    finally:
        for _client, _org, r in created:
            if r.status_code == 201:
                db_delete_workspace(r.json()["id"])


def test_3_duplicate_name_in_same_tenant_is_409(a_owner, org_a, settings):
    name = f"{settings['cloud_project_name']} dup {uuid.uuid4().hex[:6]}"
    first = a_owner.post(ws(org_a), json={"name": name}, label=f"A creates '{name}'")
    assert first.status_code == 201
    try:
        dup = a_owner.post(ws(org_a), json={"name": name}, label=f"A creates '{name}' again")
        assert dup.status_code != 201, "Trùng tên trong cùng tenant mà vẫn tạo được — thiếu UNIQUE(tenant_id, name)"
        assert dup.status_code == 409, (
            f"Bị chặn nhưng trả {dup.status_code} thay vì 409 Conflict "
            f"(lỗi ràng buộc UNIQUE chưa được map sang 409): {dup.text[:200]}")
    finally:
        db_delete_workspace(first.json()["id"])


def test_3_db_unique_is_per_tenant(org_a, org_b, settings, evidence):
    """Kiểm tra ràng buộc ở tầng DB, độc lập với lỗi 500 của API tạo workspace."""
    name = f"{settings['cloud_project_name']} db {uuid.uuid4().hex[:6]}"
    a = db_create_workspace(org_a, name)
    b = db_create_workspace(org_b, name)
    try:
        assert a["id"] != b["id"], "Hai tenant dùng chung bản ghi"
        evidence.note("DB chấp nhận cùng tên ở 2 tenant", a=a["id"], b=b["id"])
        res = subprocess.run(["docker", "exec", DB_CONTAINER, "psql", "-U", "backend", "-d", "backenddb", "-tAc",
                              f"INSERT INTO workspaces (tenant_id, name) VALUES ('{org_a}', '{name}');"],
                             capture_output=True, text=True)
        evidence.note("DB: chèn trùng tên trong cùng tenant", returncode=res.returncode, stderr=res.stderr.strip()[:300])
        assert res.returncode != 0 and "uq_workspace_tenant_name" in res.stderr, \
            "DB cho phép trùng tên trong cùng tenant — thiếu UNIQUE(tenant_id, name)"
    finally:
        db_delete_workspace(a["id"])
        db_delete_workspace(b["id"])
