import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';

import { AuthProvider } from '@/auth/AuthProvider';
import { BrandingEffect } from '@/features/branding/BrandingEffect';
import { consoleRoutes } from '@/features/console/routes';
import { ThemeProvider } from '@/lib/theme';
import { publicRoutes, routes } from '@/routes';

/** Renders the whole app (real routes, auth and providers) at the given path. */
export function renderApp(path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrandingEffect />
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return { ...view, router };
}

/** Renders the public site (landing page and sign-up, at the base domain) at the given path. */
export function renderSite(path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(publicRoutes, { initialEntries: [path] });
  const view = render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return { ...view, router };
}

/** The Ramplight console (<base domain>/console), with its own session. */
export function renderConsole(path = '/console') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(consoleRoutes, { initialEntries: [path] });
  const view = render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return { ...view, router };
}
