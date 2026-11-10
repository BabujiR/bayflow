import { Link } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { ROLE_LABELS } from '@/auth/roles';
import { Badge } from '@/components/ui/badge';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowRightIcon } from '@/components/ui/icons';
import { navItemsFor } from '@/lib/navigation';

export function HomePage() {
  const { user } = useAuth();
  if (!user) {
    return null;
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
