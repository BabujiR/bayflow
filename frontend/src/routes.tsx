import type { RouteObject } from 'react-router';

import { GuestOnly, RequireAuth, RequireRole } from '@/auth/guards';
import { AppShell } from '@/components/layout/AppShell';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AppointmentsPage } from '@/features/appointments/AppointmentsPage';
import { BookAppointmentPage } from '@/features/appointments/BookAppointmentPage';
import { BoardPage } from '@/features/board/BoardPage';
import { CustomerDetailPage } from '@/features/customers/CustomerDetailPage';
import { CustomersPage } from '@/features/customers/CustomersPage';
import { JobCardDetailPage } from '@/features/jobcards/JobCardDetailPage';
import { JobCardsPage } from '@/features/jobcards/JobCardsPage';
import { MyJobsPage } from '@/features/jobcards/MyJobsPage';
import { UsersPage } from '@/features/users/UsersPage';
import { VehicleDetailPage } from '@/features/vehicles/VehicleDetailPage';
import { VehiclesPage } from '@/features/vehicles/VehiclesPage';
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
            children: [{ path: '/board', element: <BoardPage /> }],
          },
          {
            element: <RequireRole roles={['MECHANIC']} />,
            children: [{ path: '/my-jobs', element: <MyJobsPage /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN']} />,
            children: [
              { path: '/appointments', element: <AppointmentsPage /> },
              { path: '/vehicles/:id', element: <VehicleDetailPage /> },
            ],
          },
          {
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR']} />,
            children: [{ path: '/appointments/new', element: <BookAppointmentPage /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER']} />,
            children: [{ path: '/vehicles', element: <VehiclesPage /> }],
          },
          {
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN']} />,
            children: [
              { path: '/job-cards', element: <JobCardsPage /> },
              { path: '/job-cards/:id', element: <JobCardDetailPage /> },
            ],
          },
          {
            element: <RequireRole roles={['SERVICE_ADVISOR', 'ADMIN']} />,
            children: [
              { path: '/customers', element: <CustomersPage /> },
              { path: '/customers/:id', element: <CustomerDetailPage /> },
            ],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [{ path: '/users', element: <UsersPage /> }],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
