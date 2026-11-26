import type { RouteObject } from 'react-router';

import { CHANGE_PASSWORD_PATH, GuestOnly, RequireAuth, RequireRole } from '@/auth/guards';
import { AppShell } from '@/components/layout/AppShell';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AppointmentsPage } from '@/features/appointments/AppointmentsPage';
import { BookAppointmentPage } from '@/features/appointments/BookAppointmentPage';
import { BoardPage } from '@/features/board/BoardPage';
import { GuestBookingPage } from '@/features/booking/GuestBookingPage';
import { CustomerDetailPage } from '@/features/customers/CustomerDetailPage';
import { CustomersPage } from '@/features/customers/CustomersPage';
import { CannedJobsPage } from '@/features/cannedjobs/CannedJobsPage';
import { DueSoonPage } from '@/features/reminders/DueSoonPage';
import { DashboardPage } from '@/features/reports/DashboardPage';
import { ReportsPage } from '@/features/reports/ReportsPage';
import { InvoicePage } from '@/features/invoices/InvoicePage';
import { InvoicePrintPage } from '@/features/invoices/InvoicePrintPage';
import { InvoicesPage } from '@/features/invoices/InvoicesPage';
import { JobCardDetailPage } from '@/features/jobcards/JobCardDetailPage';
import { LocationsPage } from '@/features/locations/LocationsPage';
import { JobCardsPage } from '@/features/jobcards/JobCardsPage';
import { MyJobsPage } from '@/features/jobcards/MyJobsPage';
import { NotificationsPage } from '@/features/notifications/NotificationsPage';
import { LandingPage } from '@/features/site/LandingPage';
import { SignupPage } from '@/features/site/SignupPage';
import { SiteLayout } from '@/features/site/SiteLayout';
import { UsersPage } from '@/features/users/UsersPage';
import { VehicleDetailPage } from '@/features/vehicles/VehicleDetailPage';
import { VehiclesPage } from '@/features/vehicles/VehiclesPage';
import { WorkshopSettingsPage } from '@/features/workshop/WorkshopSettingsPage';
import { ChangePasswordPage } from '@/pages/ChangePasswordPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ResetPasswordPage } from '@/pages/ResetPasswordPage';

/**
 * A workshop's app (`<slug>.localhost`). Role guards mirror the API's rules; the API remains the
 * real enforcement.
 */
export const routes: RouteObject[] = [
  {
    element: <GuestOnly />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
          { path: '/forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
    ],
  },
  // The public booking page (Google Maps / the shop's site link): no account needed.
  { path: '/book', element: <GuestBookingPage /> },
  {
    // Signed in or not: the e-mailed link signs in whoever proves they read that mailbox.
    element: <AuthLayout />,
    children: [{ path: '/reset-password', element: <ResetPasswordPage /> }],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        // Outside the app shell: after a password reset, nothing else is reachable until it's done.
        element: <AuthLayout />,
        children: [{ path: CHANGE_PASSWORD_PATH, element: <ChangePasswordPage /> }],
      },
      {
        // Outside the app shell: the invoice alone, for Print / Save as PDF.
        element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN']} />,
        children: [{ path: '/invoices/:id/print', element: <InvoicePrintPage /> }],
      },
      {
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          // Every signed-in user has their own notifications.
          { path: '/notifications', element: <NotificationsPage /> },
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
            element: <RequireRole roles={['SERVICE_ADVISOR', 'MANAGER', 'ADMIN']} />,
            children: [{ path: '/due-soon', element: <DueSoonPage /> }],
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
            element: <RequireRole roles={['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN']} />,
            children: [
              { path: '/invoices', element: <InvoicesPage /> },
              { path: '/invoices/:id', element: <InvoicePage /> },
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
            // Owners and managers (managers see their own locations' figures).
            element: <RequireRole roles={['ADMIN', 'MANAGER']} />,
            children: [
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/reports', element: <ReportsPage /> },
              { path: '/canned-jobs', element: <CannedJobsPage /> },
            ],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: '/users', element: <UsersPage /> },
              { path: '/settings', element: <WorkshopSettingsPage /> },
              { path: '/locations', element: <LocationsPage /> },
            ],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

/** The public site at the base domain (`localhost:3000`): landing page and workshop sign-up. */
export const publicRoutes: RouteObject[] = [
  {
    element: <SiteLayout />,
    children: [
      { index: true, element: <LandingPage /> },
      // The setup link from a console invite (/setup?invite=…).
      { path: '/setup', element: <SignupPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
