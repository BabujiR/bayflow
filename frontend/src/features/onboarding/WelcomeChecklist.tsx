import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { brandingApi, cannedJobsApi, locationsApi, remindersApi, usersApi } from '@/api/endpoints';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PRODUCT_NAME } from '@/lib/brand';
import { cn } from '@/lib/utils';

const STORAGE_KEY = 'ramplight.welcome-dismissed';

function dismissedHere(): boolean {
  try {
    return window.localStorage.getItem(`${STORAGE_KEY}:${window.location.host}`) === '1';
  } catch {
    return false;
  }
}

function rememberDismissed(): void {
  try {
    window.localStorage.setItem(`${STORAGE_KEY}:${window.location.host}`, '1');
  } catch {
    // Storage unavailable: it just shows again next time.
  }
}

interface Step {
  key: string;
  title: string;
  text: string;
  to: string;
  done: boolean | undefined;
  optional?: boolean;
}

/**
 * "Get your workshop ready": the first things a new owner (ADMIN) does, each ticked off from the
 * workshop's own data. Hidden once everything required is done, or when the owner dismisses it
 * (remembered in this browser for this workshop).
 */
export function WelcomeChecklist() {
  const [dismissed, setDismissed] = useState(dismissedHere);
  const enabled = !dismissed;
  const branding = useQuery({ queryKey: ['branding'], queryFn: brandingApi.get, enabled });
  const users = useQuery({
    queryKey: ['users', 'welcome'],
    queryFn: () => usersApi.list({ page: 0, size: 50 }),
    enabled,
  });
  const cannedJobs = useQuery({
    queryKey: ['canned-jobs', 'welcome'],
    queryFn: () => cannedJobsApi.list(),
    enabled,
  });
  const intervals = useQuery({ queryKey: ['service-intervals'], queryFn: remindersApi.intervals, enabled });
  const locations = useQuery({ queryKey: ['locations'], queryFn: locationsApi.list, enabled });

  if (dismissed) {
    return null;
  }
  const staff = users.data?.content.filter((u) => u.roles.some((r) => r !== 'CUSTOMER')).length;
  const steps: Step[] = [
    {
      key: 'logo',
      title: 'Add your logo',
      text: 'Your customers see it on the sign-in page, estimates and invoices.',
      to: '/settings?tab=branding',
      done: branding.data ? branding.data.logoUrl != null : undefined,
    },
    {
      key: 'team',
      title: 'Add your team',
      text: 'Accounts for your advisors and mechanics.',
      to: '/users',
      done: staff === undefined ? undefined : staff > 1,
    },
    {
      key: 'canned',
      title: 'Set up canned jobs',
      text: 'Your everyday work with its labour and parts, for one-click estimates.',
      to: '/canned-jobs',
      done: cannedJobs.data ? cannedJobs.data.length > 0 : undefined,
    },
    {
      key: 'intervals',
      title: 'Choose service intervals',
      text: 'So customers are reminded when their car is due again.',
      to: '/settings?tab=reminders',
      done: intervals.data ? intervals.data.length > 0 : undefined,
    },
    {
      key: 'locations',
      title: 'Add your other locations',
      text: 'For groups: each shop with its own team, rates and time zone.',
      to: '/locations',
      done: locations.data ? locations.data.length > 1 : undefined,
      optional: true,
    },
  ];
  const required = steps.filter((s) => !s.optional);
  if (required.some((s) => s.done === undefined)) {
    return null; // still loading (or a list this account can't read): show nothing rather than guess
  }
  if (required.every((s) => s.done)) {
    return null;
  }
  const doneCount = steps.filter((s) => s.done).length;
  return (
    <Card aria-labelledby="welcome-heading" role="region">
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
        <div>
          <CardTitle id="welcome-heading">Get your workshop ready</CardTitle>
          <CardDescription>
            Welcome to {PRODUCT_NAME}! {doneCount} of {steps.length} done.
          </CardDescription>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            rememberDismissed();
            setDismissed(true);
          }}
        >
          Dismiss
        </Button>
      </CardHeader>
      <CardContent>
        <ol className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {steps.map((step) => (
            <li key={step.key} data-testid={`welcome-${step.key}`} data-done={step.done ? 'true' : 'false'}>
              <Link
                to={step.to}
                className={cn(
                  'flex h-full gap-3 rounded-lg border p-3 transition-colors hover:border-primary/50',
                  step.done && 'bg-muted/40',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border text-xs',
                    step.done ? 'border-success bg-success text-white' : 'border-muted-foreground/40',
                  )}
                >
                  {step.done ? '✓' : ''}
                </span>
                <span>
                  <span
                    className={cn(
                      'block text-sm font-medium',
                      step.done && 'text-muted-foreground line-through',
                    )}
                  >
                    {step.title}
                    {step.optional && <span className="font-normal text-muted-foreground"> (optional)</span>}
                    <span className="sr-only">{step.done ? ' (done)' : ' (to do)'}</span>
                  </span>
                  <span className="block text-xs text-muted-foreground">{step.text}</span>
                </span>
              </Link>
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}
