import { Outlet } from 'react-router';

import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { BrandBanner } from '@/features/branding/BrandBanner';
import { PoweredBy } from '@/features/branding/PoweredBy';
import { useBranding } from '@/features/branding/use-branding';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { isWorkshopNotFound, usePublicWorkshop } from '@/features/workshop/use-public-workshop';
import { cn } from '@/lib/utils';
import { WorkshopNotFoundPage } from '@/pages/WorkshopNotFoundPage';

import { TAGLINE } from './Logo';
import { ThemeToggle } from './ThemeToggle';

/**
 * Layout for the login and register pages of one workshop's address, in the workshop's
 * branding: its logo, announcement and cover photo (beside the form on wide screens, above it
 * on phones).
 */
export function AuthLayout() {
  const workshop = usePublicWorkshop();
  const { branding } = useBranding();
  const notFound = workshop.isError && isWorkshopNotFound(workshop.error);
  const cover = !notFound ? branding.coverUrl : null;

  return (
    <div
      className={cn(
        'min-h-svh bg-gradient-to-b from-primary/5 to-background',
        cover && 'lg:grid lg:grid-cols-2',
      )}
    >
      {cover && (
        <div
          className="relative h-40 overflow-hidden sm:h-56 lg:sticky lg:top-0 lg:h-svh"
          data-testid="workshop-cover"
        >
          <img src={cover} alt="" className="absolute inset-0 size-full object-cover" />
        </div>
      )}
      <div className="flex min-h-svh flex-col">
        <BrandBanner />
        <div className="flex justify-end p-4">
          <ThemeToggle />
        </div>
        <div className="flex flex-1 flex-col items-center justify-center gap-6 px-4 pb-16">
          <div className="flex flex-col items-center gap-2 text-center">
            <WorkshopLogo size="large" />
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
              <Alert>
                This workshop's account is suspended, so nobody can sign in. Please contact the workshop.
              </Alert>
            )}
            {notFound ? <WorkshopNotFoundPage /> : <Outlet />}
          </div>
        </div>
        <PoweredBy />
      </div>
    </div>
  );
}
