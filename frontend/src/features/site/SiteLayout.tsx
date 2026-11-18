import { Link, Outlet } from 'react-router';

import { Logo } from '@/components/layout/Logo';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { Button } from '@/components/ui/button';

/** The public site at the base domain: landing page and workshop sign-up. No sign-in here. */
export function SiteLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-gradient-to-b from-primary/5 to-background">
      <header className="mx-auto flex w-full max-w-5xl items-center gap-4 px-4 py-4">
        <Link to="/" aria-label="Bayflow home">
          <Logo withTagline />
        </Link>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <Button asChild size="sm">
            <Link to="/signup">Set up your workshop</Link>
          </Button>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 pb-16">
        <Outlet />
      </main>
    </div>
  );
}
