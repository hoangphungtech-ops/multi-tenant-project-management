"""Tự kiểm chứng bộ test (test-the-tests) trên mock API.

1. Mock AN TOÀN      -> toàn bộ Test 1–3 phải PASS.
2. Mock có lỗ hổng X -> test tương ứng phải FAIL (chứng minh test thực sự phát hiện lỗi, không "luôn xanh").

Chạy:  python tests/security/selfcheck.py
"""
from __future__ import annotations

import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SERVER = HERE.parent / "mock_api" / "server.py"

SCENARIOS = [
    (None, None),
    ("idor", "test_1_cross_tenant_idor.py"),
    ("stale-membership", "test_2_membership_revocation.py"),
    ("global-unique", "test_3_duplicate_name_cache.py"),
    ("shared-cache", "test_3_duplicate_name_cache.py"),
]


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_up(url: str) -> None:
    for _ in range(50):
        try:
            urllib.request.urlopen(url + "/health", timeout=1)
            return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("mock API không khởi động được")


def run(vuln: str | None, test_file: str | None) -> bool:
    port = free_port()
    cmd = [sys.executable, str(SERVER), "--port", str(port)] + (["--vuln", vuln] if vuln else [])
    srv = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        base = f"http://127.0.0.1:{port}"
        wait_up(base)
        env = {**os.environ, "API_BASE_URL": base, "API_TARGET": "mock",
               "EVIDENCE_DIR": tempfile.mkdtemp(prefix=f"selfcheck-{vuln or 'secure'}-"),
               "PYTHONIOENCODING": "utf-8"}
        args = [sys.executable, "-m", "pytest", "-q", "--no-header"] + ([test_file] if test_file else [])
        res = subprocess.run(args, cwd=HERE, env=env, capture_output=True, text=True, encoding="utf-8")
        tail = (res.stdout.strip().splitlines() or ["?"])[-1]
        if vuln is None:
            ok = res.returncode == 0
            print(f"[{'OK ' if ok else 'BAD'}] secure mock        -> tests must PASS : {tail}")
        else:
            ok = res.returncode == 1  # 1 = có test FAIL; 2+ = lỗi cấu hình/collection, không tính
            print(f"[{'OK ' if ok else 'BAD'}] --vuln {vuln:<16} -> {test_file} must FAIL : {tail}")
        if not ok:
            print(res.stdout[-3000:])
        return ok
    finally:
        srv.terminate()
        srv.wait(timeout=5)


if __name__ == "__main__":
    results = [run(v, f) for v, f in SCENARIOS]
    print(f"\nSelfcheck: {sum(results)}/{len(results)} scenarios behaved as expected")
    sys.exit(0 if all(results) else 1)
