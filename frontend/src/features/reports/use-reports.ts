import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router';

import { reportsApi, type ReportRange } from '@/api/endpoints';

import { customPeriod, customPeriodProblem, periodFor, type Period, type PeriodPreset } from './period';

export const REPORTS_QUERY_KEY = 'reports';

/** The dashboard refreshes itself while it is open. */
const DASHBOARD_REFRESH_MS = 60_000;

const PRESETS: PeriodPreset[] = ['today', 'last7', 'last30', 'month', 'lastMonth', 'year'];

/**
 * The period in the address (`?period=last30`, or `?from=2026-11-01&to=2026-11-21` for custom
 * dates), so a report can be bookmarked or shared. `zone` is the workshop's time zone: periods
 * are worked out again when it is known.
 */
export function usePeriodParam(
  zone: string | undefined,
  fallback: Exclude<PeriodPreset, 'custom'> = 'month',
): [Period, (next: Period) => void] {
  const [params, setParams] = useSearchParams();
  const preset = params.get('period');
  const first = params.get('from');
  const last = params.get('to');

  const period = useMemo(() => {
    if (first && last && !customPeriodProblem(first, last)) {
      return customPeriod(first, last, 'custom', zone);
    }
    const chosen = PRESETS.includes(preset as PeriodPreset) ? (preset as Exclude<PeriodPreset, 'custom'>) : fallback;
    return periodFor(chosen, new Date(), zone);
  }, [preset, first, last, fallback, zone]);

  const setPeriod = (next: Period) => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        updated.delete('period');
        updated.delete('from');
        updated.delete('to');
        if (next.preset === 'custom') {
          updated.set('from', next.firstDay);
          updated.set('to', next.lastDay);
        } else {
          updated.set('period', next.preset);
        }
        return updated;
      },
      { replace: true },
    );
  };

  return [period, setPeriod];
}

const range = (period: Period): ReportRange => ({ from: period.from, to: period.to });

export function useReportSummary(period: Period, live = false) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'summary', period.from, period.to],
    queryFn: () => reportsApi.summary(range(period)),
    refetchInterval: live ? DASHBOARD_REFRESH_MS : false,
  });
}

export function useDailySales(period: Period, zone: string | undefined, live = false) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'daily', period.from, period.to, zone],
    queryFn: () => reportsApi.daily(range(period), zone),
    refetchInterval: live ? DASHBOARD_REFRESH_MS : false,
  });
}

export function useTechnicianReport(period: Period, live = false) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'technicians', period.from, period.to],
    queryFn: () => reportsApi.technicians(range(period)),
    refetchInterval: live ? DASHBOARD_REFRESH_MS : false,
  });
}

export function useLocationSales(period: Period, live = false) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'locations', period.from, period.to],
    queryFn: () => reportsApi.locations(range(period)),
    refetchInterval: live ? DASHBOARD_REFRESH_MS : false,
  });
}

export function useAdvisorReport(period: Period) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'advisors', period.from, period.to],
    queryFn: () => reportsApi.advisors(range(period)),
  });
}

export function useDeclinedReport(period: Period) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'declined', period.from, period.to],
    queryFn: () => reportsApi.declined(range(period)),
  });
}

export function usePartsReport(period: Period) {
  return useQuery({
    queryKey: [REPORTS_QUERY_KEY, 'parts', period.from, period.to],
    queryFn: () => reportsApi.parts(range(period)),
  });
}
