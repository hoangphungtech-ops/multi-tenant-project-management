# TV3 — Export Worker, Observability & Load Testing

**Project:** Multi-Tenant Project Management SaaS on Microsoft Azure
**Stack:** Java 21, Spring Boot 3 (team integration), PostgreSQL 16, Azure Queue Storage, Azure Blob Storage, Application Insights, k6.

## Scope and current status

This is an **independently runnable Java 21 proof of the Export Worker SECURITY DOMAIN LOGIC**. It is intentionally **not** a completed Azure deployment or Spring Boot application. It can be started while TV2 finalizes DB schema / API contracts and TV1 provisions Azure.

Implemented and locally tested:
- Reject altered `claimedTenantId` by comparing with DB-authoritative export job's tenant ID.
- Check the requesting user's current tenant membership before export.
- Query only tasks belonging to the verified tenant (port contract; actual SQL/RLS is still TV2's responsibility).
- Build CSV with escaping and spreadsheet formula-injection mitigation.
- Generate deterministic blob path `exports/{tenantId}/{jobId}.csv` to support retries.
- Skip already completed jobs; retry recoverable upload failures.
- Provide self-tests with two distinct tenants and explicit negative security scenarios.

**Not yet implemented:** real JDBC repository, real Azure Queue/Blob adapters, distributed job leases, poison-queue handling, Azure Monitor dashboard, Azure alert delivery, real cloud evidence. These require integration and must not be presented as completed.

## Run the test locally (JDK 21; no Maven required)

### Windows PowerShell
```powershell
cd TV3_Cloud_Azure_Starter
.\scripts\run-tests.ps1
```

### Bash (macOS/Linux/Git Bash)
```bash
bash scripts/run-tests.sh
```

Expected output: `5/5 TV3 security & reliability tests passed`.

## Begin team integration

1. Send `docs/CONTRACT_TO_CONFIRM.md` to TV2, TV1 and TV4 for one-time sign-off.
2. Place core classes into team's shared Spring Boot codebase; run as `spring.profiles.active=worker` (do not create a conflicting second backend application).
3. Implement ports with Spring JDBC/JPA and Azure SDK Java; use private Blob containers, least-privilege Managed Identity in Azure, and Azurite locally.
4. Ensure DB calls with tenant data enforce tenant-scoped SQL/RLS, including protection against JDBC connection-pool tenant-context leakage.
5. Build queue poller: message visibility timeout, delete only for terminal/successful results, safe retries, and explicit poison queue after max dequeue count.
6. Connect Micrometer counters to App Insights, create dashboard/metric alert with TV1's Terraform, and run failure drill; archive actual screenshot/HTTP/log evidence.
7. Coordinate with TV4 for security test cases and with TV2 for authenticated API load testing; `k6/api-load.js` is parameterized for endpoints not yet finalized.

## Repository layout

```text
src/main/java/.../ExportWorker.java     # Core tenant verification & retry
src/main/java/.../CsvBuilder.java       # Safe CSV output
src/test/java/.../ExportWorkerSelfTest.java # Runnable security tests
k6/api-load.js                          # Parameterized 2.5-minute test
scripts/run-tests.ps1                   # Windows test runner
scripts/run-tests.sh                    # Unix test runner
docs/CONTRACT_TO_CONFIRM.md             # Mandatory cross-member contract
docs/WORK_ITEMS.md                      # Task breakdown, DoD, milestones
docs/OBSERVABILITY.md                   # Metrics / failure drill plan
docs/WORKER_SEQUENCE.puml               # Sequence diagram source
```

## Project boundaries
- TV1 owns Terraform/Azure resources/CI-CD provisioning; TV3 owns telemetry definitions, dashboard content and validation with TV1.
- TV2 owns schema/RLS/identity/core API; TV3 owns Worker and implementation/testing of queue consumption, plus shared export contract.
- TV4 owns frontend/tests 1–3/final design report; TV3 owns worker failure drill and load testing evidence.

See `docs/WORK_ITEMS.md` for accountable outputs and `docs/CONTRACT_TO_CONFIRM.md` for dependencies.
