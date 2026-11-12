# Bayflow

**The smarter way to run your auto workshop.**

Bayflow is a vehicle maintenance platform that runs a workshop from booking to
service history: customers book appointments, service advisors check vehicles
in and manage job cards, mechanics log the work, and every completed job is
recorded in the vehicle's service history.

> **Status:** all 8 milestones complete. See [docs/PLAN.md](docs/PLAN.md) for the
> design, the workflows and the assumptions.

## Architecture

![Bayflow architecture](docs/architecture.svg)

- **nginx** serves the React app and routes each `/api/v1/*` path to the right
  service, so the browser talks to a single origin.
- **auth-service** issues JWTs; the other services verify them with its public
  key (JWKS).
- **appointment-service** creates the job card in **workshop-service** at
  check-in, using a SYSTEM token. The call is idempotent and retried from an
  outbox if workshop-service is down.
- All services share one PostgreSQL schema, but each reads and writes **only
  its own tables** (`auth_*`, `apt_*`, `ws_*`).

| Component | Tech |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL 16, one schema, table prefix per service (`auth_`, `apt_`, `ws_`) |
| Frontend | React 19, TypeScript, Vite, TanStack Query, Tailwind CSS + shadcn/ui |
| Tests | JUnit 5, AssertJ, Testcontainers, WireMock, Vitest, Playwright |
| Delivery | Docker Compose, GitHub Actions, GitHub Container Registry |

## Prerequisites

- JDK 21 (Temurin recommended)
- Maven 3.9+
- Docker Desktop (for Postgres, Testcontainers and the full stack)
- Node 22.12+ (24 LTS recommended) and pnpm 10: `npm install -g pnpm@10`
  (Node 25+ no longer includes Corepack, so `corepack enable` does not work there)

## Quick start

```bash
# Optional: copy the environment template and adjust ports or secrets
cp .env.example .env                 # PowerShell: Copy-Item .env.example .env

# Build and start everything: PostgreSQL, the three services and the web app
docker compose up -d --build
docker compose ps                    # all five should become "healthy"
```

Open **http://localhost:3000** and sign in with a demo user below.

| What | URL |
|---|---|
| **Web app** (nginx, the whole stack on one origin) | http://localhost:3000 |
| Auth API docs (Swagger UI) | http://localhost:8083/swagger-ui.html |
| Appointment API docs (Swagger UI) | http://localhost:8081/swagger-ui.html |
| Workshop API docs (Swagger UI) | http://localhost:8082/swagger-ui.html |
| Public signing key (JWKS) | http://localhost:8083/.well-known/jwks.json |

For frontend development with hot reload, keep the stack running and start the
Vite dev server instead (http://localhost:5173):

```bash
cd frontend
pnpm install
pnpm dev
```

### Demo users (`dev` profile only)

| Username | Password | Role |
|---|---|---|
| `customer1` | `Bayflow!2026` | CUSTOMER |
| `advisor1` | `Bayflow!2026` | SERVICE_ADVISOR |
| `mechanic1` | `Bayflow!2026` | MECHANIC |
| `admin1` | `Bayflow!2026` | ADMIN |

Try it:

```bash
curl -i -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"advisor1","password":"Bayflow!2026"}'
```

### Try the whole workflow in the web app

At http://localhost:3000 (or the dev server on :5173):

1. **customer1** — complete the profile on first sign-in, add a vehicle under
   *My vehicles*, then *Book a service*.
2. **advisor1** — on *Today's board* (pick the booking's day): *Confirm* →
   *Mark arrived* → *Check in* with the odometer reading. The job card opens
   automatically.
3. **mechanic1** — *My jobs* → the card under *Needs inspection* → add findings.
4. **advisor1** — on the job card: add recommended work with estimates, then
   *Send to customer for approval*.
5. **customer1** — *Job cards* → approve or decline each item.
6. **mechanic1** — *Start work*, log work (plus parts and labour) for each
   approved item, then *Complete job*.
7. **customer1** — *My vehicles* → the vehicle → its service history.
8. **admin1** — *User accounts* to create staff; read-only views elsewhere.

### Using the APIs directly

