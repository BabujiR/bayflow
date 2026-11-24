import { Link, Outlet } from 'react-router';

import { Logo } from '@/components/layout/Logo';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { Button } from '@/components/ui/button';
import { PRODUCT_NAME } from '@/lib/brand';

/** The public site at the base domain: landing page and workshop sign-up. No sign-in here. */
export function SiteLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-gradient-to-b from-primary/5 to-background">
      <header className="mx-auto flex w-full max-w-6xl items-center gap-4 px-4 py-4">
        <Link to="/" aria-label={`${PRODUCT_NAME} home`}>
          <Logo withTagline />
        </Link>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <Button asChild size="sm" variant="outline" className="hidden sm:inline-flex">
            <a href="/#demo">Request a demo</a>
          </Button>
          <Button asChild size="sm">
            <Link to="/signup">Set up your workshop</Link>
          </Button>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16">
        <Outlet />
      </main>
      <footer className="border-t">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-6 text-sm text-muted-foreground">
          <span>
            © {new Date().getFullYear()} {PRODUCT_NAME}
          </span>
          <span>Shop management for independent auto workshops</span>
        </div>
      </footer>
    </div>
  );
}
