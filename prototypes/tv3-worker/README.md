# TV3 — Export Worker, Observability & Load Testing

**Project:** Multi-Tenant Project Management SaaS on Microsoft Azure
**Stack:** Java 21, Spring Boot 3 (team integration), PostgreSQL 16, Azure Queue Storage, Azure Blob Storage, Application Insights, k6.

## Scope and current status

This is an independently runnable proof of the Export Worker security logic. The original framework-free prototype remains available through its standalone test scripts. A separate Spring Boot worker application is being added in this directory so it can later be integrated into the team's shared backend without creating a competing API or schema. This is not a completed Azure deployment.

Implemented and locally tested:
- Reject altered `claimedTenantId` by comparing with DB-authoritative export job's tenant ID.
- Check the requesting user's current tenant membership before export.
- Query only tasks belonging to the verified tenant (port contract; actual SQL/RLS is still TV2's responsibility).
- Build CSV with escaping and spreadsheet formula-injection mitigation.
- Generate deterministic blob path `exports/{tenantId}/{jobId}.csv` to support retries.
- Skip already completed jobs; retry recoverable upload failures.
- Provide self-tests with two distinct tenants and explicit negative security scenarios.

**Not yet implemented or verified:** authoritative JDBC repository, Blob adapter, and TV2 `ExportMessageProcessor`. The Azure Queue adapter has unit coverage but Azurite/Azure connectivity has not been tested. Distributed database leases, Azure Monitor dashboard/alerts, and cloud evidence also remain pending.

## Run the prototype regression tests (JDK 21+; no Maven required)

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

## Spring Boot worker application

The Maven application uses Java 21 bytecode (`maven.compiler.release=21`), Spring Boot 3, and a non-web `worker` profile. The Azure Queue SDK adapter supports `azurite` (connection string) and `azure` (Managed Identity). The message parser and poller are implemented, but the worker still fails closed until TV2 supplies a production `ExportMessageProcessor` that verifies the authoritative job/tenant and persists a terminal state. No export or successful ACK is simulated.

```powershell
cd prototypes\tv3-worker
mvn test
mvn package
```

### Azurite local configuration

1. Start Azurite Queue on port `10001` (requires Docker Desktop/Engine):
   ```powershell
   docker run --rm -p 10001:10001 mcr.microsoft.com/azure-storage/azurite azurite-queue --queueHost 0.0.0.0
   ```
2. In another PowerShell session, configure the Azurite connection string without committing it:
   ```powershell
   Copy-Item .env.example .env
   $env:AZURE_STORAGE_CONNECTION_STRING = '<your Azurite connection string with QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1>'
   $env:SPRING_PROFILES_ACTIVE = 'worker,azurite'
   ```
   The `azurite` profile creates the two local queues if missing. The `.env` file is git-ignored; the application reads the connection string only from the environment.
3. After the TV2 processor is integrated, start the worker:
   ```powershell
   mvn spring-boot:run
   ```

Current required environment variables:

| Variable | Profile | Purpose |
|---|---|---|
| `AZURE_STORAGE_CONNECTION_STRING` | `worker,azurite` | Local Azurite Queue connection string; no default credential is embedded |
| `AZURE_STORAGE_QUEUE_ENDPOINT` | `worker,azure` | Azure Storage Queue endpoint, for example `https://<account>.queue.core.windows.net`; authentication uses Managed Identity |
| `SPRING_PROFILES_ACTIVE` | local/Azure | Select `worker,azurite` or `worker,azure` |

Azure queues must already exist (TV1 Terraform provisions them); the Azure profile does not create infrastructure. The managed identity needs least-privilege Queue Data permissions. Provide a Java 21 runtime for the final runtime check. Azurite/network integration has not been run in this environment.

## Begin team integration

1. Send `docs/CONTRACT_TO_CONFIRM.md` to TV2, TV1 and TV4 for one-time sign-off; TV2 confirmation is still required for the proposed queue payload and processor contract.
2. Integrate the worker module into the team's shared Spring Boot codebase when TV2 provides it; run with `worker,azurite` locally or `worker,azure` in Azure (do not create a conflicting second backend application).
3. Implement ports with Spring JDBC/JPA and Azure SDK Java; use private Blob containers, least-privilege Managed Identity in Azure, and Azurite locally.
4. Ensure DB calls with tenant data enforce tenant-scoped SQL/RLS, including protection against JDBC connection-pool tenant-context leakage.
5. Complete `ExportMessageProcessor` with TV2's database contract; only then does the real Worker profile start. The Azure adapter renews visibility, deletes with current receipts after durable outcomes, retries transient failures, and moves invalid/exhausted messages to a sanitized poison envelope.
6. Connect Micrometer counters to App Insights, create dashboard/metric alert with TV1's Terraform, and run failure drill; archive actual screenshot/HTTP/log evidence.
7. Coordinate with TV4 for security test cases and with TV2 for authenticated API load testing; `k6/api-load.js` is parameterized for endpoints not yet finalized.

## Repository layout

```text
pom.xml                                  # Spring Boot 3 / Java 21 worker build
src/main/java/.../worker/                # Spring application, config, poll lifecycle port
src/main/java/.../ExportWorker.java     # Existing core tenant verification & retry
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
