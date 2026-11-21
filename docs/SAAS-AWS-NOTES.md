# Bayflow SaaS: what changes on AWS

Locally, Bayflow runs as a multi-workshop SaaS (phases 1–4, see `docs/PLAN.md` items 17–20).
This note lists what has to change when the same build runs on AWS. Nothing here is built yet.

## Addresses, DNS and certificates

| Locally | On AWS |
|---|---|
| Public site `http://localhost:3000` | `https://bayflow.example` (your domain) |
| Workshops `http://<slug>.localhost:3000` | `https://<slug>.bayflow.example` |
| Browsers resolve `*.localhost` themselves | Route 53: an `A`/`ALIAS` record for the apex **and** a wildcard `*.bayflow.example`, both to the load balancer (or CloudFront) |
| Plain HTTP | ACM certificate covering `bayflow.example` and `*.bayflow.example` on the ALB / CloudFront |

- Set `BAYFLOW_BASE_DOMAIN=bayflow.example` on **all three services** and `VITE_BASE_DOMAIN=bayflow.example` when building the web app.
- Leave `BAYFLOW_FALLBACK_WORKSHOP` **empty**: requests without a workshop address then get 404 instead of signing in to some default workshop.
- The load balancer must keep the browser's `Host` (ALB does) and nginx passes it on in `X-Forwarded-Host`. Make sure nothing in front rewrites the host to the internal service name.
- Only the web app (nginx) is public. The services' own ports must not be reachable from the internet: the address check relies on `X-Forwarded-Host` set by nginx.
- Set `BAYFLOW_COOKIE_SECURE=true`. Keep refresh cookies **host-only** (no `Domain` attribute), as now, so a session never spans workshops.
- Reserved slugs (`www`, `api`, `admin`, `status`, `mail`, …) cover the names you are likely to use for the platform itself. Add any others you put on the domain (e.g. `grafana`) to `SignupService.RESERVED` before going live.

## Database

