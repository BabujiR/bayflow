import { useQuery } from '@tanstack/react-query';

import { tenantsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';

/** The workshop at this address, for the sign-in and registration pages (public). */
export function usePublicWorkshop() {
  return useQuery({
    queryKey: ['public-workshop'],
    queryFn: tenantsApi.current,
    staleTime: 5 * 60_000,
    retry: (failureCount, error) => !(error instanceof ApiError && error.isClientError) && failureCount < 2,
  });
}

export const isWorkshopNotFound = (error: unknown) =>
  error instanceof ApiError && error.code === 'workshop-not-found';
