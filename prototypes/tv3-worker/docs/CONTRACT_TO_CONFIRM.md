# Contract to confirm with the group BEFORE production Worker integration

> This is a proposed integration contract, **not** a finalized team agreement. TV2 must confirm it before implementing `ExportMessageProcessor` or connecting exports to its database.

## TV2 ↔ TV3: Azure Queue payload proposal

Queue names already declared by TV1's Terraform:
- Main: `export-jobs`
- Poison: `export-jobs-poison`

Proposed version 1 queue message (all fields are untrusted):
```json
{
  "schemaVersion": 1,
  "jobId": "00000000-0000-0000-0000-00000000002a",
  "claimedTenantId": "00000000-0000-0000-0000-00000000000a",
  "requestId": "req-123"
}
```

Required by the current parser: `schemaVersion` (integer, currently `1`) and `jobId` (UUID). Optional: `claimedTenantId` (UUID) and `requestId` (non-empty, at most 128 characters). Unknown properties and unsupported versions are rejected. **TV2 must confirm field names, whether claimed tenant is included, requestId propagation, and producer compatibility.**

`claimedTenantId` is never an authority for selecting tenant data. The queue adapter has no database access and does not perform export work. TV2 must provide the authoritative job lookup/membership/claim implementation behind `ExportMessageProcessor`.

## TV2 ↔ TV3: durable processing contract (BLOCKED)

Before returning a durable outcome, the processor must:
1. Load `export_jobs` using `jobId`; derive tenant/user from the authoritative row.
2. Compare the optional claim only as a consistency/security check. On mismatch, do not query tenant tasks or mutate the legitimate job; persist the agreed terminal security result before returning `DURABLY_REJECTED`.
3. Re-check current membership and authorization at execution time.
4. Atomically claim the job and define stale-lease recovery.
5. Query only the verified tenant and define transaction-scoped RLS/connection-pool reset behavior.
6. Return `DURABLY_COMPLETED` or `DURABLY_REJECTED` only after the database transaction has committed. Otherwise return `RETRYABLE_FAILURE` or throw.

The current branch has only the `ExportMessageProcessor` interface. No production implementation is present; therefore no export is performed and the Worker profile fails startup without that bean.

## Queue acknowledgement and retry behavior implemented in the adapter

1. Receive one message using the configured visibility timeout and retain its messageId/popReceipt.
2. Parse/validate the versioned payload. Invalid messages are copied to the poison queue as sanitized metadata (source message ID, error code, dequeue count, timestamp); the source is deleted only after poison enqueue succeeds.
3. For a valid message, renew visibility while the processor runs. Keep the latest popReceipt returned by each renewal.
4. Delete the message only after a durable processor outcome, using the current messageId and latest popReceipt.
5. On retryable outcome/processor exception, update visibility with exponential backoff and jitter; do not delete before the configured dequeue limit.
6. At the limit, enqueue a sanitized poison record first and delete the source only after that send succeeds. If poison send, visibility update, or source delete fails, surface the error; queue redelivery remains possible. A send-success/delete-failure can duplicate a poison record and requires Phase 5 operational handling.

TV2/TV3 must confirm max dequeue count, retry timing, permanent error categories, and whether a durable rejected job should be deleted directly or routed to poison.

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
