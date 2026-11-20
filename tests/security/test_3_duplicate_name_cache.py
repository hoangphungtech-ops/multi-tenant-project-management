"""TEST 3 — Trùng tên Project giữa 2 tenant & tách biệt Cache.

Org A và Org B cùng tạo project tên "Cloud Project". Kỳ vọng:
  * Cả hai tạo thành công (UNIQUE(tenant_id, name), không phải UNIQUE(name)).
  * Trong CÙNG một tenant, tạo trùng tên lần 2 -> 409.
  * Danh sách project (đã qua cache, khóa tenant:{id}:projects) của mỗi org chỉ chứa dữ liệu org đó,
    kể cả khi cache đã "ấm" trước khi tạo và sau khi một org thay đổi dữ liệu.
"""
import pytest

import api_paths as P


def _cleanup(client, org, name):
    for p in client.get(P.projects(org), label=f"{client.name}: cleanup scan").json():
        if p["name"].lower() == name.lower():
            client.delete(P.project(org, p["id"]), label=f"{client.name}: cleanup '{name}'")


@pytest.fixture
def name(settings, a_owner, b_owner, org_a, org_b):
    n = settings["cloud_project_name"]
    _cleanup(a_owner, org_a, n)
    _cleanup(b_owner, org_b, n)
    yield n
    _cleanup(a_owner, org_a, n)
    _cleanup(b_owner, org_b, n)


def _ids(resp):
    assert resp.status_code == 200
    return {p["id"] for p in resp.json()}


def test_same_project_name_in_two_tenants(a_owner, b_owner, org_a, org_b, name, evidence):
    # 0. Làm "ấm" cache của cả 2 org trước khi tạo
    a_owner.get(P.projects(org_a), label="warm cache A")
    b_owner.get(P.projects(org_b), label="warm cache B")

    # 1. Cả hai tạo cùng tên -> đều thành công
    ra = a_owner.post(P.projects(org_a), json={"name": name}, label=f"A creates '{name}'")
    rb = b_owner.post(P.projects(org_b), json={"name": name}, label=f"B creates '{name}'")
    assert ra.status_code == 201, f"Org A không tạo được '{name}': {ra.status_code} {ra.text[:200]}"
    assert rb.status_code == 201, (
        f"Org B bị chặn tạo '{name}' ({rb.status_code}) — ràng buộc UNIQUE đang là toàn cục thay vì (tenant_id, name)")
    pa, pb = ra.json(), rb.json()
    assert pa["id"] != pb["id"], "Hai tenant dùng chung 1 bản ghi project!"
    if "tenantId" in pa:
        assert pa["tenantId"] == org_a and pb["tenantId"] == org_b
    evidence.note("Two distinct DB records", projectA=pa["id"], projectB=pb["id"])

    # 2. Trùng tên trong CÙNG tenant vẫn bị chặn
    dup = a_owner.post(P.projects(org_a), json={"name": name}, label=f"A creates '{name}' again (same tenant)")
    assert dup.status_code == 409, f"Trùng tên trong cùng tenant phải 409, nhận {dup.status_code}"

    # 3. Đọc 2 lần (lần 2 chắc chắn từ cache) — mỗi org chỉ thấy bản ghi của mình
    for attempt in (1, 2):
        ids_a = _ids(a_owner.get(P.projects(org_a), label=f"A lists projects (read #{attempt})"))
        ids_b = _ids(b_owner.get(P.projects(org_b), label=f"B lists projects (read #{attempt})"))
        assert pa["id"] in ids_a and pb["id"] not in ids_a, f"Cache/list của Org A lẫn dữ liệu Org B (read #{attempt})"
        assert pb["id"] in ids_b and pa["id"] not in ids_b, f"Cache/list của Org B lẫn dữ liệu Org A (read #{attempt})"
        assert not (ids_a & ids_b), "Hai danh sách có phần tử chung"

    # 4. A thay đổi -> cache A bị invalidate, B không bị ảnh hưởng
    upd = a_owner.patch(P.project(org_a, pa["id"]), json={"description": "changed by A"}, label="A updates its project")
    assert upd.status_code == 200
    b_view = b_owner.get(P.project(org_b, pb["id"]), label="B reads its project after A's update")
    assert b_view.status_code == 200 and b_view.json().get("description") != "changed by A"

    deleted = a_owner.delete(P.project(org_a, pa["id"]), label="A deletes its 'Cloud Project'")
    assert deleted.status_code in (200, 204)
    assert pa["id"] not in _ids(a_owner.get(P.projects(org_a), label="A list after delete (cache invalidated)"))
    assert pb["id"] in _ids(b_owner.get(P.projects(org_b), label="B list after A deletes (B unaffected)"))
