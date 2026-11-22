import { useEffect } from 'react';
import { Link, NavLink, Outlet } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { effectiveRoles } from '@/auth/roles';
import { FullPageSpinner } from '@/components/ui/spinner';
import { BrandBanner } from '@/features/branding/BrandBanner';
import { PoweredBy } from '@/features/branding/PoweredBy';
import { useBranding } from '@/features/branding/use-branding';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { CustomerProfileGate } from '@/features/customers/CustomerProfileGate';
import { LocationSwitcher } from '@/features/locations/LocationSwitcher';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { PRODUCT_NAME } from '@/lib/brand';
import { setWorkshopZone } from '@/lib/format';
import { navItemsFor } from '@/lib/navigation';
import { cn } from '@/lib/utils';

import { MobileMenu, Sidebar } from './Sidebar';
import { ThemeToggle } from './ThemeToggle';
import { UserMenu } from './UserMenu';

/**
 * The signed-in layout. Staff: a sidebar of their links in sections (collapsible; a slide-over
 * menu on small screens) and a slim header with the location switcher, bell, theme and account.
 * Customers, with only a few links: one header row with the links in it.
 */
export function AppShell() {
  const { user } = useAuth();
  const roles = effectiveRoles(user?.roles ?? []);
  const workshop = useWorkshop();
  const { branding, displayName: brandName } = useBranding();
  const isCustomerOnly = roles.length > 0 && roles.every((role) => role === 'CUSTOMER');
  // Signing out (or switching user) goes back to the browser's zone until the next workshop loads.
  useEffect(() => () => setWorkshopZone(undefined), []);

  const homeLabel = branding.logoUrl && brandName ? `${brandName} home` : `${PRODUCT_NAME} home`;
  // The workshop's own logo already names it; without one, the product logo needs the name.
  const showName = !!workshop.data && !branding.logoUrl;

  const page = (
    <main id="main" className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 lg:px-8">
      {workshop.isPending ? (
        // Wait for the workshop so the first dates already use its time zone.
        // If it fails, pages still work in the browser's zone.
        <FullPageSpinner label="Loading your workshop" />
      ) : (
        <CustomerProfileGate>
          <Outlet />
        </CustomerProfileGate>
      )}
    </main>
  );

  if (isCustomerOnly) {
    const items = navItemsFor(roles);
    return (
      <div className="flex min-h-svh flex-col">
        <SkipLink />
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4">
            <Link to="/" aria-label={homeLabel} className="shrink-0">
              <WorkshopLogo withTagline={!workshop.data} />
            </Link>
            {showName && (
              <span
                className="hidden max-w-48 shrink-0 truncate border-l pl-4 text-sm font-semibold sm:inline"
                data-testid="workshop-name"
                title={workshop.data?.displayName}
              >
                {workshop.data?.displayName}
              </span>
            )}
            <nav aria-label="Main" className="hidden min-w-0 flex-1 items-center gap-1 md:flex">
              {items.map((item) => (
                <NavLink key={item.to} to={item.to} className={navLinkClass}>
                  {item.label}
                </NavLink>
              ))}
            </nav>
            <div className="ml-auto flex shrink-0 items-center gap-1 md:ml-0">
              <NotificationBell />
              <ThemeToggle />
              <UserMenu />
            </div>
          </div>
          {/* Small screens: the same links in a row under the header */}
          <nav aria-label="Main (compact)" className="flex gap-1 overflow-x-auto px-4 pb-2 md:hidden">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <BrandBanner variant="full" />
        </header>
        {page}
        <PoweredBy />
      </div>
    );
  }

  const menu = { roles, homeLabel, workshopName: workshop.data?.displayName, showName };
  return (
    <div className="flex min-h-svh">
      <SkipLink />
      <Sidebar {...menu} />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="flex h-16 items-center gap-2 px-4 lg:px-8">
            <MobileMenu {...menu} />
            <Link to="/" aria-label={homeLabel} className="shrink-0 lg:hidden">
              <WorkshopLogo />
            </Link>
            <div className="ml-auto flex shrink-0 items-center gap-1">
              <LocationSwitcher />
              <NotificationBell />
              <ThemeToggle />
              <UserMenu />
            </div>
          </div>
          <BrandBanner variant="slim" />
        </header>
        {page}
        <PoweredBy />
      </div>
    </div>
  );
}

function SkipLink() {
  return (
    <a
      href="#main"
      className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-card focus:px-3 focus:py-2"
    >
      Skip to content
    </a>
  );
}

function navLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'shrink-0 rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
  );
}
