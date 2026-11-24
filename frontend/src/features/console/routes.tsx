import type { RouteObject } from 'react-router';

import { NotFoundPage } from '@/pages/NotFoundPage';

import { ConsoleLayout } from './ConsoleLayout';
import { DemoRequestsPage } from './DemoRequestsPage';

/** <base domain>/console: the Ramplight console (Ramplight's own team). */
export const consoleRoutes: RouteObject[] = [
  {
    path: '/console',
    element: <ConsoleLayout />,
    children: [
      { index: true, element: <DemoRequestsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
