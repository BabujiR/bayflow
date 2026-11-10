import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';

import { customersApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { QueryState } from '@/components/common/QueryState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAction } from '@/lib/use-action';

import { ProfileForm } from './ProfileForm';

/**
 * Customers need a profile (name, contact details) before they can add vehicles or book.
 * Until they have one, this shows the profile form instead of the page.
 */
export function CustomerProfileGate({ children }: { children: ReactNode }) {
  const { user, hasRole } = useAuth();
  const isCustomer = hasRole('CUSTOMER');
  const profile = useQuery({
    queryKey: ['my-profile'],
    queryFn: customersApi.me,
    enabled: isCustomer,
    retry: false,
  });
  const create = useAction(customersApi.createMe, {
    success: 'Profile saved. Welcome to Bayflow!',
    invalidate: [['my-profile']],
  });

  if (!isCustomer) {
    return <>{children}</>;
  }
  if (profile.error instanceof ApiError && profile.error.status === 404) {
    return (
      <Card className="mx-auto max-w-lg">
        <CardHeader>
          <CardTitle>Complete your profile</CardTitle>
          <CardDescription>
            Tell us how to reach you. You only need to do this once, before adding vehicles or booking a service.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ProfileForm
            submitLabel="Save profile"
            defaultValues={{ email: user?.email ?? '' }}
            onSubmit={(values) => create.mutateAsync(values)}
          />
        </CardContent>
      </Card>
    );
  }
  return <QueryState query={profile}>{() => children}</QueryState>;
}
