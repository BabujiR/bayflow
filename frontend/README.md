# bayflow-web

The Bayflow web app: React 19, TypeScript, Vite, TanStack Query, React Router,
React Hook Form + Zod, Tailwind CSS with shadcn/ui-style components.

## Run it

```bash
# from the repository root: start the backend
docker compose up -d

cd frontend
pnpm install
pnpm dev            # http://localhost:5173
```

The dev server proxies `/api/v1/*` to the three services (see `vite.config.ts`),
so the browser talks to one origin, just like behind nginx in Docker.

## Scripts

| Command | What it does |
|---|---|
| `pnpm dev` | Dev server with hot reload |
| `pnpm build` | Type-check and build to `dist/` |
| `pnpm test` | Vitest + React Testing Library + MSW |
| `pnpm lint` / `pnpm typecheck` | ESLint / TypeScript |
| `pnpm api:update` | Download the services' OpenAPI documents and regenerate `src/api/schema` (services must be running) |

## How sign-in works here

- The access token is kept **in memory only** (`src/api/http.ts`), never in
  `localStorage`.
- On page load the app calls `POST /api/v1/auth/refresh`; the HttpOnly
  `bayflow_rt` cookie restores the session if there is one.
- A 401 triggers one refresh and a retry. Concurrent 401s share a single refresh,
  because reusing a refresh token ends the session.
- The token is refreshed a minute before it expires.

## Layout

| Path | Contents |
|---|---|
| `src/api` | HTTP client, errors, API functions, generated types (`schema/`) |
| `src/auth` | AuthProvider, `useAuth`, route guards |
| `src/components/ui` | Buttons, inputs, cards and other building blocks |
| `src/components/layout` | App shell, logo, theme toggle, user menu |
| `src/pages` | One component per screen |
| `src/test` | Test setup, MSW server, render helper |
