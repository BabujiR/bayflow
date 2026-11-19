# Role
You are a senior full-stack engineer (Java 21 / Spring Boot 4, React 19 / TypeScript).
Turn Bayflow, the existing app in this repository, into a multi-tenant SaaS product that
runs fully on the local Docker stack (`docker compose up -d --build`). AWS comes later; do
not add any cloud resources now.

# Context
- Read README.md and docs/PLAN.md first. Bayflow has three Spring Boot services
  (auth 8083, appointment 8081, workshop 8082), one React app behind nginx (port 3000),
  one PostgreSQL 16 database `bayflow` with one schema `bayflow`, per-service table prefixes
  (auth_, apt_, ws_) and per-service Flyway history tables.
- Today one deployment serves one workshop. Goal: one deployment serves many workshops
  ("tenants"); no workshop can ever see another's data.

# Fixed decisions (do not change without asking me)
1. Isolation model: shared tables + `tenant_id` column on every per-tenant table +
   PostgreSQL row-level security (RLS), enforced in BOTH the application and the database.
2. Tenant id is a UUID; each tenant also has a unique, lowercase `slug` for its address.
3. Tenants live in auth-service (`auth_tenants`). Other services never read auth tables;
   they get the tenant from the JWT.
4. The JWT gets a new claim `tid` (tenant UUID). `common-security` exposes it as
   `CurrentUser.tenantId()`. A user token without `tid` is rejected (401). SYSTEM tokens
   stay tenant-less.
5. Service-to-service: appointment-service sends `tenantId` in the job-card request;
   workshop-service accepts that field only from a SYSTEM token. Outbox rows carry tenant_id.
6. Per-tenant tables: all apt_* and ws_* tables, auth_users, auth_user_roles,
   auth_refresh_tokens (child tables get their own tenant_id, no join-based policies).
   Global tables: auth_tenants, auth_clients, Flyway history tables.
7. Keep: Java 21, Spring Boot 4, PostgreSQL 16, React 19, all current library versions,
   REST only, existing Dependabot rules, existing security (RS256, HttpOnly SameSite=Strict
   refresh cookie, X-Bayflow-Client CSRF header check, CSP). No dependency upgrades
   unless a phase strictly needs one; say so first.
8. The ADMIN role becomes "Workshop admin", scoped to its own tenant. No platform-wide
   admin in this work.

# Local SaaS setup (must work by the end)
- Base domain from config `BAYFLOW_BASE_DOMAIN` (default `localhost`; later `bayflow.app`).
- `http://localhost:3000` = public landing + workshop sign-up (from Phase 4).
- `http://<slug>.localhost:3000` = one workshop's app. Browsers resolve *.localhost to
  127.0.0.1, so no hosts-file edits. nginx must accept any host and pass
  `X-Forwarded-Host`; Vite dev server must allow `.localhost` hosts.
- The `dev` profile seeds TWO workshops for verification:
  `demo` (demo.localhost:3000) and `acme` (acme.localhost:3000), each with
  customer1 / advisor1 / mechanic1 / admin1, password `Bayflow!2026`, so the same
  usernames exist in both, plus a few vehicles and bookings in each.
- Two database roles, created by `infra/postgres/init` on a fresh volume:
  `bayflow_owner` (owns tables, used ONLY by Flyway via spring.flyway.user/password) and
  `bayflow_app` (used by the running services; NOT owner, NO BYPASSRLS).
  Because init scripts run only on a new volume, also provide
  `scripts/upgrade-local-db.(sh|ps1)` that creates the roles on an existing local
  database, and document when `docker compose down -v` is needed instead.

# Phases (one at a time)
For EACH phase:
1. Show a short plan (files to change, migrations, risks) and WAIT for my approval.
2. Implement it. Keep the build green: `mvn verify` (backend), `pnpm test`, `pnpm lint`,
   `pnpm typecheck` (frontend), and the Playwright suite against the local stack.
3. Update README.md and docs/PLAN.md.
4. Give me a "Verify locally" checklist: exact commands, URLs, logins and what I should see.
5. Stop. I review and commit before the next phase.

## Phase 1: Tenant foundation (users notice nothing)
- auth V3 migration: `auth_tenants` (id, slug unique, name, status ACTIVE|SUSPENDED,
  created_at); insert tenant `default` with a FIXED well-known UUID; add tenant_id to
  auth_users, auth_user_roles, auth_refresh_tokens; username and email unique per tenant.
- appointment and workshop V3 migrations: add tenant_id to every table, backfill with the
  same fixed default UUID (Flyway placeholder `${defaultTenantId}`, same value in all three
  services), then NOT NULL, then indexes starting with tenant_id.
  `uq_apt_vehicles_vin` becomes (tenant_id, vin).
