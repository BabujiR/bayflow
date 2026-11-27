import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, type ReactNode } from 'react';

import { customersApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { QueryState } from '@/components/common/QueryState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PRODUCT_NAME } from '@/lib/brand';
import { forgetClaim, pendingClaim } from '@/features/booking/claim';
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
    success: `Profile saved. Welcome to ${PRODUCT_NAME}!`,
    invalidate: [['my-profile']],
  });
  // An online booking they came from ("Create an account" / "sign in" on its page) joins the account.
  const claim = useAction(customersApi.claim, {
    success: 'Your online booking is now in your account.',
    invalidate: [['my-profile'], ['appointments'], ['vehicles']],
    onSuccess: forgetClaim,
    onError: () => {
      forgetClaim();
      return false;
    },
  });
  const claimed = useRef(false);
  const token = isCustomer ? pendingClaim() : null;
  useEffect(() => {
    if (token && !claimed.current) {
      claimed.current = true;
      claim.mutate(token);
    }
  }, [token, claim]);

  if (!isCustomer) {
    return <>{children}</>;
  }
  if (token || claim.isPending) {
    return <p className="text-sm text-muted-foreground">Adding your online booking to your account…</p>;
  }
  if (profile.error instanceof ApiError && profile.error.status === 404) {
    return (
      <Card className="mx-auto max-w-lg">
        <CardHeader>
          <CardTitle>Complete your profile</CardTitle>
          <CardDescription>
            Tell us how to reach you. You only need to do this once, before adding vehicles or booking a
            service.
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
