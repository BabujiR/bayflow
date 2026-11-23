import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const AUTH = process.env.BAYFLOW_AUTH_URL ?? 'http://localhost:8083';
const APPOINTMENT = process.env.BAYFLOW_APPOINTMENT_URL ?? 'http://localhost:8081';
const WORKSHOP = process.env.BAYFLOW_WORKSHOP_URL ?? 'http://localhost:8082';
const NOTIFICATION = process.env.BAYFLOW_NOTIFICATION_URL ?? 'http://localhost:8084';

// The dev server mirrors what nginx does in Docker: one origin for the UI and all three
// services, so the refresh-token cookie works and no CORS is needed. Like nginx it serves every
// workshop address (demo.localhost:5173, acme.localhost:5173, ...) and passes the browser's host
// on in X-Forwarded-Host (xfwd), which is how the services know the workshop.
const to = (target: string) => ({ target, xfwd: true });
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    allowedHosts: ['localhost', '.localhost'],
    proxy: {
      '/api/v1/auth': to(AUTH),
      '/api/v1/tenants': to(AUTH),
      '/.well-known': to(AUTH),
      '/api/v1/customers': to(APPOINTMENT),
      '/api/v1/vehicles': to(APPOINTMENT),
      '/api/v1/appointments': to(APPOINTMENT),
      '/api/v1/job-cards': to(WORKSHOP),
      '/api/v1/service-history': to(WORKSHOP),
      '/api/v1/reports': to(WORKSHOP),
      '/api/v1/invoices': to(WORKSHOP),
      '/api/v1/quickbooks': to(WORKSHOP),
      '/api/v1/canned-jobs': to(WORKSHOP),
      '/api/v1/notifications': to(NOTIFICATION),
    },
  },
  test: {
    // Unit and component tests only; e2e/ is run by Playwright against the real stack.
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    // Tests run as a workshop's app (demo.localhost); the public site has renderSite().
    environmentOptions: { jsdom: { url: 'http://demo.localhost:3000/' } },
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    // Room for a few 5 s waits (see src/test/setup.ts) before a test is failed as hung.
    testTimeout: 15_000,
  },
});