- Two PostgreSQL users, both in **Secrets Manager**:
  - the **owner** (owns the tables; used only by Flyway via `BAYFLOW_DB_OWNER_USER` / `BAYFLOW_DB_OWNER_PASSWORD`);
  - **`bayflow_app`** (the services' runtime login: no superuser, no `BYPASSRLS`, owns nothing). Create it once with the statements in `infra/postgres/init/02-create-app-role.sh`; each service's `V4` migration grants it what it needs.
- On RDS the master user is not a true superuser. That's why RLS is enabled but not forced. The owner runs migrations and the `SECURITY DEFINER` outbox function; never give the services the owner's credentials. `RowLevelSecurityGuard` refuses to start a service that is.
- Keep one shared database and schema (`bayflow`) with row-level security; per-workshop databases are not needed.

## Rate limits and other in-memory state

- Login and sign-up limits are kept **in memory per auth-service instance**. With more than one instance behind the load balancer, move them to a shared store (ElastiCache/Redis) or apply limits at the edge (AWS WAF rate-based rules on `POST /api/v1/tenants/signup` and `/api/v1/auth/login`).
- The workshop-settings caches (5 minutes) in appointment-service and workshop-service are per instance. That's fine: settings changes show up everywhere within 5 minutes.

## Sign-up hardening before opening to the public

- Email verification of the first admin, and a CAPTCHA (or WAF bot control) on sign-up.
- Terms acceptance and billing, if the product will charge.
- A platform-admin tool to suspend, reactivate and delete workshops. Locally this is SQL: `update bayflow.auth_tenants set status = 'SUSPENDED' where slug = '…'`.
- Monitoring: sign-ups per hour, `workshop-mismatch` 401s, `workshop-not-found` 404s.

## Configuration summary

| Variable | Service | AWS value |
|---|---|---|
| `BAYFLOW_BASE_DOMAIN` | auth, appointment, workshop | `bayflow.example` |
| `VITE_BASE_DOMAIN` | web app build | `bayflow.example` |
| `BAYFLOW_FALLBACK_WORKSHOP` | auth | empty |
| `BAYFLOW_SIGNUP_MAX_PER_HOUR` | auth | e.g. `5` (plus WAF) |
| `BAYFLOW_DB_USER` / `BAYFLOW_DB_PASSWORD` | all | `bayflow_app`, from Secrets Manager |
| `BAYFLOW_DB_OWNER_USER` / `BAYFLOW_DB_OWNER_PASSWORD` | all (Flyway) | owner, from Secrets Manager |
| `BAYFLOW_SYSTEM_CLIENT_SECRET`, `BAYFLOW_WORKSHOP_CLIENT_SECRET` | auth + callers | random, from Secrets Manager |
| `BAYFLOW_COOKIE_SECURE` | auth | `true` |
| `SPRING_PROFILES_ACTIVE` | all | not `dev` (no demo workshops or users) |

The existing deployment guide ("Bayflow on AWS: deployment guide") covers the rest: ECS/Fargate, RDS, ALB, CI/CD. Add the wildcard DNS, the certificate and the variables above to it.

## Locations

Organizations with several locations need nothing new on AWS, with one exception:

- auth-service now calls appointment-service (before a location is archived) at
  `BAYFLOW_APPOINTMENT_URL`, as well as the other way round. Allow that in the security
  groups / service discovery, and keep `/api/v1/internal/` unreachable from the internet
  (nginx returns 404 for it; the ALB should only route to nginx).
- The 5-minute location caches in appointment-service and workshop-service are per instance:
  a changed time zone or labour rate reaches every instance within 5 minutes; new locations
  work at once.
- Location codes and per-location time zones need no DNS changes: locations live inside their
  organization's address (`<slug>.bayflow.example`).

## Branding images

Locally, logos and cover photos live in PostgreSQL (`auth_tenant_assets`, at most a few MB
per workshop) and auth-service serves them. On AWS:

- **Keep it as is for a demo or a few hundred workshops.** The images are small, re-encoded
  and served with an ETag and a one-year `immutable` cache (the URL changes when the image
  does), so browsers rarely ask twice.
- **At scale, move them to S3 + CloudFront:** store the re-encoded bytes under
  `branding/<tenant id>/<kind>/<sha256>.<ext>` in a private bucket (Block Public Access on),
  serve them through CloudFront with Origin Access Control on the same domain (a
  `/branding-assets/*` behaviour), and return those URLs from `/tenants/current`. Keep the
  upload going through auth-service so every image is still checked and re-encoded; never
  hand out pre-signed upload URLs that skip that step. Then `auth_tenant_assets` keeps only
  the key and hash.
- **Upload size at the edge:** nginx allows 4 MB only under `/api/v1/auth/tenant/branding/`.
  If an ALB or CloudFront sits in front, keep its body limit at least that high for this
  path (ALB has no small limit; WAF body inspection only looks at the first 8–64 KB, so
  don't add a WAF rule that blocks large bodies there).
- If images move to another domain (a CDN host), add it to `img-src` in
  `infra/nginx/snippets/security-headers.conf`.

## Notifications

- **notification-service** is a fourth ECS service (512 MB is plenty), private like the
  others: the ALB routes only `/api/v1/notifications*` to it through nginx, never
  `/api/v1/internal/`. Give it the `BAYFLOW_NOTIFICATION_CLIENT_SECRET` from Secrets
  Manager (auth-service needs the same value) and `BAYFLOW_NOTIFICATION_URL` to
  appointment-service and workshop-service (service discovery / Cloud Map).
- Keep the outboxes. They make delivery reliable without SQS; move to SQS/SNS (or
  EventBridge) only when several consumers need the same events or volumes are high.
- The 30-second polling is cheap (an indexed count per user). If it ever matters,
  server-sent events can replace it without API changes.
- Email (phase N2) will use **SES** from a verified domain with DKIM; locally Mailpit.

