import { useState } from 'react';

import { usersApi } from '@/api/endpoints';
import { refreshSession } from '@/api/http';
import type { Role } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { rolesSummary, STAFF_ROLES } from '@/auth/roles';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAction } from '@/lib/use-action';

const DISMISSED_KEY = 'bayflow.owner-roles-asked';

function readDismissed(userId: string): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === userId;
  } catch {
    return false;
  }
}

/**
 * For an admin who is only an admin: "Do you also work in the shop?" Ticking the front desk or
 * working on cars adds those roles to their own account, so they get the board and My jobs too.
 * Asked once per browser (Not now hides it); roles can change later in User accounts.
 */
export function OwnerRolesPrompt() {
  const { user, updateUser } = useAuth();
  const [chosen, setChosen] = useState<Role[]>([]);
  const [hidden, setHidden] = useState(() => (user ? readDismissed(user.id) : true));
  const save = useAction(
    () =>
      usersApi.setRoles(user!.id, {
        roles: STAFF_ROLES.filter((r) => user!.roles.includes(r) || chosen.includes(r)),
      }),
    {
      success: (u) => `You're now ${rolesSummary(u.roles)}. Your new pages are in the menu.`,
      invalidate: [['users']],
      onSuccess: (saved) => {
        updateUser(saved);
        void refreshSession();
      },
    },
  );
  if (
    !user ||
    hidden ||
    !user.roles.includes('ADMIN') ||
    user.roles.includes('SERVICE_ADVISOR') ||
    user.roles.includes('MECHANIC')
  ) {
    return null;
  }
  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, user.id);
    } catch {
      // Private window: hidden until the page reloads.
    }
    setHidden(true);
  };
  const toggle = (role: Role, on: boolean) =>
    setChosen((current) => (on ? [...current, role] : current.filter((r) => r !== role)));

  return (
    <Card className="gb-step" aria-labelledby="owner-roles-title">
      <CardHeader>
        <CardTitle id="owner-roles-title">Do you also work in the shop?</CardTitle>
        <CardDescription>
          Many owners also run the desk or work on cars. Tick what you do and you&apos;ll get those pages too.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={chosen.includes('SERVICE_ADVISOR')}
            onChange={(e) => toggle('SERVICE_ADVISOR', e.target.checked)}
          />
          I also run the front desk (today&apos;s board, check-in, invoices)
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={chosen.includes('MECHANIC')}
            onChange={(e) => toggle('MECHANIC', e.target.checked)}
          />
          I also work on cars (My jobs)
        </label>
        <div className="flex gap-2">
          <Button disabled={chosen.length === 0 || save.isPending} onClick={() => save.mutate(undefined)}>
            {save.isPending ? 'Saving…' : 'Add to my account'}
          </Button>
          <Button variant="ghost" onClick={dismiss}>
            Not now
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
