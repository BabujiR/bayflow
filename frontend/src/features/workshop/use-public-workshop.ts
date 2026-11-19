import { useQuery } from '@tanstack/react-query';

import { tenantsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';

/**
 * The public info of the workshop at this address (name, branding). The same for everyone,
 * so it survives sign-out: AuthProvider clears every other query then.
 */
export const PUBLIC_WORKSHOP_QUERY_KEY = ['public-workshop'] as const;

/** The workshop at this address, for the sign-in and registration pages (public). */
export function usePublicWorkshop() {
  return useQuery({
    queryKey: PUBLIC_WORKSHOP_QUERY_KEY,
    queryFn: tenantsApi.current,
    staleTime: 5 * 60_000,
    retry: (failureCount, error) => !(error instanceof ApiError && error.isClientError) && failureCount < 2,
  });
}

export const isWorkshopNotFound = (error: unknown) =>
  error instanceof ApiError && error.code === 'workshop-not-found';
