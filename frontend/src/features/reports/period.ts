import { getWorkshopZone, localInputToIso, todayIso } from '@/lib/format';

/**
 * Report periods. Days are the workshop's calendar days: a period runs from midnight of its
 * first day to midnight after its last day, in the workshop's time zone.
 */
export type PeriodPreset = 'today' | 'last7' | 'last30' | 'month' | 'lastMonth' | 'year' | 'custom';

export const PERIOD_LABELS: Record<PeriodPreset, string> = {
  today: 'Today',
  last7: 'Last 7 days',
  last30: 'Last 30 days',
  month: 'This month',
  lastMonth: 'Last month',
  year: 'This year',
  custom: 'Custom dates',
};

/** The longest period the API accepts. */
export const MAX_DAYS = 366;

export interface Period {
  preset: PeriodPreset;
  /** First day, YYYY-MM-DD. */
  firstDay: string;
  /** Last day (inclusive), YYYY-MM-DD. */
  lastDay: string;
  /** ISO instants for the API: [from, to). */
  from: string;
  to: string;
}

/** YYYY-MM-DD plus `days` (may be negative). */
export function addDays(day: string, days: number): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** Days from `first` to `last`, both included. */
export function daysBetween(first: string, last: string): number {
  const ms = Date.parse(`${last}T00:00:00Z`) - Date.parse(`${first}T00:00:00Z`);
  return Math.round(ms / 86_400_000) + 1;
}

/** Every day from `first` to `last`, both included. */
export function eachDay(first: string, last: string): string[] {
  const days: string[] = [];
  for (let day = first; day <= last; day = addDays(day, 1)) {
    days.push(day);
  }
  return days;
}

/** Midnight at the start of `day` in the workshop's zone (or `zone`), as an ISO instant. */
export function startOfDay(day: string, zone: string | undefined = getWorkshopZone()): string {
  return localInputToIso(`${day}T00:00`, zone);
}

/** The period for a preset, relative to today in the workshop's zone (or `zone`). */
export function periodFor(
  preset: Exclude<PeriodPreset, 'custom'>,
  now: Date = new Date(),
  zone: string | undefined = getWorkshopZone(),
): Period {
  const today = todayIso(now, zone);
  const monthStart = `${today.slice(0, 8)}01`;
  switch (preset) {
    case 'today':
      return customPeriod(today, today, 'today', zone);
    case 'last7':
      return customPeriod(addDays(today, -6), today, 'last7', zone);
    case 'last30':
      return customPeriod(addDays(today, -29), today, 'last30', zone);
    case 'month':
      return customPeriod(monthStart, today, 'month', zone);
    case 'lastMonth': {
      const lastMonthEnd = addDays(monthStart, -1);
      return customPeriod(`${lastMonthEnd.slice(0, 8)}01`, lastMonthEnd, 'lastMonth', zone);
    }
    case 'year':
      return customPeriod(`${today.slice(0, 4)}-01-01`, today, 'year', zone);
  }
}

/** A period from `firstDay` to `lastDay`, both included, in the workshop's zone (or `zone`). */
export function customPeriod(
  firstDay: string,
  lastDay: string,
  preset: PeriodPreset = 'custom',
  zone: string | undefined = getWorkshopZone(),
): Period {
  return {
    preset,
    firstDay,
    lastDay,
    from: startOfDay(firstDay, zone),
    to: startOfDay(addDays(lastDay, 1), zone),
  };
}

/** Why custom dates can't be used, or null when they can. */
export function customPeriodProblem(firstDay: string, lastDay: string): string | null {
  if (!firstDay || !lastDay) {
    return 'Choose both dates.';
  }
  if (firstDay > lastDay) {
    return 'The first day must be on or before the last day.';
  }
  if (daysBetween(firstDay, lastDay) > MAX_DAYS) {
    return `Choose ${MAX_DAYS} days or fewer.`;
  }
  return null;
}
