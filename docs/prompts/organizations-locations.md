# Role
You are a senior full-stack engineer (Java 21 / Spring Boot 4, React 19 / TypeScript).
Extend Bayflow, the existing multi-tenant SaaS app in this repository, so that one account
can run several shops: **organizations with locations**. Everything runs on the local Docker
stack (`docker compose up -d --build`); do not add cloud resources.

# Context
- Read README.md, docs/PLAN.md (items 17–20) and docs/SAAS-AWS-NOTES.md first.
- Today: one tenant = one workshop, at `<slug>.localhost:3000`. Isolation is shared tables +
  `tenant_id` + PostgreSQL row-level security (RLS), with the tenant set per transaction
  (`app.tenant`). Tokens carry `tid` (tenant) and `tsl` (slug). Workshop settings (labour rate,
  currency, time zone) live in auth-service; job cards freeze rate and currency when they open.
- Goal: a tenant becomes an **organization** that has one or more **locations** (shops).
  An independent shop is an organization with exactly one location and must notice nothing.
  A multi-location operator sees every location or only the ones their role allows.

# Fixed decisions (do not change without asking me)
1. **The tenant stays the organization.** Keep `auth_tenants`, `tenant_id`, the `tid` claim and
   every existing RLS policy exactly as they are. No table renames. UI wording: "organization"
   for the account, "location" for a shop. The organization's address stays
   `<slug>.localhost`; locations do not get their own subdomains.
2. **Locations live in auth-service:** `auth_locations` (id uuid, tenant_id, code unique per
   tenant (2–20 chars, a-z 0-9 hyphen), name, phone, email, address, time_zone (nullable),
   labour_rate (nullable), status ACTIVE|ARCHIVED, created_at, updated_at, version), with RLS
   like every per-tenant table. Other services never read auth tables; they get locations
   from the token, the request and a cached internal endpoint (like workshop settings).
3. **Default location id = tenant id.** Every existing tenant gets one location "Main" whose id
   equals its tenant id, so each service can backfill `location_id = tenant_id` on its own,
   with no cross-service reads in migrations. New organizations (sign-up) get their first
   location created the same way.
4. **Settings inheritance:** the organization keeps its defaults (`auth_tenant_settings`). A
   location may override `time_zone` and `labour_rate`; null means "use the organization's".
   **Currency is organization-wide only** (one currency per organization keeps rollups simple).
   Effective settings = location override ?? organization default.
5. **What gets `location_id` (NOT NULL after backfill, index starting with tenant_id):**
   apt_appointments, the outbox payload, ws_job_cards (copied from the appointment),
   ws_service_history (copied from the card). **Customers and vehicles stay
   organization-wide:** a customer can visit any location; their history shows the location of
   each visit.
