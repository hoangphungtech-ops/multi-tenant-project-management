# TV3 Observability & Failure Drill — planned (not deployed)

## Suggested four dashboard charts
1. **Export Jobs by Result:** success / transient failure / security rejection over time.
2. **Tenant Mismatch Detection:** `worker_tenant_mismatch_total` counter (alert signal).
3. **Worker Processing Duration:** p95 export job processing duration (not HTTP p95).
4. **Queue/Retry Health:** retry counts, poison queue length or oldest message age.

> Microsoft Application Insights Java agent can autocollect Micrometer counters and Spring Boot Actuator metrics. Confirm emitted metric names in the actual workspace before writing alert filters. Avoid putting jobId or tenantId into metrics dimensions; use them in controlled logs instead to limit cardinality and cost.

## Proposed sanitized JSON log
```json
{"level":"WARN","event":"worker_tenant_mismatch","requestId":"req-123","jobId":"job-a","authoritativeTenantId":"tenant-a","result":"REJECTED"}
```
Log actual IDs ONLY according to team's privacy policy; do not log access tokens, passwords, connection strings, SAS, CSV content, raw queue payload or exception messages that might include secrets.

## Failure drill recipe
1. Seed two independent tenants A and B; create export_job A in DB.
2. Enqueue message for job A but claim tenant B.
3. Expect: `DROP_FORGED`, no task SELECT for tenant B, no private CSV produced, legitimate job A remains pending.
4. Expect: sanitized warning log, counter increment and alert condition met.
5. Verify Action Group email arrived; record real creation/ingestion/evaluation/receipt timestamps.
6. Re-enqueue correct claim A and verify normal success (no permanent denial-of-service).

## Alert design notes
- TV3 supplies metric name and condition; TV1 manages Terraform alert and Action Group.
- Alert delivery time is not guaranteed to be 1–2 minutes; measure it.
- Azure Storage Queue does **not** offer automatic dead-lettering. Consumer moves poison messages after a dequeue-count threshold.
- K6 HTTP p95 and Worker processing p95 are **different measurements**.

## Sources
- https://learn.microsoft.com/en-us/azure/app-service/monitor-app-service
- https://learn.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-config
- https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-azure-and-service-bus-queues-compared-contrasted
- https://grafana.com/docs/k6/latest/using-k6/thresholds/
