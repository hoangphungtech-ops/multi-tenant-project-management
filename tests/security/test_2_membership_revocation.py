"""TEST 2 — Thu hồi Membership có hiệu lực NGAY LẬP TỨC.

Owner xóa User X khỏi Org A, rồi NGAY LẬP TỨC dùng lại đúng JWT cũ (vẫn còn hạn) của User X
gọi API Org A. Kỳ vọng: 403 ngay request kế tiếp, vì API kiểm tra bảng memberships mỗi request,
không tin role/tenant ghi trong JWT.

Test tự dọn dẹp: thêm lại User X với role MEMBER để chạy lặp lại được.
"""
import time

import pytest

import api_paths as P


@pytest.fixture
def x_identity(user_x):
    r = user_x.get(P.me(), label="user-X: /me (identify)")
    assert r.status_code == 200
    return r.json()


@pytest.fixture
def x_is_member_of_a(a_owner, org_a, x_identity, evidence):
    """Bảo đảm X đang là MEMBER của Org A trước test; sau test thêm lại nếu đã bị xóa."""
    members = a_owner.get(P.members(org_a), label="A-owner: list members (precondition)").json()
    if not any(m["userId"] == x_identity["userId"] for m in members):
        r = a_owner.post(P.members(org_a), json={"email": x_identity["email"], "role": "MEMBER"},
                         label="A-owner: re-add user X (precondition)")
        assert r.status_code in (200, 201), "Không thêm được User X vào Org A"
    yield x_identity
    members = a_owner.get(P.members(org_a), label="A-owner: list members (teardown)").json()
    if not any(m["userId"] == x_identity["userId"] for m in members):
        r = a_owner.post(P.members(org_a), json={"email": x_identity["email"], "role": "MEMBER"},
                         label="A-owner: restore user X (teardown)")
        evidence.note("teardown: restored user X", status=r.status_code)


def test_revoked_member_is_blocked_on_next_request(a_owner, user_x, org_a, x_is_member_of_a, evidence):
    x = x_is_member_of_a
    project_id = a_owner.get(P.projects(org_a), label="setup: A projects").json()[0]["id"]

    # 1. Trước khi thu hồi: X truy cập được
    before = user_x.get(P.projects(org_a), label="BEFORE revoke: X lists Org A projects")
    assert before.status_code == 200, "Điều kiện ban đầu sai: X phải truy cập được Org A"

    # 2. Owner thu hồi
    revoke = a_owner.delete(P.member(org_a, x["userId"]), label="Owner A removes user X")
    assert revoke.status_code in (200, 204), f"Xóa thành viên thất bại: {revoke.status_code}"
    revoked_at = time.perf_counter()

    # 3. NGAY LẬP TỨC dùng lại đúng JWT cũ của X
    after_read = user_x.get(P.projects(org_a), label="AFTER revoke: X lists Org A projects (same JWT)")
    gap_ms = (time.perf_counter() - revoked_at) * 1000
    evidence.note("Khoảng cách giữa lúc thu hồi và request kế tiếp", gapMs=round(gap_ms, 1))
    assert after_read.status_code == 403, (
        f"LỖ HỔNG: X vẫn đọc được Org A sau khi bị thu hồi ({after_read.status_code}) — "
        "API đang tin JWT/cache thay vì kiểm tra bảng memberships")

    after_write = user_x.post(P.project_tasks(org_a, project_id), json={"title": "written after revoke"},
                              label="AFTER revoke: X tries to create task (same JWT)")
    assert after_write.status_code == 403

    after_export = user_x.post(P.exports(org_a), json={}, label="AFTER revoke: X tries to export")
    assert after_export.status_code == 403

    # 4. JWT vẫn hợp lệ về danh tính (không phải 401) — chứng minh chặn là do membership, không do token hết hạn
    me = user_x.get(P.me(), label="AFTER revoke: X /me still authenticates")
    assert me.status_code == 200, "JWT của X phải vẫn hợp lệ — test cần chứng minh chặn dựa trên DB"
    assert org_a not in [m["tenantId"] for m in me.json()["memberships"]]

    # 5. Hành động thu hồi được ghi Audit Log
    audit = a_owner.get(P.audit_events(org_a), params={"limit": 20}, label="Audit log shows MEMBER_REMOVED")
    assert audit.status_code == 200
    assert any(e.get("entityId") == x["userId"] and "REMOV" in e.get("action", "").upper()
               for e in audit.json()), "Audit log thiếu sự kiện xóa thành viên"


def test_role_downgrade_takes_effect_immediately(a_owner, user_x, org_a, x_is_member_of_a):
    """Bổ sung: đổi role MEMBER -> GUEST cũng phải có hiệu lực ngay."""
    x = x_is_member_of_a
    try:
        r = a_owner.patch(P.member(org_a, x["userId"]), json={"role": "GUEST"}, label="Owner downgrades X to GUEST")
        assert r.status_code == 200
        resp = user_x.post(P.projects(org_a), json={"name": f"x-after-downgrade-{int(time.time())}"},
                           label="AFTER downgrade: X tries to create project")
        assert resp.status_code == 403, f"X đã là GUEST nhưng vẫn ghi được ({resp.status_code})"
        assert user_x.get(P.projects(org_a), label="AFTER downgrade: X can still read").status_code == 200
    finally:
        a_owner.patch(P.member(org_a, x["userId"]), json={"role": "MEMBER"}, label="restore X role MEMBER")
