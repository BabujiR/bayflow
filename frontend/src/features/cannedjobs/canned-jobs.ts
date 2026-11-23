import { useQuery } from '@tanstack/react-query';

import { cannedJobsApi } from '@/api/endpoints';

export const CANNED_JOBS_QUERY_KEY = ['canned-jobs'] as const;

/** Active canned jobs usable at a location (its own and the organization-wide ones). */
export function useCannedJobs(locationId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: [...CANNED_JOBS_QUERY_KEY, 'for', locationId ?? 'all'],
    queryFn: () => cannedJobsApi.list({ locationId }),
    enabled,
    staleTime: 60_000,
  });
}
