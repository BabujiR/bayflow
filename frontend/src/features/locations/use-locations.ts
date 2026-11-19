import { useQuery } from '@tanstack/react-query';
import { useCallback, useMemo, useSyncExternalStore } from 'react';

import { locationsApi } from '@/api/endpoints';
import type { Location } from '@/api/types';
import { useAuth } from '@/auth/auth-context';

import { getLocationChoice, subscribeLocationChoice, type LocationChoice } from './selection';

export const LOCATIONS_QUERY_KEY = ['locations'] as const;

/** The organization's locations (customers get the active ones only). */
export function useLocations() {
  const { user } = useAuth();
  return useQuery({
    queryKey: LOCATIONS_QUERY_KEY,
    queryFn: locationsApi.list,
    enabled: !!user,
    staleTime: 60_000,
  });
}

/**
 * The active locations the signed-in user works with: every one for admins and customers, their
 * own for managers, advisors and mechanics. Sorted by name.
 */
export function useMyLocations(): Location[] {
  const { user } = useAuth();
  const { data } = useLocations();
  return useMemo(() => {
    const active = (data ?? []).filter((l) => l.status === 'ACTIVE');
    if (!user || user.allLocations !== false) {
      return active;
    }
    const mine = new Set(user.locationIds ?? []);
    return active.filter((l) => mine.has(l.id));
  }, [data, user]);
}

/** The location switcher's current choice (`all` or a location id). */
export function useLocationChoice(): LocationChoice {
  return useSyncExternalStore(subscribeLocationChoice, getLocationChoice, getLocationChoice);
}

/** Looks up any location of the organization (archived ones too) by id. */
export function useLocationLookup() {
  const { data } = useLocations();
  const byId = useMemo(() => new Map((data ?? []).map((l) => [l.id, l])), [data]);
  const hasSeveral = (data ?? []).length > 1;
  const get = useCallback((id?: string | null) => (id ? byId.get(id) : undefined), [byId]);
  return {
    /** The location, if known. */
    get,
    /** Its time zone, for showing its times (undefined = the workshop's default zone). */
    zoneOf: useCallback((id?: string | null) => (id ? byId.get(id)?.timeZone : undefined), [byId]),
    /** Its name. */
    nameOf: useCallback(
      (id?: string | null) => (id ? (byId.get(id)?.name ?? 'Unknown location') : ''),
      [byId],
    ),
    /** True once the organization has (or had) more than one location: show location names. */
    hasSeveral,
  };
}

/**
 * The single location a screen that works "per day" (board, booking) uses: the switcher's choice if
 * it is one of mine, else my first location by name. Undefined while locations load.
 */
export function usePreferredLocation(): Location | undefined {
  const mine = useMyLocations();
  const choice = useLocationChoice();
  return mine.find((l) => l.id === choice) ?? mine[0];
}
