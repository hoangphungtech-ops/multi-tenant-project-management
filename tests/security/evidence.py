"""Ghi bằng chứng kiểm thử: input, timestamp, raw HTTP request/response — đã che thông tin mật.

Quy tắc nhóm: ảnh/log đính kèm báo cáo KHÔNG được lọt JWT, mật khẩu, SAS token.
Mọi thứ ghi ra đĩa đều đi qua redact() trước.
"""
from __future__ import annotations

import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import requests

REDACTED = "***REDACTED***"
SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie", "x-ms-client-secret", "ocp-apim-subscription-key"}
SAS_PARAMS = {"sig", "se", "st", "sp", "sv", "sr", "skoid", "sktid", "skt", "ske", "sks", "skv", "spr", "sip"}
SENSITIVE_JSON_KEYS = {"password", "access_token", "id_token", "refresh_token", "client_secret", "token", "secret"}

_JWT_RE = re.compile(r"eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*")
_BEARER_RE = re.compile(r"(Bearer\s+)[^\s\"',]{12,}", re.IGNORECASE)
_SIG_RE = re.compile(r"([?&](?:sig|se|skoid|sktid)=)[^&\s\"']+", re.IGNORECASE)
_CONN_RE = re.compile(r"(AccountKey|SharedAccessSignature|Password|InstrumentationKey)=([^;\s\"']+)", re.IGNORECASE)


def redact_text(text: str) -> str:
    text = _JWT_RE.sub(REDACTED, text)
    text = _BEARER_RE.sub(r"\1" + REDACTED, text)
    text = _SIG_RE.sub(r"\1" + REDACTED, text)
    text = _CONN_RE.sub(r"\1=" + REDACTED, text)
    return text


def redact_url(url: str) -> str:
    parts = urlsplit(url)
    if not parts.query:
        return url
    q = [(k, REDACTED if k.lower() in SAS_PARAMS else v) for k, v in parse_qsl(parts.query, keep_blank_values=True)]
    return urlunsplit(parts._replace(query=urlencode(q, safe="*")))


def redact_obj(obj):
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k.lower() in SENSITIVE_JSON_KEYS:
                out[k] = REDACTED
            elif k.lower() == "url" and isinstance(v, str):
                out[k] = redact_url(v)
            else:
                out[k] = redact_obj(v)
        return out
    if isinstance(obj, list):
        return [redact_obj(v) for v in obj]
    if isinstance(obj, str):
        return redact_text(obj)
    return obj


def redact_headers(headers) -> dict:
    return {k: (REDACTED if k.lower() in SENSITIVE_HEADERS else redact_text(str(v))) for k, v in headers.items()}


def _body(raw: bytes | str | None, content_type: str | None):
    if raw is None or raw == b"" or raw == "":
        return None
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8", errors="replace")
    if content_type and "json" in content_type:
        try:
            return redact_obj(json.loads(raw))
        except json.JSONDecodeError:
            pass
    text = redact_text(raw)
    return text if len(text) <= 4000 else text[:4000] + f"...<truncated {len(text) - 4000} chars>"


class EvidenceRecorder:
    """Một recorder cho mỗi test; ghi JSONL + bản raw HTTP dễ đọc cho báo cáo."""

    def __init__(self, run_dir: Path, test_id: str) -> None:
        safe = re.sub(r"[^\w.\-]+", "_", test_id)[:150]
        self.json_path = run_dir / f"{safe}.jsonl"
        self.http_path = run_dir / f"{safe}.http.txt"
        self.seq = 0
        run_dir.mkdir(parents=True, exist_ok=True)

    def note(self, message: str, **data) -> None:
        entry = {"type": "note", "ts": _ts(), "message": message, "data": redact_obj(data)}
        self._write(entry, f"### NOTE {entry['ts']}  {message}  {json.dumps(entry['data'], ensure_ascii=False)}\n\n")

    def record(self, label: str, resp: requests.Response, started: float, elapsed_ms: float) -> None:
        self.seq += 1
        req = resp.request
        entry = {
            "type": "http", "seq": self.seq, "label": label,
            "requestTimestamp": datetime.fromtimestamp(started, timezone.utc).isoformat(timespec="milliseconds"),
            "responseTimestamp": _ts(), "elapsedMs": round(elapsed_ms, 1),
            "request": {
                "method": req.method, "url": redact_url(req.url),
                "headers": redact_headers(req.headers),
                "body": _body(req.body, req.headers.get("Content-Type")),
            },
            "response": {
                "status": resp.status_code, "headers": redact_headers(resp.headers),
                "body": _body(resp.content, resp.headers.get("Content-Type")),
            },
        }
        raw = (
            f"### [{self.seq}] {label}\n"
            f"# sent {entry['requestTimestamp']}  received {entry['responseTimestamp']}  ({entry['elapsedMs']} ms)\n"
            f"{req.method} {entry['request']['url']}\n"
            + "".join(f"{k}: {v}\n" for k, v in entry["request"]["headers"].items())
            + (f"\n{_dump(entry['request']['body'])}\n" if entry["request"]["body"] is not None else "")
            + f"\n--> HTTP {resp.status_code} {resp.reason}\n"
            + "".join(f"{k}: {v}\n" for k, v in entry["response"]["headers"].items())
            + (f"\n{_dump(entry['response']['body'])}\n" if entry["response"]["body"] is not None else "")
            + "\n"
        )
        self._write(entry, raw)

    def _write(self, entry: dict, raw: str) -> None:
        with self.json_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        with self.http_path.open("a", encoding="utf-8") as f:
            f.write(raw)


class ApiClient:
    """requests.Session gắn token của 1 user; mọi request đều được ghi bằng chứng."""

    def __init__(self, base_url: str, token: str | None, name: str, recorder_getter, timeout: float = 15) -> None:
        self.base_url = base_url.rstrip("/")
        self.name = name
        self.session = requests.Session()
        if token and token.startswith("demo:"):
            # Chỉ cho backend TV2 chạy local ở chế độ demo: danh tính qua header thay cho JWT.
            _, user, tenant = token.split(":", 2)
            self.session.headers["X-Demo-User"] = user
            self.session.headers["X-Demo-Tenant"] = tenant
        elif token:
            self.session.headers["Authorization"] = f"Bearer {token}"
        self.session.headers["Accept"] = "application/json"
        self._recorder = recorder_getter
        self.timeout = timeout
        self._n = 0

    def request(self, method: str, path: str, label: str | None = None, **kw) -> requests.Response:
        self._n += 1
        kw.setdefault("timeout", self.timeout)
        headers = kw.pop("headers", {}) or {}
        headers.setdefault("X-Request-Id", f"tv4-{self.name}-{int(time.time() * 1000)}-{self._n}")
        url = path if path.startswith("http") else self.base_url + path
        started = time.time()
        t0 = time.perf_counter()
        resp = self.session.request(method, url, headers=headers, **kw)
        elapsed = (time.perf_counter() - t0) * 1000
        rec = self._recorder()
        if rec is not None:
            rec.record(label or f"{self.name}: {method} {path}", resp, started, elapsed)
        return resp

    def get(self, path, **kw): return self.request("GET", path, **kw)
    def post(self, path, **kw): return self.request("POST", path, **kw)
    def patch(self, path, **kw): return self.request("PATCH", path, **kw)
    def put(self, path, **kw): return self.request("PUT", path, **kw)
    def delete(self, path, **kw): return self.request("DELETE", path, **kw)


def _ts() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def _dump(body) -> str:
    return body if isinstance(body, str) else json.dumps(body, ensure_ascii=False, indent=2)
