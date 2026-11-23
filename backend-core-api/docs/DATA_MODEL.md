# Data Model

Six tables: tenants, users, tenant_memberships, workspaces, audit_events, export_jobs.

- `tenants`: tenant root.
- `users`: maps Entra object ID to application user.
- `tenant_memberships`: user-to-tenant relation and role.
- `workspaces`: tenant-owned resource with `UNIQUE(tenant_id, name)`.
- `audit_events`: append-oriented activity record.
- `export_jobs`: asynchronous export tracking.

All tenant-owned tables include `tenant_id`; RLS policies read transaction-local `app.tenant_id`.
