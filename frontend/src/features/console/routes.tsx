import type { RouteObject } from 'react-router';

import { withErrorPage } from '@/components/common/RouteError';
import { NotFoundPage } from '@/pages/NotFoundPage';

import { ConsoleLayout } from './ConsoleLayout';
import { DemoRequestsPage } from './DemoRequestsPage';
import { InvitationsPage } from './InvitationsPage';
import { WorkshopsPage } from './WorkshopsPage';

/** <base domain>/console: the Ramplight console (Ramplight's own team). */
export const consoleRoutes: RouteObject[] = withErrorPage([
  {
    path: '/console',
    element: <ConsoleLayout />,
    children: [
      { index: true, element: <DemoRequestsPage /> },
      { path: 'invitations', element: <InvitationsPage /> },
      { path: 'workshops', element: <WorkshopsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
