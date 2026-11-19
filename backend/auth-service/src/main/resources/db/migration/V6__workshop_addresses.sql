-- Multi-tenancy, phase 4: every workshop has its own address, <slug>.<base domain>.
-- The workshop all data from before multi-tenancy belongs to becomes "demo"
-- (demo.localhost:3000 locally); its id and data are unchanged. Slugs never change after this,
-- because tokens and addresses refer to them.
UPDATE auth_tenants
SET slug = 'demo', updated_at = now()
WHERE id = '${default_tenant_id}'
  AND slug = 'default'
  AND NOT EXISTS (SELECT 1 FROM auth_tenants WHERE slug = 'demo');
