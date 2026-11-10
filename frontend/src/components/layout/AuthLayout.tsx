import { Outlet } from 'react-router';

import { Logo, TAGLINE } from './Logo';
import { ThemeToggle } from './ThemeToggle';

/** Layout for the login and register pages. */
export function AuthLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-gradient-to-b from-primary/5 to-background">
      <div className="flex justify-end p-4">
        <ThemeToggle />
      </div>
      <div className="flex flex-1 flex-col items-center justify-center gap-6 px-4 pb-16">
        <div className="flex flex-col items-center gap-2 text-center">
          <Logo />
          <p className="text-sm text-muted-foreground">{TAGLINE}</p>
        </div>
        <div className="w-full max-w-sm">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
