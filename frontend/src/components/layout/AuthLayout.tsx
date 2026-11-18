import { Outlet } from 'react-router';

import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { isWorkshopNotFound, usePublicWorkshop } from '@/features/workshop/use-public-workshop';
import { WorkshopNotFoundPage } from '@/pages/WorkshopNotFoundPage';

import { Logo, TAGLINE } from './Logo';
import { ThemeToggle } from './ThemeToggle';

/** Layout for the login and register pages of one workshop's address. */
export function AuthLayout() {
  const workshop = usePublicWorkshop();
  const notFound = workshop.isError && isWorkshopNotFound(workshop.error);

  return (
    <div className="flex min-h-svh flex-col bg-gradient-to-b from-primary/5 to-background">
      <div className="flex justify-end p-4">
        <ThemeToggle />
      </div>
      <div className="flex flex-1 flex-col items-center justify-center gap-6 px-4 pb-16">
        <div className="flex flex-col items-center gap-2 text-center">
          <Logo />
          {workshop.isPending ? (
            <Skeleton className="h-6 w-40" />
          ) : workshop.data ? (
            <h2 className="text-lg font-semibold" data-testid="workshop-name">
              {workshop.data.displayName}
            </h2>
          ) : (
            <p className="text-sm text-muted-foreground">{TAGLINE}</p>
          )}
        </div>
        <div className="grid w-full max-w-sm gap-4">
          {workshop.data && !workshop.data.active && (
            <Alert>This workshop's account is suspended, so nobody can sign in. Please contact the workshop.</Alert>
          )}
          {notFound ? <WorkshopNotFoundPage /> : <Outlet />}
        </div>
      </div>
    </div>
  );
}
