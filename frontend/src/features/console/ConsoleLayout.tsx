import { zodResolver } from '@hookform/resolvers/zod';
import { type ReactNode, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, NavLink, Outlet } from 'react-router';
import { z } from 'zod';

import { errorMessage } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { Logo } from '@/components/layout/Logo';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { FullPageSpinner } from '@/components/ui/spinner';
import { cn } from '@/lib/utils';

/**
 * The Ramplight console at <base domain>/console: for Ramplight's own team (PLATFORM_ADMIN).
 * Signed out it shows the sign-in; a workshop account is turned away.
 */
export function ConsoleLayout() {
  const { status, user, logout, hasRole } = useAuth();
  if (status === 'loading') {
    return <FullPageSpinner label="Loading the Ramplight console" />;
  }
  if (status === 'anonymous') {
    return <ConsoleLogin />;
  }
  if (!hasRole('PLATFORM_ADMIN')) {
    return (
      <Shell>
        <Card className="mx-auto mt-16 max-w-md">
          <CardHeader>
            <CardTitle>Not a console account</CardTitle>
            <CardDescription>
              The console is for Ramplight&apos;s team. Workshop accounts sign in at their workshop&apos;s
              address.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void logout()}>
              Sign out
            </Button>
          </CardContent>
        </Card>
      </Shell>
    );
  }
  return (
    <Shell
      nav={
        <nav aria-label="Console" className="flex items-center gap-1">
          <ConsoleLink to="/console" end>
            Demo requests
          </ConsoleLink>
          <ConsoleLink to="/console/invitations">Invitations</ConsoleLink>
          <ConsoleLink to="/console/workshops">Workshops</ConsoleLink>
        </nav>
      }
      account={
        <div className="flex items-center gap-2 text-sm">
          <span className="hidden text-muted-foreground sm:inline">{user?.username}</span>
          <Button size="sm" variant="outline" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      }
    >
      <Outlet />
    </Shell>
  );
}

function ConsoleLink({ to, end, children }: { to: string; end?: boolean; children: string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'rounded-md px-3 py-1.5 text-sm font-medium',
          isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:text-foreground',
        )
      }
    >
      {children}
    </NavLink>
  );
}

function Shell({ nav, account, children }: { nav?: ReactNode; account?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="border-b">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-4 px-4 py-3">
          <Link to="/console" aria-label="Ramplight console home" className="flex items-center gap-2">
            <Logo />
            <span className="rounded bg-primary/10 px-2 py-0.5 text-xs font-semibold uppercase text-primary">
              Console
            </span>
          </Link>
          {nav}
          <div className="ml-auto flex items-center gap-2">
            <ThemeToggle />
            {account}
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        {children}
      </main>
    </div>
  );
}

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
});

type LoginValues = z.infer<typeof loginSchema>;

function ConsoleLogin() {
  const { login } = useAuth();
  const [problem, setProblem] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });
  return (
    <Shell>
      <Card className="mx-auto mt-16 w-full max-w-sm">
        <CardHeader>
          <CardTitle>Sign in to the console</CardTitle>
          <CardDescription>For Ramplight&apos;s team.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            noValidate
            aria-label="Sign in to the console"
            className="grid gap-4"
            onSubmit={handleSubmit(async (values) => {
              setProblem(null);
              try {
                await login(values);
              } catch (error) {
                setProblem(errorMessage(error));
              }
            })}
          >
            <Field label="Username" error={formState.errors.username?.message}>
              <Input autoComplete="username" {...register('username')} />
            </Field>
            <Field label="Password" error={formState.errors.password?.message}>
              <Input type="password" autoComplete="current-password" {...register('password')} />
            </Field>
            {problem && (
              <p role="alert" className="text-sm text-destructive">
                {problem}
              </p>
            )}
            <Button type="submit" disabled={formState.isSubmitting}>
              {formState.isSubmitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </Shell>
  );
}
