import { Navigate, Outlet, useLocation } from 'react-router';

import type { Role } from '@/api/types';
import { FullPageSpinner } from '@/components/ui/spinner';
import { PRODUCT_NAME } from '@/lib/brand';
import { ForbiddenPage } from '@/pages/ForbiddenPage';

import { useAuth } from './auth-context';

/** Where someone signed in with a temporary password chooses their own first. */
export const CHANGE_PASSWORD_PATH = '/change-password';

/**
 * Signed-in users only; everyone else goes to the login page and comes back afterwards. Someone
 * signed in with a temporary password (after a staff reset) chooses a new one before anything else.
 */
export function RequireAuth() {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return <FullPageSpinner label={`Loading ${PRODUCT_NAME}`} />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (user?.mustChangePassword && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace state={{ from: location.pathname + location.search }} />;
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
    return <FullPageSpinner label={`Loading ${PRODUCT_NAME}`} />;
  }
  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from.startsWith('/') ? from : '/'} replace />;
  }
  return <Outlet />;
}