Each service has Swagger UI (links above). Get a token from
`POST /api/v1/auth/login` in the auth Swagger UI, click **Authorize** in the other
service's Swagger UI and paste it.

## How authentication works

- **auth-service** stores users (BCrypt passwords) and signs **access tokens**:
  RS256 JWTs valid for 15 minutes with claims `sub` (user id), `username`,
  `roles`, `iss=bayflow-auth`, `aud=bayflow`.
- The **refresh token** is a random value sent only as the `bayflow_rt` cookie
  (`HttpOnly`, `SameSite=Strict`, path `/api/v1/auth`), valid 7 days. Only its
  hash is stored. Every `/auth/refresh` rotates it; replaying an old one ends
  the whole session.
- **CSRF:** the services are bearer-token APIs (no sessions, no auth cookies), so
  Spring's CSRF filter is off. The two cookie endpoints, `/auth/refresh` and
  `/auth/logout`, instead require the `X-Bayflow-Client` header (which a cross-site
  page cannot send without a CORS preflight, and Bayflow allows none) and refuse
  `Sec-Fetch-Site: cross-site`. The web app sends the header on every call.
- Other services verify tokens with the public key from
  `/.well-known/jwks.json` (shared setup in `backend/common-security`).
- **appointment-service** gets a short-lived `SYSTEM` token from
  `POST /api/v1/auth/token` (client credentials) to call workshop-service.
- **Roles:** CUSTOMER (self-registered), SERVICE_ADVISOR, MECHANIC and ADMIN.
  Only an ADMIN creates or disables staff accounts (`/api/v1/auth/users`).
  The first admin outside dev is created from `BAYFLOW_BOOTSTRAP_ADMIN_*` in
  `.env` when no admin exists yet.
- Login is protected by a rate limit (10 attempts/minute per username + IP)
  and a 15-minute lock after 5 wrong passwords.
- Signing key: without configuration, auth-service generates a throwaway key at
  startup (tokens stop working after a restart). For a stable key run
  `scripts/generate-dev-keys.sh` (or `.ps1`) and set the two
  `BAYFLOW_JWT_*_KEY_PATH` lines in `.env`. In production set
  `BAYFLOW_JWT_ALLOW_EPHEMERAL=false` and mount real keys.

## Tests

| Command | What it covers |
|---|---|
| `mvn -f backend/pom.xml verify` | Unit tests (state machines, rules), Testcontainers integration tests for every service against PostgreSQL 16, WireMock tests of the check-in → job card integration, the shared-schema migration test, JaCoCo ≥ 80% on domain and application code. Needs Docker running. |
| `pnpm test` (in `frontend/`) | Vitest + Testing Library + MSW: sign-in flow, forms, role-based screens. |
| `pnpm e2e` (in `frontend/`) | 23 recorded Playwright scenarios against the running stack (`docker compose up -d --build`): the full journey plus edge cases (lockout, roles, data isolation, double booking, concurrent advisors, odometer rules, partial/declined approval, work rules, workshop outage). Every actor is filmed; open `frontend/e2e-videos/index.html` to watch them side by side. First time: `pnpm e2e:install`. See *End-to-end scenarios* below. |

## End-to-end scenarios

```bash
docker compose up -d --build --wait     # the stack on http://localhost:3000
cd frontend
pnpm e2e:install                        # once: Playwright's Chromium
pnpm e2e                                # all scenarios, desktop + phone (~25–35 min)
pnpm e2e --project=desktop -g "double booking"   # one scenario
```

Afterwards open **`frontend/e2e-videos/index.html`**: pick a scenario and every person in it
(customer, advisor, mechanic, admin) plays side by side, in sync. A caption on each screen
says who they are and what is being tried; amber captions are edge cases, green ones are
checks. The timeline under the videos jumps to any step. `playwright-report/` has the same
videos plus a trace for any failure.

