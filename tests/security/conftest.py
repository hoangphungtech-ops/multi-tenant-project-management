"""Cấu hình chung cho bộ Automated API Security Tests (Test 1–3).

Nguồn cấu hình (ưu tiên từ trên xuống):
  1. Biến môi trường
  2. tests/security/tokens.local.json  (đã .gitignore — KHÔNG commit)
  3. Mặc định cho mock API (API_TARGET=mock)

Biến:
  API_BASE_URL     https://app-...-api-xxxx.azurewebsites.net   (mặc định http://127.0.0.1:8080)
  API_TARGET       mock | local | azure   (chỉ để gắn nhãn bằng chứng; mock => dùng token giả lập)
  TOKEN_A_OWNER    JWT của Owner Org A
  TOKEN_A_GUEST    JWT của Guest Org A
  TOKEN_X          JWT của User X (MEMBER Org A, sẽ bị thu hồi ở Test 2)
  TOKEN_B_OWNER    JWT của Owner Org B
  ORG_A_ID / ORG_B_ID  (tùy chọn) nếu Owner thuộc nhiều org
  CLOUD_PROJECT_NAME   (mặc định "Cloud Project")
"""
from __future__ import annotations

import json
import os
import platform
from datetime import datetime, timezone
from pathlib import Path

import pytest

import api_paths as P
from evidence import ApiClient, EvidenceRecorder

HERE = Path(__file__).parent
MOCK_TOKENS = {
    "TOKEN_A_OWNER": "mock-a-owner",
    "TOKEN_A_GUEST": "mock-a-guest",
    "TOKEN_X": "mock-a-member3",
    "TOKEN_B_OWNER": "mock-b-owner",
}

_current_recorder: EvidenceRecorder | None = None
_results: list[dict] = []
_run_dir: Path | None = None


def _setting(name: str, default: str | None = None) -> str | None:
    if os.environ.get(name):
        return os.environ[name]
    local = HERE / "tokens.local.json"
    if local.exists():
        value = json.loads(local.read_text(encoding="utf-8")).get(name)
        if value:
            return value
    if os.environ.get("API_TARGET", "mock") == "mock" and name in MOCK_TOKENS:
        return MOCK_TOKENS[name]
    return default


@pytest.fixture(scope="session")
def settings() -> dict:
    cfg = {
        "base_url": _setting("API_BASE_URL", "http://127.0.0.1:8080"),
        "target": os.environ.get("API_TARGET", "mock"),
        "cloud_project_name": _setting("CLOUD_PROJECT_NAME", "Cloud Project"),
    }
    missing = [k for k in MOCK_TOKENS if not _setting(k)]
    if missing:
        pytest.exit(f"Thiếu token: {', '.join(missing)} (đặt biến môi trường hoặc tokens.local.json)", 2)
    return cfg


@pytest.fixture(scope="session")
def run_dir(settings) -> Path:
    global _run_dir
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    d = Path(os.environ.get("EVIDENCE_DIR") or HERE / "evidence" / f"{stamp}-{settings['target']}")
    d.mkdir(parents=True, exist_ok=True)
    _run_dir = d
    (d / "run-info.json").write_text(json.dumps({
        "startedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "target": settings["target"], "baseUrl": settings["base_url"],
        "python": platform.python_version(), "host": platform.node(),
    }, indent=2), encoding="utf-8")
    return d


@pytest.fixture(autouse=True)
def evidence(request, run_dir):
    global _current_recorder
    _current_recorder = EvidenceRecorder(run_dir, request.node.nodeid.split("::", 1)[-1])
    _current_recorder.note("TEST START", nodeid=request.node.nodeid)
    yield _current_recorder
    _current_recorder.note("TEST END")
    _current_recorder = None


def _client(settings, token_key: str, name: str) -> ApiClient:
    return ApiClient(settings["base_url"], _setting(token_key), name, lambda: _current_recorder)


@pytest.fixture(scope="session")
def a_owner(settings): return _client(settings, "TOKEN_A_OWNER", "A-owner")


@pytest.fixture(scope="session")
def a_guest(settings): return _client(settings, "TOKEN_A_GUEST", "A-guest")


@pytest.fixture(scope="session")
def user_x(settings): return _client(settings, "TOKEN_X", "user-X")


@pytest.fixture(scope="session")
def b_owner(settings): return _client(settings, "TOKEN_B_OWNER", "B-owner")


@pytest.fixture(scope="session")
def anonymous(settings): return ApiClient(settings["base_url"], None, "anonymous", lambda: _current_recorder)


def _owned_org(client: ApiClient, override: str | None) -> str:
    r = client.get(P.me(), label=f"{client.name}: discover tenant via /me")
    assert r.status_code == 200, f"/me của {client.name} trả {r.status_code}: token sai hoặc API chưa chạy"
    if override:
        return override
    owned = [m["tenantId"] for m in r.json()["memberships"] if m["role"] == "OWNER"]
    assert len(owned) == 1, f"{client.name} là OWNER của {len(owned)} org — hãy đặt ORG_A_ID/ORG_B_ID"
    return owned[0]


@pytest.fixture(scope="session")
def org_a(a_owner) -> str:
    return _owned_org(a_owner, _setting("ORG_A_ID"))


@pytest.fixture(scope="session")
def org_b(b_owner, org_a) -> str:
    b = _owned_org(b_owner, _setting("ORG_B_ID"))
    assert b != org_a, "Org A và Org B phải là 2 tổ chức khác nhau"
    return b


@pytest.fixture(scope="session")
def org_b_resources(b_owner, org_b) -> dict:
    """ID thật của Org B (lấy bằng token hợp lệ của B) để Org A thử truy cập trái phép."""
    r = b_owner.get(P.projects(org_b), label="B-owner: list projects of Org B (setup)")
    assert r.status_code == 200 and r.json(), "Org B cần có ít nhất 1 project (chạy seed script)"
    project = r.json()[0]
    r = b_owner.get(P.project_tasks(org_b, project["id"]), label="B-owner: list tasks of Org B (setup)")
    assert r.status_code == 200 and r.json(), "Project của Org B cần có task (chạy seed script)"
    task = r.json()[0]
    r = b_owner.post(P.exports(org_b), json={"projectId": project["id"]}, label="B-owner: create export in Org B (setup)")
    assert r.status_code in (200, 201, 202), f"Không tạo được export job cho Org B: {r.status_code}"
    return {"project": project, "task": task, "job_id": r.json()["jobId"]}


# ---------- tổng hợp kết quả ----------
def pytest_runtest_makereport(item, call):
    if call.when == "call" or (call.when == "setup" and call.excinfo is not None):
        _results.append({
            "test": item.nodeid, "phase": call.when,
            "outcome": "passed" if call.excinfo is None else "failed",
            "durationSec": round(call.duration, 3),
            "finishedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
            "error": None if call.excinfo is None else str(call.excinfo.value)[:500],
        })


def pytest_sessionfinish(session, exitstatus):
    if _run_dir is None or not _results:
        return
    passed = sum(r["outcome"] == "passed" for r in _results)
    summary = {"passed": passed, "failed": len(_results) - passed, "results": _results}
    (_run_dir / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    lines = ["| Test | Kết quả | Thời gian (s) |", "|---|---|---|"]
    lines += [f"| `{r['test']}` | {'✅ PASS' if r['outcome'] == 'passed' else '❌ FAIL'} | {r['durationSec']} |" for r in _results]
    (_run_dir / "SUMMARY.md").write_text(
        f"# Kết quả Security Tests\n\nPASS {passed} / {len(_results)}\n\n" + "\n".join(lines) + "\n", encoding="utf-8")
