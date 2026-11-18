import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { useAuth } from '@/auth/auth-context';
import { Select } from '@/components/ui/select';

import { loadLocationChoiceFor, setLocationChoice } from './selection';
import { LOCATIONS_QUERY_KEY, useLocationChoice, useMyLocations } from './use-locations';

/**
 * The header's location switcher: "All locations" or one of the user's locations. Shown only to
 * staff who work with more than one location; the choice narrows every list and board.
 */
export function LocationSwitcher() {
  const { user, hasRole } = useAuth();
  const queryClient = useQueryClient();
  const mine = useMyLocations();
  const choice = useLocationChoice();
  const userId = user?.id ?? null;

  // Each user's last choice, remembered in this browser.
  useEffect(() => {
    if (loadLocationChoiceFor(userId)) {
      void queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== LOCATIONS_QUERY_KEY[0] });
    }
    return () => {
      loadLocationChoiceFor(null);
    };
  }, [userId, queryClient]);

  // A location the user no longer works at (or archived) falls back to all of theirs.
  useEffect(() => {
    if (choice !== 'all' && mine.length > 0 && !mine.some((l) => l.id === choice)) {
      setLocationChoice('all');
    }
  }, [choice, mine]);

  // Customers pick a location when booking; staff with one location have nothing to switch.
  if (!hasRole('ADMIN', 'SERVICE_ADVISOR', 'MECHANIC') || mine.length < 2) {
    return null;
  }

  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="sr-only">Location</span>
      <Select
        aria-label="Location"
        className="h-9 w-44"
        value={choice}
        onChange={(e) => {
          setLocationChoice(e.target.value);
          // Every list depends on the location; the location list itself does not.
          void queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== LOCATIONS_QUERY_KEY[0] });
        }}
      >
        <option value="all">All locations</option>
        {mine.map((l) => (
          <option key={l.id} value={l.id}>
            {l.name}
          </option>
        ))}
      </Select>
    </label>
  );
}
