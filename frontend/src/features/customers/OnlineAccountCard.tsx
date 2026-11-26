import { useQuery } from '@tanstack/react-query';

import { customerAccountsApi } from '@/api/endpoints';
import { useAuth } from '@/auth/auth-context';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate, formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { ResetPasswordButton } from '../users/ResetPasswordButton';

export const customerAccountKey = (userId: string) => ['customer-account', userId];

/**
 * A customer's online account on their page: who they sign in as, its status, and what the
 * shop can do (advisors: reset the password, unlock; managers and admins: also disable or
 * enable sign-in). Walk-in customers have none.
 */
export function OnlineAccountCard({ userId, name }: { userId: string | null; name: string }) {
  const { hasRole } = useAuth();
  const canDisable = hasRole('MANAGER', 'ADMIN');

  if (!userId) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Online account</CardTitle>
          <CardDescription>
            No online account: a walk-in customer. They can register at your address, or book online at /book
            without one.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }
  return <AccountCard userId={userId} name={name} canDisable={canDisable} />;
}

function AccountCard({ userId, name, canDisable }: { userId: string; name: string; canDisable: boolean }) {
  const account = useQuery({
    queryKey: customerAccountKey(userId),
    queryFn: () => customerAccountsApi.get(userId),
  });
  const invalidate = [customerAccountKey(userId), ['customer-accounts']];
  const setEnabled = useAction((enabled: boolean) => customerAccountsApi.setEnabled(userId, enabled), {
    success: (a) =>
      a.enabled ? `${name} can sign in again` : `${name} can't sign in any more (signed out everywhere)`,
    invalidate,
  });
  const unlock = useAction(() => customerAccountsApi.unlock(userId), {
    success: `${name}'s account is unlocked`,
    invalidate,
  });

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1.5">
          <CardTitle>Online account</CardTitle>
          <CardDescription>
            How {name} signs in to see their bookings, estimates and invoices.
          </CardDescription>
        </div>
        <div className="flex flex-wrap gap-2">
          <ResetPasswordButton userId={userId} name={name} />
          {account.data?.locked && (
            <Button
              size="sm"
              variant="outline"
              disabled={unlock.isPending}
              onClick={() => unlock.mutate(undefined)}
            >
              Unlock
            </Button>
          )}
          {canDisable && account.data && (
            <Button
              size="sm"
              variant={account.data.enabled ? 'outline' : 'default'}
              disabled={setEnabled.isPending}
              onClick={() => setEnabled.mutate(!account.data!.enabled)}
            >
              {account.data.enabled ? 'Disable sign-in' : 'Enable sign-in'}
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <QueryState query={account}>
          {(a) => (
            <dl className="grid gap-2 text-sm sm:grid-cols-[9rem_1fr]">
              <dt className="text-muted-foreground">Username</dt>
              <dd className="font-medium">{a.username}</dd>
              <dt className="text-muted-foreground">Sign-in email</dt>
              <dd>{a.email}</dd>
              <dt className="text-muted-foreground">Status</dt>
              <dd className="flex flex-wrap items-center gap-2">
                <AccountStatusBadge enabled={a.enabled} locked={a.locked} />
                {a.locked && a.lockedUntil && (
                  <span className="text-muted-foreground">
                    after too many failed sign-ins, until {formatDateTime(a.lockedUntil)}
                  </span>
                )}
                {a.mustChangePassword && (
                  <span className="text-muted-foreground">· has a temporary password from a reset</span>
                )}
              </dd>
              <dt className="text-muted-foreground">Created</dt>
              <dd>{formatDate(a.createdAt)}</dd>
            </dl>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}

/** Active, Locked or Disabled. */
export function AccountStatusBadge({ enabled, locked }: { enabled: boolean; locked: boolean }) {
  if (!enabled) {
    return <Badge variant="outline">Disabled</Badge>;
  }
  if (locked) {
    return <Badge variant="warning">Locked</Badge>;
  }
  return <Badge variant="success">Active</Badge>;
}
