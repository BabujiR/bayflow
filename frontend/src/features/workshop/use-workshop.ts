import { useQuery } from '@tanstack/react-query';

import { workshopApi } from '@/api/endpoints';
import { setWorkshopZone } from '@/lib/format';

export const WORKSHOP_QUERY_KEY = ['workshop'] as const;

/**
 * The signed-in user's workshop. Loading it also switches date formatting to the workshop's
 * time zone, before any page renders a date.
 */
export function useWorkshop() {
  return useQuery({
    queryKey: WORKSHOP_QUERY_KEY,
    queryFn: async () => {
      const workshop = await workshopApi.current();
      setWorkshopZone(workshop.timeZone);
      return workshop;
    },
    staleTime: 5 * 60_000,
  });
}
