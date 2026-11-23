# TV3 Implementation Progress

Updated: 2026-11-16

## Current phase

**Phase 2 — Azure Queue Storage: PARTIAL**

Phase 0 audit and plan are recorded in [implementation-plan.md](./implementation-plan.md). The expected prior progress file did not exist in this checkout. Phase 1 code and documentation from the prior turn were present as uncommitted changes and have been preserved.

## Verified baseline

- Branch: `feat/worker-spring-boot`; initially clean and at commit `4a97592`, shared with `main`.
- Existing prototype runner executed on 2026-11-16 with the installed JDK 25: **5/5 tests passed**.
- The five cases cover forged claimed tenant, verified-tenant export, duplicate delivery, revoked membership, and retry after a transient blob error/formula neutralization.
- This is not evidence of Java 21 runtime compatibility, Spring Boot integration, PostgreSQL/Azurite behavior, or Azure telemetry.

## Environment limitations

- Installed runtime/compiler: JDK 25 only; no JDK 21 was found in the checked install locations.
- `mvn` is not on PATH. The Maven dependency cache has entries, but no Maven executable was found.
- Docker CLI is installed, but Docker Engine is unreachable (`dockerDesktopLinuxEngine` pipe missing).
- No live Azure access or team confirmation of the export schema/contracts was exercised.

## Current work and outcomes

| Item | Status | Evidence / limitation |
|---|---|---|
| Repository audit and implementation plan | DONE | `implementation-plan.md`; read root/prototype docs, worker, CSV, self-test, queue/storage/DB/monitoring Terraform |
| Preserve original prototype tests | DONE | Actual run: 5/5 passed before Spring Boot changes |
| Spring Boot 3 non-web `worker` profile and Java 21 bytecode target | DONE | Maven compiled with `javac [release 21]`; runtime test executed on JDK 25, not JDK 21 |
| Queue poll lifecycle port, backoff/jitter, and graceful shutdown | DONE | JUnit retry/shutdown test passed; test-only poller exercised the Boot lifecycle |
| Runnable worker with a real Queue adapter | PARTIAL | Azure Queue clients/poller are wired for `worker,azurite` or `worker,azure`; runtime remains fail-closed because no production `ExportMessageProcessor` is available |
| Azure Storage Queue SDK adapter and `ExportQueuePoller` | PARTIAL | SDK 12.32.2 code compiled; fake-transport unit tests passed; Azurite/Azure endpoint not contacted |
| Queue parser, ACK/retry/poison routing, visibility renewal | PASS in unit tests | Durable outcomes delete with current receipt; retry updates visibility; poison send precedes source delete |
| Production `ExportMessageProcessor` / tenant verification | BLOCKED | No TV2 schema/RLS/lease contract; worker does not export or claim tenant safety |
| Azurite integration | NOT VERIFIED | Docker Engine pipe remains unavailable |
| JDBC and Blob adapters | PENDING/BLOCKED | TV2 schema/RLS contract not provided; Blob work is later phase |
| Azure Monitor, alert, cloud failure drill | NOT VERIFIED | No deployed Azure environment exercised |
| k6 performance report | BLOCKED | API endpoint/auth contract and authorized target unavailable; no benchmark fabricated |

## Changes made in this continuation

- Added a standalone Maven/Spring Boot 3.4.5 worker module under `prototypes/tv3-worker`; the existing Java core and script remain unchanged.
- Added Java 21 compiler release configuration, a non-web default `worker` profile, typed polling/backoff/shutdown properties, and a `SmartLifecycle` polling port. The worker profile requires an `ExportQueuePoller` bean instead of exiting successfully without doing work.
- Added Spring context and lifecycle tests. Updated the TV3 README and recorded audit/phase blockers in this directory.
- No Terraform, TV2 schema, API/authentication, frontend, CI/CD, or group workflows were changed.
- Added Azure Storage Queue SDK 12.32.2 and Azure Identity 1.19.0 adapters; local `azurite` profile reads `AZURE_STORAGE_CONNECTION_STRING`, Azure profile uses `ManagedIdentityCredential` and `AZURE_STORAGE_QUEUE_ENDPOINT`.
- Added version-1 JSON parser (schemaVersion/jobId required; claimedTenantId optional/untrusted), visibility renewal, correct latest-popReceipt ACK, transient backoff, and sanitized poison forwarding.
- Added `ExportMessageProcessor` as an explicit TV2 integration port only. No production implementation is present; the worker cannot perform an export and intentionally fails startup without it.
- Proposed Queue payload/ack rules are recorded for TV2 in `prototypes/tv3-worker/docs/CONTRACT_TO_CONFIRM.md`; they are not yet a signed team contract.

## Commands executed and actual results

- `.\prototypes\tv3-worker\scripts\run-tests.ps1` — **PASS**, existing prototype **5/5** on JDK 25 (run before and after the Spring changes).
- Temporary Maven 3.9.9: `mvn -B -ntp clean verify` in `prototypes/tv3-worker` — **PASS**, 16 tests, 0 failures/errors/skips; package/repackage successful. Compiler output reports `release 21`; tests ran using Java 25.0.5.
- Queue unit coverage: 9 poller cases, 4 message parser cases, 2 lifecycle cases, and 1 Spring profile test. Queue poller cases include malformed/missing fields, queue receive failure, durable success, retry without deletion, current-receipt duplicate delivery, visibility renewal, poison routing and poison-send failure.
- `docker version --format '{{.Client.Version}} / {{.Server.Version}}'` — **FAIL / NOT VERIFIED**; Docker CLI exists but Docker Engine pipe `dockerDesktopLinuxEngine` is unavailable. Azurite was not started.
- `git diff --check` — **PASS**.

## Next actions

1. Get TV2 agreement on the proposed versioned queue payload and durable processing outcomes.
2. Implement `ExportMessageProcessor` against TV2's authoritative job/membership/task schema and RLS/lease semantics; until then the Worker intentionally does not start processing.
3. Use a reachable Docker Engine/Azurite to verify connection profiles, queue creation, visibility update, and source/poison ACK behavior end to end.
4. Re-run a runtime check on Java 21 when that JDK is available.

## Cập nhật ngày 23/11/2026

Đã kiểm tra mã TV2 mới trên `origin/main` và xác minh Worker demo trên PostgreSQL/Azurite. Tham chiếu bản mới nhất ở [báo cáo kiểm thử](test-report.md), [báo cáo cuối kỳ](final-report.md) và [hợp đồng TV2](../../prototypes/tv3-worker/docs/CONTRACT_TO_CONFIRM.md). Các mục trên trang này ghi lại trạng thái ngày 16/11.
