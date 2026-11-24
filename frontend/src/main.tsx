import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { ApiError } from '@/api/errors';
import { AuthProvider } from '@/auth/AuthProvider';
import { AppToaster } from '@/components/layout/AppToaster';
import { BrandingEffect } from '@/features/branding/BrandingEffect';
import { consoleRoutes } from '@/features/console/routes';
import { currentSite, isConsole } from '@/lib/site';
import { ThemeProvider } from '@/lib/theme';
import { publicRoutes, routes } from '@/routes';

import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Don't retry what the user or their permissions caused (4xx); retry network/server errors twice.
      retry: (failureCount, error) => !(error instanceof ApiError && error.isClientError) && failureCount < 2,
    },
  },
});

// The bare base domain is the public site (sign-up, no sessions); <slug>.<base domain> is a
// workshop's app. Each address keeps its own refresh cookie, so sessions never cross workshops.
const site = currentSite();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        {isConsole() ? (
          // <base domain>/console: Ramplight's team, with its own session (the console organization).
          <AuthProvider>
            <RouterProvider router={createBrowserRouter(consoleRoutes)} />
            <AppToaster />
          </AuthProvider>
        ) : site.kind === 'public' ? (
          <>
            <RouterProvider router={createBrowserRouter(publicRoutes)} />
            <AppToaster />
          </>
        ) : (
          <AuthProvider>
            <BrandingEffect />
            <RouterProvider router={createBrowserRouter(routes)} />
            <AppToaster />
          </AuthProvider>
        )}
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
