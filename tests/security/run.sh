#!/usr/bin/env bash
# Chạy Security Tests 1–3.
#   bash tests/security/run.sh                          # mock API local (tự bật/tắt)
#   API_TARGET=azure API_BASE_URL=https://app-...azurewebsites.net bash tests/security/run.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export API_TARGET="${API_TARGET:-mock}"
export API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080}"
export PYTHONIOENCODING=utf-8
PY="${PYTHON:-python3}"; command -v "$PY" >/dev/null || PY=python
MOCK_PID=""
if [ "$API_TARGET" = "mock" ]; then
  "$PY" "$HERE/../mock_api/server.py" --port 8080 >/dev/null 2>&1 &
  MOCK_PID=$!
  trap 'kill $MOCK_PID 2>/dev/null || true' EXIT
  sleep 1
fi
cd "$HERE"
set +e
"$PY" -m pytest "$@"
CODE=$?
"$PY" scan_secrets.py evidence
exit $CODE
