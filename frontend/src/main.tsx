import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { ApiError } from '@/api/errors';
import { AuthProvider } from '@/auth/AuthProvider';
import { AppToaster } from '@/components/layout/AppToaster';
import { ThemeProvider } from '@/lib/theme';
import { routes } from '@/routes';

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

const router = createBrowserRouter(routes);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
          <AppToaster />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
