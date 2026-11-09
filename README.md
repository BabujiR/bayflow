# Bayflow

**The smarter way to run your auto workshop.**

Bayflow is a vehicle maintenance platform that runs a workshop from booking to
service history: customers book appointments, service advisors check vehicles
in and manage job cards, mechanics log the work, and every completed job is
recorded in the vehicle's service history.

> **Status:** Milestone 1 of 8 — monorepo skeleton. See [docs/PLAN.md](docs/PLAN.md)
> for the full design and the milestone list.

## Architecture (target)

```
Browser ──▶ nginx (bayflow-web) ──┬─▶ auth-service         :8083 ─┐
                                  ├─▶ appointment-service  :8081 ─┼─▶ PostgreSQL
                                  └─▶ workshop-service     :8082 ─┘   db "bayflow", schema "bayflow"
appointment-service ──REST (SYSTEM token)──▶ workshop-service
```

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
- Node 22 LTS + pnpm (from milestone 6)

## Quick start (milestone 1)

```bash
# 1. Optional: copy the environment template and adjust ports or passwords
cp .env.example .env

# 2. Start PostgreSQL
docker compose up -d
docker compose ps          # postgres should be "healthy"

# 3. Check the schema exists
docker compose exec postgres psql -U bayflow -d bayflow -c "\dn"

# 4. Build the backend (no modules yet; validates the parent build)
mvn -f backend/pom.xml verify
```

On Windows PowerShell use `Copy-Item .env.example .env` for step 1.

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Maven multi-module build (`bayflow-parent`) |
| `frontend/` | bayflow-web React app (milestone 6) |
| `infra/postgres/init/` | creates schema `bayflow` on first start |
| `infra/nginx/` | reverse proxy config (milestone 8) |
| `scripts/` | helper scripts (dev JWT keys from milestone 2) |
| `docs/` | build plan and design notes |
| `.github/` | CI/CD workflows and Dependabot |

## Milestones

| # | Scope | Status |
|---|---|---|
| 1 | Monorepo skeleton, compose (Postgres), CI skeleton | ✅ done |
| 2 | common-security + auth-service | ⏳ next |
| 3 | appointment-service | |
| 4 | workshop-service: job card, inspection, approval | |
| 5 | Work logging, completion, history + check-in integration | |
| 6 | Frontend foundation | |
| 7 | Frontend screens per role | |
| 8 | nginx, full compose, Playwright e2e, CD | |

## Assumptions

Documented in [docs/PLAN.md § 9](docs/PLAN.md#9-assumptions). The main ones:
one workshop time zone (default `America/New_York`), 60-minute default slots,
mileage in miles, a single currency (USD), and SERVICE_ADVISOR also manages
staff accounts.
