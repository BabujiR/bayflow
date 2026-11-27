import { Link } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { ROLE_LABELS } from '@/auth/roles';
import { Badge } from '@/components/ui/badge';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowRightIcon } from '@/components/ui/icons';
import { OwnerRolesPrompt } from '@/features/onboarding/OwnerRolesPrompt';
import { WelcomeChecklist } from '@/features/onboarding/WelcomeChecklist';
import { DashboardPage } from '@/features/reports/DashboardPage';
import { navItemsFor } from '@/lib/navigation';

export function HomePage() {
  const { user } = useAuth();
  if (!user) {
    return null;
  }
  // Owners and managers start on their numbers; a new owner also sees what to set up first.
  // An owner who also runs the desk or works on cars gets their day's work above the numbers.
  if (user.roles.includes('ADMIN')) {
    return (
      <div className="grid gap-6">
        <WelcomeChecklist />
        <OwnerRolesPrompt />
        <TodaysWork />
        <DashboardPage />
      </div>
    );
  }
  if (user.roles.includes('MANAGER')) {
    return (
      <div className="grid gap-6">
        <TodaysWork />
        <DashboardPage />
      </div>
    );
  }
  const items = navItemsFor(user.roles);

  return (
    <div className="grid gap-8">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight">Welcome, {user.username}</h1>
        {user.roles.map((role) => (
          <Badge key={role}>{ROLE_LABELS[role]}</Badge>
        ))}
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((item) => (
          <li key={item.to}>
            <Link to={item.to} className="group block h-full rounded-xl focus-visible:outline-2">
              <Card className="h-full transition-colors group-hover:border-primary/50">
                <CardHeader>
                  <CardTitle className="flex items-center justify-between">
                    {item.label}
                    <ArrowRightIcon className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                  </CardTitle>
                  <CardDescription>{item.description}</CardDescription>
                </CardHeader>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Shortcuts to the day's work for someone who also runs the desk or works on cars. */
function TodaysWork() {
  const { user } = useAuth();
  const shortcuts = [
    user?.roles.includes('SERVICE_ADVISOR') && {
      to: '/board',
      title: "Today's board",
      text: 'Confirm, mark arrived and check in.',
    },
    user?.roles.includes('MECHANIC') && {
      to: '/my-jobs',
      title: 'My jobs',
      text: 'Approved and in-progress work waiting for you.',
    },
  ].filter((s): s is { to: string; title: string; text: string } => Boolean(s));
  if (shortcuts.length === 0) {
    return null;
  }
  return (
    <section aria-label="Your work today" className="grid gap-4 sm:grid-cols-2">
      {shortcuts.map((s) => (
        <Link key={s.to} to={s.to} className="group block rounded-xl focus-visible:outline-2">
          <Card className="h-full transition-colors group-hover:border-primary/50">
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                {s.title}
                <ArrowRightIcon className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
              </CardTitle>
              <CardDescription>{s.text}</CardDescription>
            </CardHeader>
          </Card>
        </Link>
      ))}
    </section>
  );
}
