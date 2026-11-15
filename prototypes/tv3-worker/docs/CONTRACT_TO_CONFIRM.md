# Contract to confirm with the group BEFORE Azure SDK/JDBC integration

> This is a proposed integration contract, **not** a finalized team agreement. TV2 should lock schema & endpoints first (end of week 2 according to group document).

## TV2 ↔ TV3: authoritative job record & tenant isolation

Proposed queue message (JSON, untrusted contents):
```json
{"jobId":"00000000-0000-0000-0000-00000000002a","claimedTenantId":"00000000-0000-0000-0000-00000000000a","requestId":"req-123"}
```

The worker should:
1. Read job by jobId from authoritative `export_jobs` table.
2. Compare `claimedTenantId` with stored `tenant_id`. On mismatch: increment mismatch counter and reject without querying tasks, without mutating legitimate job.
3. Re-check `memberships` at execution time (requested_by_user_id, tenant_id, active status).
4. Atomically claim job; handle timed-out leases before retry. Queue delivery may be at least once.
5. Query tasks only with verified DB tenant ID; enforce RLS per PostgreSQL transaction and reset tenant context on pooled connections.
6. Upload `exports/{tenant_id}/{job_id}.csv` into private Blob container (never public).
7. Mark complete with blob path; do not emit SAS tokens into logs.
8. ACK/Delete queue message **only** after a successful/terminal decision; retry transients. After max dequeue count move to explicit `exports-poison` queue.

Confirm SQL names, `export_jobs` status transitions, `requested_by`, membership deletion semantics, lease timeout/locks, CSV columns, `request_id`, error codes, number of retries and event logging. Do not infer these from the high-level document.

## TV1 ↔ TV3: infrastructure interfaces

Confirm queue name, poison queue name, blob container name, managed identity access scope, Application Insights connection, metric namespace, Terraform resource names and whether alert provisioned by IaC or cloud portal.

**Responsibility:** TV1 provisions through Terraform; TV3 defines detection signal, dashboard charts, failure drill, alert validation and test evidence; both review PR.

## TV4 ↔ TV3: frontend and test interfaces

Confirm `POST /exports`, `GET /exports/{jobId}`, success/error state model, and authenticated endpoint returning a short-lived download URL. Frontend must not receive a storage account key. TV4 should not generate SAS; TV2 owns API authorization and token generation according to current division.

## Security decisions requiring group approval

- Should current membership be revalidated on worker execution? Current group doc says yes.
- What happens to a queued export if membership was revoked? Propose permanent reject with reason.
- What is a user's permitted export scope? Beyond membership alone, agree project/task filtering.
- How long does a processing lease last, and who can re-claim after crash?
- How is worker's job-by-ID lookup handled with RLS? Need carefully scoped worker identity; no blanket bypass of tenant isolation.
- If a user requests 100k tasks, use streaming/pagination, not entire `List<Task>` in memory as starter does.
