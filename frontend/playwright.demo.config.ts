import { defineConfig, devices } from '@playwright/test';

/**
 * Records the product demo videos against the live site (see demo/README.md).
 *
 *   $env:DEMO_PASSWORD = "<a strong password>"   (PowerShell; every demo account uses it)
 *   pnpm demo:record                               all five videos, in order
 *   pnpm demo:record demo/01-walkthrough.spec.ts   just the walkthrough
 *
 * DEMO_SITE_URL (default https://getramplight.com), DEMO_SLUG (default summit-auto) and
 * DEMO_PACE (1 = normal, 1.3 = slower) change where and how fast it records.
 * Videos land in demo-videos/.
 */
export default defineConfig({
  testDir: './demo',
  testMatch: '**/*.spec.ts',
  outputDir: 'demo-results',
  timeout: 45 * 60_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 800 },
    actionTimeout: 30_000,
    navigationTimeout: 45_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // The demo fixture records its own video (demo/support/presenter.ts).
    video: 'off',
  },
});
