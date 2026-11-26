import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { useMemo, useState, type ReactNode } from 'react';

import { bookingApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { BrandBanner } from '@/features/branding/BrandBanner';
import { PoweredBy } from '@/features/branding/PoweredBy';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { todayIso } from '@/lib/format';
import { cn } from '@/lib/utils';
import { WorkshopNotFoundPage } from '@/pages/WorkshopNotFoundPage';

/** The public booking pages' frame: the shop's logo and name, the page, "powered by". */
export function BookingShell({ shopName, children }: { shopName?: string; children: ReactNode }) {
  return (
    <div className="min-h-svh bg-gradient-to-b from-primary/5 to-background">
      <div className="flex min-h-svh flex-col">
        <BrandBanner />
        <header className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3 px-4 py-4">
          <div className="flex min-w-0 items-center gap-3">
            <WorkshopLogo />
            {shopName && (
              <span className="truncate font-semibold" data-testid="booking-shop-name">
                {shopName}
              </span>
            )}
          </div>
          <ThemeToggle />
        </header>
        <main className="mx-auto w-full max-w-2xl flex-1 px-4 pb-16">{children}</main>
        <PoweredBy />
      </div>
    </div>
  );
}

/** Loading, no workshop at this address, suspended or other errors; else the page. */
export function PublicQueryState<T>({
  query,
  children,
  errorText,
}: {
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
  /** Words for a problem code (e.g. an invalid link); otherwise the server's message. */
  errorText?: (error: ApiError) => ReactNode | undefined;
}) {
  if (query.isPending) {
    return (
      <div className="grid gap-3" aria-label="Loading">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (query.isError) {
    const error = query.error;
    if (error instanceof ApiError && error.code === 'workshop-not-found') {
      return <WorkshopNotFoundPage />;
    }
    return (
      <Alert>
        {(error instanceof ApiError && errorText?.(error)) ||
          (error instanceof ApiError && error.code === 'workshop-suspended'
            ? "This workshop isn't taking online bookings right now. Please call the shop."
            : errorMessage(error))}
      </Alert>
    );
  }
  return <>{children(query.data)}</>;
}

/** "2026-11-16" plus `days`, as a date (no time zone involved). */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export const dayLabel = (date: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat(undefined, { ...options, timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));

export const timeLabel = (iso: string, zone: string) =>
  new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', timeZone: zone }).format(
    new Date(iso),
  );

export const whenLabel = (iso: string, zone: string) =>
  new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: zone,
  }).format(new Date(iso));

export function TimePicker({
  location,
  zone,
  slot,
  onPick,
  error,
}: {
  location: string;
  zone: string;
  slot: string | null;
  onPick: (slot: string) => void;
  error: string | null;
}) {
  const today = useMemo(() => todayIso(new Date(), zone), [zone]);
  const [from, setFrom] = useState(today);
  const availability = useQuery({
    queryKey: ['booking', 'availability', location, from],
    queryFn: () => bookingApi.availability(location, from, 7),
  });
  const days = availability.data?.days ?? [];
  const [day, setDay] = useState<string | null>(null);
  const firstOpen = days.find((d) => d.slots.length > 0)?.date ?? null;
  const shown = days.find((d) => d.date === day) ?? days.find((d) => d.date === firstOpen);

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-medium">Pick a time</h2>
        <div className="flex gap-1">
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="Earlier days"
            disabled={from <= today}
            onClick={() => {
              setFrom(addDays(from, -7) < today ? today : addDays(from, -7));
              setDay(null);
            }}
          >
            ←
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="Later days"
            onClick={() => {
              setFrom(addDays(from, 7));
              setDay(null);
            }}
          >
            →
          </Button>
        </div>
      </div>
      {availability.isPending ? (
        <Skeleton className="h-32 w-full" />
      ) : availability.isError ? (
        <Alert>{errorMessage(availability.error)}</Alert>
      ) : (
        <>
          <div className="grid grid-cols-7 gap-1" role="group" aria-label="Days">
            {days.map((d) => {
              const open = d.slots.length > 0;
              const selected = shown?.date === d.date;
              return (
                <button
                  key={d.date}
                  type="button"
                  disabled={!open}
                  aria-pressed={selected}
                  aria-label={dayLabel(d.date, { weekday: 'long', day: 'numeric', month: 'long' })}
                  onClick={() => setDay(d.date)}
                  className={cn(
                    'grid justify-items-center gap-0.5 rounded-lg border px-1 py-2 text-xs transition-colors',
                    selected
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'hover:border-primary/60',
                    !open && 'cursor-not-allowed opacity-40',
                  )}
                >
                  <span>{dayLabel(d.date, { weekday: 'short' })}</span>
                  <span className="text-base font-semibold">{dayLabel(d.date, { day: 'numeric' })}</span>
                </button>
              );
            })}
          </div>
          {shown && shown.slots.length > 0 ? (
            <div
              key={shown.date}
              className="gb-step grid grid-cols-3 gap-2 sm:grid-cols-4"
              role="group"
              aria-label="Times"
            >
              {shown.slots.map((s) => (
                <button
                  key={s}
                  type="button"
                  aria-pressed={slot === s}
                  onClick={() => onPick(s)}
                  className={cn(
                    'rounded-lg border px-2 py-2 text-sm font-medium transition-all hover:border-primary/60',
                    slot === s && 'scale-[1.03] border-primary bg-primary text-primary-foreground',
                  )}
                >
                  {timeLabel(s, zone)}
                </button>
              ))}
            </div>
          ) : (
            <p className="rounded-lg bg-muted px-3 py-4 text-center text-sm text-muted-foreground">
              No free times these days. Try later days with →.
            </p>
          )}
          <p className="text-xs text-muted-foreground">Times are the shop&apos;s local time.</p>
        </>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

export function Summary({ rows }: { rows: [string, string][] }) {
  return (
    <dl className="grid gap-1 rounded-lg bg-muted/50 p-3 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="flex justify-between gap-3">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="text-right font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
