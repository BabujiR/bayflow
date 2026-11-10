import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const AUTH = process.env.BAYFLOW_AUTH_URL ?? 'http://localhost:8083';
const APPOINTMENT = process.env.BAYFLOW_APPOINTMENT_URL ?? 'http://localhost:8081';
const WORKSHOP = process.env.BAYFLOW_WORKSHOP_URL ?? 'http://localhost:8082';

// The dev server mirrors what nginx does in Docker: one origin for the UI and all three
// services, so the refresh-token cookie works and no CORS is needed.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1/auth': AUTH,
      '/.well-known': AUTH,
      '/api/v1/customers': APPOINTMENT,
      '/api/v1/vehicles': APPOINTMENT,
      '/api/v1/appointments': APPOINTMENT,
      '/api/v1/job-cards': WORKSHOP,
      '/api/v1/service-history': WORKSHOP,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