| File | Scenarios |
|---|---|
| `01-happy-path` | Sign-up → booking → check-in → inspection → partial approval → work, parts, labour → service history |
| `02-accounts-and-access` | Sign-up validation and duplicates; lockout after 5 wrong passwords; deep link and sign-out; what each role may open (UI and API); one customer can't see another's job card or car; disabling a user revokes their session |
| `03-booking` | Profile required first; VIN format and uniqueness; double booking (overlap, wrap-around, back-to-back allowed, past time); reschedule into a clash; cancelled slot can be rebooked; advisor books for a walk-in |
| `04-check-in` | Two advisors on the same booking (409 on the stale screen); odometer validation and "never backwards"; no-show only after the start time; no check-in before arrival; no second check-in |
| `05-job-card` | Decline everything → inspection only; no work needed; customer and advisor decide the same item at once; only approved items can be worked on; every approved item needs work before completing; completed cards are locked; who can do what |
| `06-resilience` | Stops `workshop-service`, checks in, starts it again: the job card arrives from the outbox, exactly once. Needs the Docker CLI; skip with `E2E_SKIP_CHAOS=1` |

Pace of the videos: `E2E_SLOWMO` (ms per action, default 250) and `E2E_STEP_PAUSE` (ms per
caption, default 900); set both to `0` for a fast run. The scenarios create their own
customers, so they can run again and again against the same database; staff logins are
spaced out to stay under the 10-per-minute login limit.

## CI/CD

- **CI** (`.github/workflows/ci.yml`, every push and pull request):
  - *backend*: `mvn verify`, uploads test and coverage reports
  - *frontend*: lint, type-check, unit tests, build
  - *compose*: validates `docker-compose.yml`
  - *e2e* (after backend and frontend pass): `docker compose up --build --wait`, runs
    Playwright, uploads the report and the scenario videos, prints service logs on failure
- **CD** (`.github/workflows/cd.yml`, after CI succeeds on `main`): builds and pushes
  `ghcr.io/<owner>/bayflow-auth`, `bayflow-appointment`, `bayflow-workshop` and
  `bayflow-web`, tagged with the commit SHA and `latest`.
- **Dependabot** keeps Maven, npm, Docker and GitHub Actions dependencies current.
  PostgreSQL, Java and Node major versions are upgraded deliberately, not by bot.

## Running outside local development

- Set `SPRING_PROFILES_ACTIVE` to something other than `dev` (no demo users), set
  `BAYFLOW_BOOTSTRAP_ADMIN_PASSWORD` for the first admin, and change
  `BAYFLOW_SYSTEM_CLIENT_SECRET` and the database password.
- Generate a real signing key (`scripts/generate-dev-keys.*` shows how), mount it,
  and set `BAYFLOW_JWT_ALLOW_EPHEMERAL=false`.
- Serve the web app over HTTPS (e.g. a load balancer in front of `bayflow-web`) and set
  `BAYFLOW_COOKIE_SECURE=true` so the refresh cookie is only sent over HTTPS.
- Turn Swagger UI off if the APIs should not be browsable (`SPRINGDOC_SWAGGER_UI_ENABLED=false`).

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Maven multi-module build (`bayflow-parent`) |
| `frontend/` | bayflow-web React app ([frontend/README.md](frontend/README.md)) |
| `infra/postgres/init/` | creates schema `bayflow` on first start |
| `infra/nginx/` | nginx config for the web image: routing, caching, security headers |
| `scripts/` | helper scripts (dev JWT key generation) |
| `docs/` | build plan and design notes |
| `.github/` | CI/CD workflows and Dependabot |

## Milestones

| # | Scope | Status |
|---|---|---|
| 1 | Monorepo skeleton, compose (Postgres), CI skeleton | ✅ done |
| 2 | common-security + auth-service | ✅ done |
| 3 | appointment-service | ✅ done |
| 4 | workshop-service: job card, inspection, approval | ✅ done |
| 5 | Work logging, completion, history + check-in integration | ✅ done |
| 6 | Frontend foundation | ✅ done |
| 7 | Frontend screens per role | ✅ done |
| 8 | nginx, full compose, Playwright e2e, CD | ✅ done |

## Assumptions

Documented in [docs/PLAN.md § 9](docs/PLAN.md#9-assumptions). The main ones:
one workshop time zone (default `America/New_York`), 60-minute default slots,
mileage in miles, a single currency (USD), and a separate ADMIN role that
manages user accounts.
