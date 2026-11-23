-- RLS is enabled on all tenant-owned data tables.
ALTER TABLE tenant_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE export_jobs ENABLE ROW LEVEL SECURITY;

-- The application sets app.tenant_id transaction-locally before tenant-scoped queries.
-- Missing context returns NULL and therefore matches no tenant_id.
CREATE POLICY tenant_memberships_isolation ON tenant_memberships
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY workspaces_isolation ON workspaces
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY audit_events_isolation ON audit_events
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY export_jobs_isolation ON export_jobs
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- For production, create a non-owner runtime DB role and consider FORCE ROW LEVEL SECURITY.
-- Do not run the app as a superuser or a role with BYPASSRLS.
