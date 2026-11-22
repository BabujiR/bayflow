import type { DailySales } from '@/api/types';
import { formatMoney } from '@/lib/format';

import { eachDay } from './period';

export interface TrendBucket {
  /** YYYY-MM-DD (a day) or YYYY-MM (a month). */
  key: string;
  label: string;
  sales: number;
  carCount: number;
}

/** Longer periods are shown by month, so bars stay readable. */
const MAX_DAILY_BARS = 62;

const dayLabel = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
const monthLabel = new Intl.DateTimeFormat(undefined, { month: 'short', year: 'numeric', timeZone: 'UTC' });

/** One bucket per day (or month) of the period, zero where nothing was completed. */
export function trendBuckets(days: DailySales[], firstDay: string, lastDay: string): TrendBucket[] {
  const all = eachDay(firstDay, lastDay);
  const byMonth = all.length > MAX_DAILY_BARS;
  const buckets = new Map<string, TrendBucket>();
  for (const day of all) {
    const key = byMonth ? day.slice(0, 7) : day;
    if (!buckets.has(key)) {
      const date = new Date(`${byMonth ? `${key}-01` : key}T00:00:00Z`);
      buckets.set(key, {
        key,
        label: (byMonth ? monthLabel : dayLabel).format(date),
        sales: 0,
        carCount: 0,
      });
    }
  }
  for (const d of days) {
    const bucket = buckets.get(byMonth ? d.day.slice(0, 7) : d.day);
    if (bucket) {
      bucket.sales += d.sales;
      bucket.carCount += d.carCount;
    }
  }
  return [...buckets.values()];
}

/**
 * Completed sales over the period as bars (one series, so no legend: the heading names it).
 * Hovering or focusing a bar shows its figures; the Reports page has the same numbers as a table.
 */
export function SalesTrend({
  days,
  firstDay,
  lastDay,
  currency,
}: {
  days: DailySales[];
  firstDay: string;
  lastDay: string;
  currency: string;
}) {
  const buckets = trendBuckets(days, firstDay, lastDay);
  const max = Math.max(0, ...buckets.map((b) => b.sales));
  const total = buckets.reduce((sum, b) => sum + b.sales, 0);

  if (total === 0) {
    return (
      <p className="grid h-48 place-items-center rounded-md border border-dashed text-sm text-muted-foreground">
        No completed jobs in this period yet.
      </p>
    );
  }

  return (
    <figure className="grid gap-2">
      <div className="flex items-baseline justify-between text-xs text-muted-foreground">
        <span>{formatMoney(max, currency)}</span>
      </div>
      <ol
        aria-label="Sales by day"
        className="relative flex h-48 items-end gap-0.5 border-b border-border before:absolute before:inset-x-0 before:top-0 before:border-t before:border-dashed before:border-border"
      >
        {buckets.map((b) => {
          const height = max > 0 ? Math.max((b.sales / max) * 100, b.sales > 0 ? 1.5 : 0) : 0;
          const summary = `${b.label}: ${formatMoney(b.sales, currency)}, ${b.carCount} ${b.carCount === 1 ? 'car' : 'cars'}`;
          return (
            <li key={b.key} className="group relative flex h-full min-w-0 flex-1 items-end justify-center">
              <span
                tabIndex={0}
                aria-label={summary}
                data-testid="trend-bar"
                className="block w-full max-w-10 rounded-t-[4px] bg-primary/85 outline-none transition-colors group-hover:bg-primary focus-visible:ring-2 focus-visible:ring-ring"
                style={{ height: `${height}%` }}
              />
              <span
                role="tooltip"
                className="pointer-events-none absolute bottom-full z-10 mb-1 hidden whitespace-nowrap rounded-md border bg-card px-2 py-1 text-xs text-card-foreground shadow-md group-focus-within:block group-hover:block"
              >
                <span className="font-medium">{b.label}</span>
                <br />
                {formatMoney(b.sales, currency)} · {b.carCount} {b.carCount === 1 ? 'car' : 'cars'}
              </span>
            </li>
          );
        })}
      </ol>
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{buckets[0].label}</span>
        {buckets.length > 1 && <span>{buckets[buckets.length - 1].label}</span>}
      </div>
    </figure>
  );
}
