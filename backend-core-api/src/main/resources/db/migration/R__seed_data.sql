-- Repeatable, idempotent development seed (3 organizations, 5 users/org, workspaces, and 20 tasks/org).
-- Preserves 11111111-1111-1111-1111-111111111111 and demo-owner for existing local & k6 tests.

INSERT INTO tenants (id, name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Demo Tenant (Org A)'),
  ('11111111-1111-1111-1111-222222222222', 'Beta Logistics (Org B)'),
  ('11111111-1111-1111-1111-333333333333', 'Gamma FinTech (Org C)')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO users (id, entra_object_id, email, display_name) VALUES
  ('22222222-2222-2222-2222-222222222222', 'demo-owner',      'owner@orga.example.local',   'Org A Owner'),
  ('22222222-2222-2222-2222-222222222223', 'orga-member-1',   'member1@orga.example.local', 'Org A Member 1'),
  ('22222222-2222-2222-2222-222222222224', 'orga-member-2',   'member2@orga.example.local', 'Org A Member 2'),
  ('22222222-2222-2222-2222-222222222225', 'orga-user-x',     'userx@orga.example.local',   'Org A User X'),
  ('22222222-2222-2222-2222-222222222226', 'orga-guest',      'guest@orga.example.local',   'Org A Guest'),
  ('33333333-3333-3333-3333-333333333331', 'orgb-owner',      'owner@orgb.example.local',   'Org B Owner'),
  ('33333333-3333-3333-3333-333333333332', 'orgb-member-1',   'member1@orgb.example.local', 'Org B Member 1'),
  ('33333333-3333-3333-3333-333333333333', 'orgb-member-2',   'member2@orgb.example.local', 'Org B Member 2'),
  ('33333333-3333-3333-3333-333333333334', 'orgb-member-3',   'member3@orgb.example.local', 'Org B Member 3'),
  ('33333333-3333-3333-3333-333333333335', 'orgb-guest',      'guest@orgb.example.local',   'Org B Guest'),
  ('44444444-4444-4444-4444-444444444441', 'orgc-owner',      'owner@orgc.example.local',   'Org C Owner'),
  ('44444444-4444-4444-4444-444444444442', 'orgc-member-1',   'member1@orgc.example.local', 'Org C Member 1'),
  ('44444444-4444-4444-4444-444444444443', 'orgc-member-2',   'member2@orgc.example.local', 'Org C Member 2'),
  ('44444444-4444-4444-4444-444444444444', 'orgc-member-3',   'member3@orgc.example.local', 'Org C Member 3'),
  ('44444444-4444-4444-4444-444444444445', 'orgc-guest',      'guest@orgc.example.local',   'Org C Guest')
ON CONFLICT (entra_object_id) DO UPDATE
SET email = EXCLUDED.email, display_name = EXCLUDED.display_name;

INSERT INTO tenant_memberships (tenant_id, user_id, role, status) VALUES
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'OWNER',  'ACTIVE'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222223', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222224', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222225', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222226', 'GUEST',  'ACTIVE'),
  ('11111111-1111-1111-1111-222222222222', '33333333-3333-3333-3333-333333333331', 'OWNER',  'ACTIVE'),
  ('11111111-1111-1111-1111-222222222222', '33333333-3333-3333-3333-333333333332', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-222222222222', '33333333-3333-3333-3333-333333333333', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-222222222222', '33333333-3333-3333-3333-333333333334', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-222222222222', '33333333-3333-3333-3333-333333333335', 'GUEST',  'ACTIVE'),
  ('11111111-1111-1111-1111-333333333333', '44444444-4444-4444-4444-444444444441', 'OWNER',  'ACTIVE'),
  ('11111111-1111-1111-1111-333333333333', '44444444-4444-4444-4444-444444444442', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-333333333333', '44444444-4444-4444-4444-444444444443', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-333333333333', '44444444-4444-4444-4444-444444444444', 'MEMBER', 'ACTIVE'),
  ('11111111-1111-1111-1111-333333333333', '44444444-4444-4444-4444-444444444445', 'GUEST',  'ACTIVE')
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role = EXCLUDED.role, status = EXCLUDED.status;