6. **Scoped roles.** Keep CUSTOMER, SERVICE_ADVISOR, MECHANIC, ADMIN and add MANAGER (a
   location's general manager; a regional manager is a MANAGER with several locations).
   - ADMIN = organization owner: always all locations.
   - MANAGER, SERVICE_ADVISOR and MECHANIC have a location scope: `auth_user_locations`
     (tenant_id, user_id, location_id), at least one location each.
   - CUSTOMER: no scope; organization-wide, and picks a location when booking.
7. **Scope in the token:** claim `lsc` = `["*"]` for ADMIN, otherwise the list of location ids
   (max 200; refuse more). Scope changes apply on the next token refresh (≤ 15 minutes).
   Document this.
8. **Enforcement in both layers, as for tenants:**
   - App: `common-security` exposes `CurrentUser.locationScope()`. Queries on location-bound
     tables are filtered by the scope; writes to a location outside the scope are refused.
   - Database: `TenantAwareJpaTransactionManager` also sets `app.locations`
     (`*` or a comma-separated uuid list, transaction-local). The RLS policies on
     location-bound tables become
     `tenant_id = <tenant> AND (current_setting('app.locations', true) = '*' OR location_id = ANY(string_to_array(current_setting('app.locations', true), ',')::uuid[]))`.
     Missing setting = no rows (fail closed). SYSTEM work (outbox, job-card open) runs with `*`
     inside its tenant.
   - Outside the scope is **404, not 403** (same rule as other workshops' data).
9. **Location switcher:** the web app sends `X-Bayflow-Location: <id>` or `all` on every API
   call. Services narrow the token scope to that location (never widen it; a location outside
   the scope = 403 `location-not-allowed`). No header = the whole token scope.
10. **Times:** each appointment, job card and history entry is shown in its *location's* time
    zone. "Today" on the board and "My jobs" is the selected location's today. "All locations"
    views show the location name and zone next to times.
11. **Money:** a job card freezes its location's effective labour rate and the organization's
    currency when it opens (extends SaaS phase 3; workshop-service reads effective settings for
    (tenant, location)).
12. **Keep:** Java 21, Spring Boot 4, PostgreSQL 16 (`postgres:16-alpine`), React 19, current
    library versions, REST only, Dependabot rules (never bump Postgres or Java images), all
    existing security controls. No new dependencies unless a phase strictly needs one; say so
    first.

# Phases (one at a time)
For EACH phase:
1. Show a short plan (files to change, migrations, risks, open questions) and WAIT for my approval.
2. Implement it. Keep the build green: `mvn -f backend/pom.xml verify`, `pnpm lint`,
   `pnpm typecheck`, `pnpm test`, and `pnpm e2e` against the local stack.
3. Update README.md and docs/PLAN.md (record every assumption).
4. Give me a "Verify locally" checklist: exact commands, URLs, logins and what I should see.
5. Stop. I review and commit before the next phase.

## Phase L1: Locations exist (users notice nothing)
- auth V7: `auth_locations` + RLS; insert "Main" (id = tenant id, code `main`) for every tenant;
  the location columns for overrides; sign-up and dev seeding create the first location.
- appointment V5 / workshop V6: add `location_id`, backfill `= tenant_id`, then NOT NULL and
  indexes `(tenant_id, location_id, …)`. Entities get `locationId`; the outbox payload and
  `OpenJobCardPayload` carry it; workshop-service copies it to the card and the history.
- New appointments get the organization's only location automatically when there is just one;
  API fields `locationId` (optional while an organization has one location, required otherwise).
- auth: `GET /api/v1/auth/locations` (everyone in the organization: id, code, name, address,
  effective time zone and labour rate) and SYSTEM-only
  `GET /api/v1/auth/internal/tenants/{tid}/locations/{lid}/settings`; workshop-service and
  appointment-service read effective settings through it (cached like today).
- Tests: migration from a pre-L1 database (every row lands in "Main"); a location from another
  organization cannot be used; effective settings (override vs inherit); all existing suites
  unchanged and green.
- Exit gate: every location-bound row has a non-null location_id; nothing visible has changed.

## Phase L2: Scoped access (database and API)
- MANAGER role; `auth_user_locations`; `lsc` claim; ADMIN user API to assign locations
  (`PUT /api/v1/auth/users/{id}/locations`) with rules: staff need ≥ 1 location, ADMIN needs
  none, unknown or archived locations refused, an admin cannot remove their own ADMIN role.
- `common-security`: `CurrentUser.locationScope()`, the `X-Bayflow-Location` header (narrow only),
  `app.locations` set per transaction, a token without `lsc` for a staff user is refused (401),
  like a token without `tid`.
- RLS policies on location-bound tables as in decision 8 (V-next migrations per service).
- Board, "My jobs", appointment and job-card lists, job-card detail, check-in, and the job-card
  actions all respect the scope. Customers are unaffected.
- Tests:
  - DB as `bayflow_app`: scope `*` sees all, scope {A} sees only A, missing setting sees none,
    an insert into B with scope {A} fails.
  - API isolation suite: for every location-bound endpoint, a user scoped to A gets 404 on B's
    ids and never sees B in lists; the header narrows but never widens (403).
  - A one-connection-pool test alternating scopes. The suite runs in CI.
- Exit gate: an advisor of location A cannot read or change anything of location B, in the API
  or the database.

## Phase L3: Multi-location in the web app
- *Locations* page (ADMIN): list, create, edit (name, code, contact, address, time zone and
  labour rate overrides with "inherit" shown), archive (refused for the last active location
  or one with future appointments). *User accounts*: assign locations; the MANAGER role.
- Location switcher in the header for users with more than one location ("All locations" for
  scopes with several), remembered per user in the browser, sent as `X-Bayflow-Location`;
  hidden entirely when the organization has one location.
- Booking: a customer picks the location (name, address, its local times); advisors book into
  the selected location. Board and "My jobs" use the selected location's today; "All
  locations" lists show the location and convert times to each location's zone.
- Job card and service history show the location; amounts use the frozen rate and currency.
- Tests: Vitest for the switcher, booking with a location choice, per-location times,
  single-location organizations showing no location UI. Playwright (both workshops, per-actor
  videos): in `acme`, add a second location "North" with its own zone and rate, assign
  advisor1 to Main only and a new advisor to North; each sees only their board; the admin
  switches between locations and "All locations"; a customer books at North and the job card
  uses North's rate; the full existing suite still passes unchanged.
- Exit gate: an organization adds a location, staffs it and serves customers there, with
  nobody touching the database; single-location organizations look exactly as before.

# Rules
- Never weaken an existing security control or delete a test to make a phase pass.
- Migrations must be safe on my existing local data (backfill, then constrain) and must not
  read another service's tables.
- Record every assumption in docs/PLAN.md; ask only for hard-to-reverse decisions.
- Out of scope (note them in PLAN as next steps): per-location subdomains, plan limits on the
  number of locations, rollup reporting, organization-wide templates (pricing matrix, canned
  jobs, inspection templates), marketing campaigns, payments.
- After L3, add a short section to docs/SAAS-AWS-NOTES.md on anything locations change for AWS.
