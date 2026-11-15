# TV3 Work Breakdown — Jira/GitHub Issues ready

| ID | Priority | Work item | Acceptance criteria | Dependencies |
|---|---|---|---|---|
| TV3-01 | P0 | Export flow, contracts and threat model | Queue JSON + status transitions + permitted scope agreed in writing | TV2 |
| TV3-02 | P0 | Java 21 Worker Spring profile skeleton | API/Worker profiles separated within **shared codebase**; health check | TV2 repo |
| TV3-03 | P0 | Queue consumer + secure job validation | Legit job accepted; forged tenant rejected with no task read | TV1 queue, TV2 schema |
| TV3-04 | P0 | CSV + private Blob upload | Output only verified tenant data, deterministic path, valid escaping | TV1 blob/MI, TV2 tasks |
| TV3-05 | P0 | Idempotency and queue retry safety | Duplicate job no duplicate logical export; poison route, failure recovery | TV2 DB lease |
| TV3-06 | P1 | Structured JSON logs and secret redaction | jobId, tenantId, requestId correlation; no JWT/password/SAS | App config |
| TV3-07 | P1 | App Insights custom metrics and 4 charts | Export success/fail, mismatch, retries, duration/queue | TV1 IaC |
| TV3-08 | P1 | Alert and worker forged-tenant drill | Proof of reject + metric + notification email with timestamps | TV1 alert wiring |
| TV3-09 | P1 | k6 2–3 minute run and p95 report | CSV/JSON k6 evidence: setup, latency p95 and HTTP error rate | TV2 API, test environment |
| TV3-10 | P1 | Technical readme, demo and PR review | Reproducible steps, decision log, clean PR | All |

## Suggested delivery milestones
- **Week 1:** Read scope; agree queue job contract; run this starter security self-test.
- **Week 2:** Sync schema; set shared `worker` Spring profile; Azurite local connection with TV2.
- **Weeks 3–4:** End-to-end enqueue → worker → private Blob → complete job; security/dup/retry tests; internal Azure trial with TV1.
- **Weeks 5–6:** JSON logs, metric alert, 4 charts, failure drill and k6; save actual evidence.
- **Weeks 7–8:** technical report, screenshots with secrets redacted, rehearsal and final demo.

## Evidence checklist
- PRs reviewed and merged; tests executed and timestamped.
- Local E2E request + DB state + blob path + queue ACK.
- Forged message with two tenants; log and metric prove no cross-tenant read.
- Email alert screenshot (including real receipt time, not assumed 1–2 min).
- 4 dashboard charts, threshold/alert config screenshot.
- k6 summary shows p95, error rate, VU and workload; baseline vs higher load.
- Design explanation: at-least-once messages, idempotency, RLS, pooling, poison queue, cost.
