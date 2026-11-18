import { defineConfig, devices, type Project } from '@playwright/test';

/**
 * Every workshop has its own address (SaaS phase 4): http://<slug>.localhost:3000. The suite
 * runs against each workshop in E2E_WORKSHOPS (default: the two dev workshops), so both prove
 * they work on their own; the phone run uses the last one. E2E_SITE_URL is the public site
 * (sign-up), from which the workshop addresses are derived.
 */
const SITE = new URL(process.env.E2E_SITE_URL ?? process.env.E2E_BASE_URL ?? 'http://localhost:3000');
const WORKSHOPS = (process.env.E2E_WORKSHOPS ?? 'demo,acme')
  .split(',')
  .map((w) => w.trim())
  .filter(Boolean);
const workshopUrl = (slug: string) => `${SITE.protocol}//${slug}.${SITE.host}`;

const desktop: Project[] = WORKSHOPS.map((slug) => ({
  name: `desktop-${slug}`,
  use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 }, baseURL: workshopUrl(slug) },
}));
const phone: Project = {
  name: `phone-${WORKSHOPS[WORKSHOPS.length - 1]}`,
  use: { ...devices['Pixel 7'], baseURL: workshopUrl(WORKSHOPS[WORKSHOPS.length - 1]) },
  grep: /@mobile/,
};

/**
 * Recorded end-to-end scenarios against the full stack started with
 * `docker compose up -d --build` (web app on http://localhost:3000, demo users seeded).
 *
 *   pnpm e2e                         all scenarios in both workshops, desktop + phone
 *   pnpm e2e --project=desktop-demo  one workshop on desktop
 *   E2E_WORKSHOPS=demo pnpm e2e      one workshop only
 *   pnpm e2e -g "double booking"
 *
 * Every actor in every scenario is recorded. Open e2e-videos/index.html afterwards to watch
 * them side by side; playwright-report/ has the same videos plus traces.
 *
 * E2E_SLOWMO (ms per action, default 250) and E2E_STEP_PAUSE (ms per caption, default 900)
 * set the pace of the videos; set both to 0 for a fast run.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 240_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['./e2e/support/video-reporter.ts', { outputDir: 'e2e-videos' }],
  ],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Scenarios record their own per-actor videos (see e2e/support/bayflow.ts).
    video: 'off',
    actionTimeout: 20_000,
    launchOptions: { slowMo: Number(process.env.E2E_SLOWMO ?? 250) },
  },
  projects: [...desktop, phone],
});
