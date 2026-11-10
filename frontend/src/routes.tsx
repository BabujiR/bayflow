import type { RouteObject } from 'react-router';

import { GuestOnly, RequireAuth, RequireRole } from '@/auth/guards';
import { AppShell } from '@/components/layout/AppShell';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { ComingSoonPage } from '@/pages/ComingSoonPage';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { RegisterPage } from '@/pages/RegisterPage';

/** All routes. Role guards mirror the API's rules; the API remains the real enforcement. */
export const routes: RouteObject[] = [
  {
    element: <GuestOnly />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          {
            element: <RequireRole roles={['SERVICE_ADVISOR']} />,
            children: [{ path: '/board', element: <ComingSoonPage title="Today's board" /> }],
          },
          {
            element: <RequireRole roles={['MECHANIC']} />,
            children: [{ path: '/my-jobs', element: <ComingSoonPage title="My jobs" /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN']} />,
            children: [{ path: '/appointments', element: <ComingSoonPage title="Appointments" /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER']} />,
            children: [{ path: '/vehicles', element: <ComingSoonPage title="My vehicles" /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN']} />,
            children: [{ path: '/job-cards', element: <ComingSoonPage title="Job cards" /> }],
          },
          {
            element: <RequireRole roles={['SERVICE_ADVISOR', 'ADMIN']} />,
            children: [{ path: '/customers', element: <ComingSoonPage title="Customers" /> }],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [{ path: '/users', element: <ComingSoonPage title="User accounts" /> }],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
