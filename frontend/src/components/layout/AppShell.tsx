import { Link, NavLink, Outlet } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { navItemsFor } from '@/lib/navigation';
import { cn } from '@/lib/utils';

import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';
import { UserMenu } from './UserMenu';

/** The signed-in layout: header with role-based navigation, then the page. */
export function AppShell() {
  const { user } = useAuth();
  const items = navItemsFor(user?.roles ?? []);

  return (
    <div className="min-h-svh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-card focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4">
          <Link to="/" aria-label="Bayflow home">
            <Logo withTagline />
          </Link>
          <nav aria-label="Main" className="hidden flex-1 items-center gap-1 md:flex">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
        {/* Small screens: the same links in a scrollable row */}
        <nav aria-label="Main (compact)" className="flex gap-1 overflow-x-auto px-4 pb-2 md:hidden">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} className={navLinkClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main id="main" className="mx-auto max-w-7xl px-4 py-8">
        <Outlet />
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
