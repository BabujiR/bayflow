import { defineConfig, devices } from '@playwright/test';

/**
 * Recorded end-to-end scenarios against the full stack started with
 * `docker compose up -d --build` (web app on http://localhost:3000, demo users seeded).
 *
 *   pnpm e2e                  all scenarios, desktop + phone
 *   pnpm e2e --project=desktop
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
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Scenarios record their own per-actor videos (see e2e/support/bayflow.ts).
    video: 'off',
    actionTimeout: 20_000,
    launchOptions: { slowMo: Number(process.env.E2E_SLOWMO ?? 250) },
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'phone', use: { ...devices['Pixel 7'] }, grep: /@mobile/ },
  ],
});
