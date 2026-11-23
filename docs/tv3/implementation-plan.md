# TV3 Implementation Plan

## Repository audit

- Working branch: `feat/worker-spring-boot`; clean at the audit baseline. The branch points to the same commit as `main` (`4a97592`).
- The repository contains Terraform and `prototypes/tv3-worker`; it has no shared backend, Maven build, application schema/migrations, or API source.
- The prototype has a pure Java worker core and a standalone five-case test runner. It is not a Spring Boot application and has no real queue, database, or Blob adapters.
- Terraform declares `export-jobs`, `export-jobs-poison`, a private `exports` container, a PostgreSQL 16 database, a separate `worker` App Service profile, and Application Insights. Terraform is TV1-owned and will not be changed.
- TV2 has not supplied a confirmed export-job/membership/task schema or RLS contract. The prototype's contract document records the questions still needing agreement.
- Local environment has JDK 25, no `mvn` command, and Docker CLI without a reachable Docker Engine. Java 21 runtime, PostgreSQL, and Azurite checks cannot currently be claimed.

## Phase checklist

| Phase | Scope | Initial status |
|---|---|---|
| 0 | Audit repository, prototype, contracts, infra, tools, and record this plan | DONE (audit facts verified; current-turn implementation status is in `implementation-progress.md`) |
| 1 | Spring Boot 3 / Java 21 worker application and lifecycle | PARTIAL; framework/lifecycle is implemented, but no real queue adapter is wired |
| 2 | Azure Queue receive/delete, retry, and poison routing | PARTIAL; SDK adapter/poller/tests implemented, but TV2 processor contract and Azurite E2E are unverified |
| 3 | PostgreSQL job/membership/task adapters, atomic claim, tenant isolation | BLOCKED on TV2 schema/RLS contract |
| 4 | Streaming CSV and private Blob integration | BLOCKED on Phase 2/3 contract and local Azurite |
| 5 | Idempotency, retry limits, poison queue, crash recovery | BLOCKED on agreed job lease/status semantics |
| 6 | Structured logs, metrics, and Application Insights export | PENDING; verify actual telemetry names before claiming |
| 7 | Dashboard and alert handoff | BLOCKED on TV1 Azure deployment/authorization |
| 8 | Forged-tenant drill and actual alert evidence | BLOCKED on end-to-end adapters and Azure alert deployment |
| 9 | Parameterized k6 execution and measured report | BLOCKED on TV2 API/auth endpoint and authorized test target |
| 10 | Unit/integration/security tests and quality checks | IN PROGRESS across implemented phases |
| 11 | Delivery docs, evidence, and PR handoff | IN PROGRESS |

## Implementation constraints

- Keep the working application in `prototypes/tv3-worker` until a shared backend exists; do not create an alternate API or alter TV2's schema.
- Keep the original standalone test runner intact as a regression check.
- Implement only contract-independent Spring Boot/runtime pieces before queue and database adapters.
- Mark unverified cloud, Java 21 runtime, Azurite, and performance work BLOCKED/NOT VERIFIED until actually run.
