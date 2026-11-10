import { Navigate, Outlet, useLocation } from 'react-router';

import type { Role } from '@/api/types';
import { FullPageSpinner } from '@/components/ui/spinner';
import { ForbiddenPage } from '@/pages/ForbiddenPage';

import { useAuth } from './auth-context';

/** Signed-in users only; everyone else goes to the login page and comes back afterwards. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return <FullPageSpinner label="Loading Bayflow" />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/** Only the given roles; others see the "not permitted" page. The API enforces the same rules. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasRole } = useAuth();
  return hasRole(...roles) ? <Outlet /> : <ForbiddenPage />;
}

/**
 * Login and register pages: signed-in users are sent into the app, to the page they
 * originally asked for if they were redirected here from one.
 */
export function GuestOnly() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return <FullPageSpinner label="Loading Bayflow" />;
  }
  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from.startsWith('/') ? from : '/'} replace />;
  }
  return <Outlet />;
}
