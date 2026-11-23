"""Dựng backend thật của TV2 (backend-core-api) trên máy local để TV4 chạy Security Test.

Chỉ dùng Docker (không cần cài Java/Maven). Tạo các container riêng tiền tố `tv4-qa-`,
không đụng tới docker-compose của TV2. DB tạo mới mỗi lần `start`, nên seed luôn sạch.

    python tests/backend_local/tv2_backend.py start    # build image + chạy PostgreSQL + API (http://127.0.0.1:18080)
    python tests/backend_local/tv2_backend.py status
    python tests/backend_local/tv2_backend.py stop     # xóa container + network (không còn dữ liệu)

Lưu ý bảo mật: API chạy ở chế độ demo (APP_SECURITY_DEMO_MODE=true, header X-Demo-User)
CHỈ trên máy local. Trên Azure, Terraform đặt chế độ này = false.
"""
from __future__ import annotations

import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NET = "tv4-qa-net"
DB = "tv4-qa-postgres"
AZURITE = "tv4-qa-azurite"
API = "tv4-qa-api"
IMAGE = "tv4-qa/tv2-api:local"
PORT = 18080
DB_ENV = {"POSTGRES_DB": "backenddb", "POSTGRES_USER": "backend", "POSTGRES_PASSWORD": "local_dev_only"}
# Issuer Entra có thật (tenant "consumers") để Spring tải được metadata khi khởi động;
# ở chế độ demo, JWT không được dùng để xác thực.
ISSUER = "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"


def docker(*args: str, check: bool = True, quiet: bool = False) -> subprocess.CompletedProcess:
    res = subprocess.run(["docker", *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and res.returncode != 0:
        sys.exit(f"docker {' '.join(args[:2])} lỗi:\n{res.stderr.strip()[-1500:]}")
    if not quiet and res.stdout.strip():
        print(res.stdout.strip()[-500:])
    return res


def stop() -> None:
    for name in (API, AZURITE, DB):
        docker("rm", "-f", name, check=False, quiet=True)
    docker("network", "rm", NET, check=False, quiet=True)
    print("Đã dừng và xóa container tv4-qa-*")


def wait(desc: str, fn, timeout: int) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if fn():
            print(f"✓ {desc}")
            return
        time.sleep(2)
    logs = docker("logs", "--tail", "60", API, check=False, quiet=True)
    sys.exit(f"✗ Hết thời gian chờ: {desc}\n{logs.stdout[-3000:]}{logs.stderr[-3000:]}")


def api_healthy() -> bool:
    try:
        return urllib.request.urlopen(f"http://127.0.0.1:{PORT}/actuator/health", timeout=3).status == 200
    except OSError:
        return False


def start() -> None:
    stop()
    if docker("image", "inspect", IMAGE, check=False, quiet=True).returncode != 0:
        print("Build image backend TV2 (lần đầu mất vài phút)...")
        docker("build", "-t", IMAGE, str(ROOT / "backend-core-api"), quiet=True)
    docker("network", "create", NET, quiet=True)
    env = [x for k, v in DB_ENV.items() for x in ("-e", f"{k}={v}")]
    docker("run", "-d", "--name", DB, "--network", NET, *env, "postgres:16", quiet=True)
    wait("PostgreSQL sẵn sàng", lambda: docker("exec", DB, "pg_isready", "-U", "backend", "-d", "backenddb",
                                               check=False, quiet=True).returncode == 0, 90)
    # Backend TV2 bắt buộc có Blob/Queue client khi khởi động -> dùng Azurite (giả lập Azure Storage).
    docker("run", "-d", "--name", AZURITE, "--network", NET, "mcr.microsoft.com/azure-storage/azurite:3.33.0",
           "azurite", "--blobHost", "0.0.0.0", "--queueHost", "0.0.0.0", "--skipApiVersionCheck", quiet=True)
    api_env = {
        "AZURE_STORAGE_CONNECTION_STRING": f"UseDevelopmentStorage=true;DevelopmentStorageProxyUri=http://{AZURITE}",
        "DB_URL": f"jdbc:postgresql://{DB}:5432/backenddb",
        "DB_USERNAME": DB_ENV["POSTGRES_USER"],
        "DB_PASSWORD": DB_ENV["POSTGRES_PASSWORD"],
        "APP_SECURITY_DEMO_MODE": "true",
        "ENTRA_ISSUER_URI": ISSUER,
        "CORS_ALLOWED_ORIGINS": "http://localhost:5173",
    }
    env = [x for k, v in api_env.items() for x in ("-e", f"{k}={v}")]
    docker("run", "-d", "--name", API, "--network", NET, "-p", f"127.0.0.1:{PORT}:8080", *env, IMAGE, quiet=True)
    wait(f"API TV2 sẵn sàng tại http://127.0.0.1:{PORT}", api_healthy, 180)


def status() -> None:
    docker("ps", "--filter", "name=tv4-qa-", "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}")
    print("API health:", "OK" if api_healthy() else "không phản hồi")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    {"start": start, "stop": stop, "status": status}.get(cmd, status)()
