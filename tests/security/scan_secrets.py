"""Quét thư mục bằng chứng / log trước khi đính kèm báo cáo: phát hiện JWT, SAS signature, key, mật khẩu.

Chạy:  python tests/security/scan_secrets.py <thư-mục-hoặc-file> [...]
Exit code 1 nếu phát hiện nghi vấn. (Ảnh chụp màn hình vẫn phải tự kiểm tra bằng mắt.)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

PATTERNS = {
    "JWT": re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"),
    "Bearer token": re.compile(r"Bearer\s+(?!\*\*\*REDACTED)[A-Za-z0-9._~+/-]{20,}", re.IGNORECASE),
    "SAS signature": re.compile(r"[?&]sig=(?!\*\*\*REDACTED|%2A%2A%2A)[A-Za-z0-9%+/=]{10,}", re.IGNORECASE),
    "Storage AccountKey": re.compile(r"AccountKey=(?!\*\*\*REDACTED)[A-Za-z0-9+/=]{20,}", re.IGNORECASE),
    "Password field": re.compile(r"\"?(password|pwd|db_admin_password)\"?\s*[:=]\s*\"?(?!\*\*\*REDACTED)[^\s\",]{6,}", re.IGNORECASE),
    "Private key": re.compile(r"-----BEGIN (RSA |EC )?PRIVATE KEY-----"),
}
TEXT_EXT = {".txt", ".json", ".jsonl", ".log", ".md", ".http", ".har", ".csv", ".yml", ".yaml", ".env"}


def scan(path: Path) -> list[tuple[Path, int, str]]:
    hits = []
    files = [path] if path.is_file() else [p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in TEXT_EXT]
    for f in files:
        try:
            lines = f.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        for no, line in enumerate(lines, 1):
            for name, rx in PATTERNS.items():
                if rx.search(line):
                    hits.append((f, no, name))
    return hits


if __name__ == "__main__":
    targets = [Path(a) for a in sys.argv[1:]] or [Path(__file__).parent / "evidence"]
    found = [h for t in targets if t.exists() for h in scan(t)]
    for f, no, name in found:
        print(f"[LEAK?] {name:<18} {f}:{no}")
    print(f"Đã quét {', '.join(map(str, targets))}: {len(found)} nghi vấn")
    sys.exit(1 if found else 0)