- Use Hibernate's `@TenantId` on entities with a `CurrentTenantIdentifierResolver` that reads
  `CurrentUser.tenantId()`, so JPA filters and fills tenant_id automatically. Filter any
  native SQL by hand.
- auth: login/register/refresh resolve the tenant (only `default` in this phase), look users
  up by (tenant, username), put `tid` in tokens; user admin endpoints scoped to the tenant.
- Tests: migration from a pre-phase database (all rows land in default); same username and
  same VIN allowed in two tenants, duplicates within one tenant refused; token without tid
  refused. All existing suites unchanged and green.
- Exit gate: every business row has non-null tenant_id, every user token has tid.

## Phase 2: Database isolation with RLS
- Services connect as `bayflow_app`; Flyway as `bayflow_owner`. Each service refuses to start
  if its runtime DB user owns the tables or has BYPASSRLS.
- V4 migration per service: for every per-tenant table
  ENABLE + FORCE ROW LEVEL SECURITY and policy
  `USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)`
  with the same `WITH CHECK`. Missing tenant must fail closed (no rows), never error or leak.
- `common-security`: run `SELECT set_config('app.tenant', ?, true)` at the start of every
  transaction (transaction-local, so pooled connections never carry it over).
- Outbox dispatcher: read due rows only through a `SECURITY DEFINER` function
  `apt_due_outbox(limit)` owned by bayflow_owner, then process each row in its own
  transaction with that row's tenant set.
- Tests: DB tests as bayflow_app (no tenant -> empty; tenant A cannot see/insert B);
  API isolation suite: for every endpoint, tenant B's token on tenant A's ids returns 404
  (not 403, not 200); a one-connection-pool test with alternating tenants; Playwright
  scenario across demo.localhost and acme.localhost. Isolation suite runs in CI.
- Exit gate: RLS forced on every per-tenant table, services run as bayflow_app, suites green.

## Phase 3: Workshop settings
- auth: `auth_tenant_settings` (displayName, phone, email, address, labourRate, currency,
  timeZone), RLS like other tenant tables. Env vars BAYFLOW_LABOUR_RATE, BAYFLOW_CURRENCY,
  BAYFLOW_WORKSHOP_ZONE become only the defaults for new tenants.
- `GET /api/v1/tenant` (any signed-in user), `PUT /api/v1/tenant/settings` (workshop ADMIN,
  optimistic locking). Internal SYSTEM-only endpoint for the other services; they cache
  settings 5 minutes per tenant.
- Freeze money: add labour_rate and currency to ws_job_cards (set when the card opens) and
  currency to ws_service_history; estimates and history use the stored values, never the
  current setting.
- Frontend: "Workshop settings" page for ADMIN; header shows the workshop name; money and
  dates use the workshop's currency and time zone.
- Tests: two tenants with different rate/currency/zone get correct estimates, totals,
  history and "today"; changing the rate never changes existing job cards; advisor gets 403.
- Exit gate: no business setting is read from env at request time.

## Phase 4: Self-service sign-up and subdomains (local)
- Tenant resolved from the host: auth maps `<slug>.<BAYFLOW_BASE_DOMAIN>` to a tenant at
  login, register and refresh; unknown slug -> "Workshop not found" page; SUSPENDED ->
  sign-in refused with a clear message.
- After sign-in the tenant comes only from the token; every service also checks token tid
  matches the host's tenant, else 401.
- `POST /api/v1/tenants/signup` (public, rate-limited per IP, uses the CSRF header):
  creates tenant + settings + the owner as ADMIN in one transaction. Slug rules:
  3-30 chars, a-z 0-9 and hyphen, unique, reserved list (www, app, api, admin, status,
  mail, help, ...). Live availability check endpoint.
- Frontend: landing + sign-up on the base domain, then redirect to
  `http://<slug>.localhost:3000/login`; sign-in and registration pages show the workshop
  name. Refresh cookie keeps no Domain attribute (separate per subdomain).
- Tests: sign up two workshops through the UI; same usernames independent; token from
  workshop A on workshop B's host -> 401; taken/reserved slugs refused; sign-up rate limit;
  the full Playwright suite runs against two workshop subdomains (keep per-actor videos).
- Exit gate: a new workshop signs up, configures itself and serves customers locally with
  nobody touching the database.

# Rules
- Never weaken an existing security control or delete a test to make a phase pass.
- Migrations must be safe to run on my existing local data (backfill, then constrain).
- Record every assumption in docs/PLAN.md; ask only for hard-to-reverse decisions.
- After Phase 4, write docs/SAAS-AWS-NOTES.md listing what changes for AWS
  (wildcard DNS and certificate, two DB users in Secrets Manager, BAYFLOW_BASE_DOMAIN),
  but do not build it.
