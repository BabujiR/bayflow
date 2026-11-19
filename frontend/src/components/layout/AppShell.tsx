import { useEffect } from 'react';
import { Link, NavLink, Outlet } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { effectiveRoles } from '@/auth/roles';
import { FullPageSpinner } from '@/components/ui/spinner';
import { CustomerProfileGate } from '@/features/customers/CustomerProfileGate';
import { LocationSwitcher } from '@/features/locations/LocationSwitcher';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { setWorkshopZone } from '@/lib/format';
import { navItemsFor } from '@/lib/navigation';
import { cn } from '@/lib/utils';

import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';
import { UserMenu } from './UserMenu';

/** The signed-in layout: header with role-based navigation, then the page. */
export function AppShell() {
  const { user } = useAuth();
  const items = navItemsFor(effectiveRoles(user?.roles ?? []));
  const workshop = useWorkshop();
  // Signing out (or switching user) goes back to the browser's zone until the next workshop loads.
  useEffect(() => () => setWorkshopZone(undefined), []);

  return (
    <div className="min-h-svh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-card focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
        {/* Wider than the page content: admins have many links next to the location switcher. */}
        <div className="mx-auto flex h-16 max-w-screen-2xl items-center gap-4 px-4">
          <Link to="/" aria-label="Bayflow home" className="shrink-0">
            <Logo withTagline={!workshop.data} />
          </Link>
          {workshop.data && (
            <span
              className="hidden max-w-48 shrink-0 truncate border-l pl-4 text-sm font-semibold sm:inline"
              data-testid="workshop-name"
              title={workshop.data.displayName}
            >
              {workshop.data.displayName}
            </span>
          )}
          {/* Takes the room that is left and scrolls rather than run under the controls on the right. */}
          <nav
            aria-label="Main"
            className="hidden min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none] lg:flex"
          >
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex shrink-0 items-center gap-1 lg:ml-0">
            <LocationSwitcher />
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
        {/* Small screens: the same links in a scrollable row */}
        <nav aria-label="Main (compact)" className="flex gap-1 overflow-x-auto px-4 pb-2 lg:hidden">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} className={navLinkClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main id="main" className="mx-auto max-w-7xl px-4 py-8">
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
    </div>
  );
}

function navLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'shrink-0 rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
  );
}
