import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { bookingServicesApi } from '@/api/endpoints';
import type { BookingHours, DayHours } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useLocations } from '@/features/locations/use-locations';
import { useAction } from '@/lib/use-action';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const SLOTS = [15, 20, 30, 45, 60, 90, 120];
const NOTICE: [number, string][] = [
  [0, 'None'],
  [60, '1 hour'],
  [120, '2 hours'],
  [240, '4 hours'],
  [1440, '1 day'],
  [2880, '2 days'],
];
const AHEAD = [7, 14, 30, 60, 90, 180];

const hoursKey = (locationId: string) => ['booking-hours', locationId];

/** "08:00:00" or "08:00" → "08:00". */
const hhmm = (t: string | null) => (t ? t.slice(0, 5) : '');

/**
 * Settings → Online booking: when each location takes online bookings — its weekly hours, how
 * long a slot is and how many cars fit in one, how much notice it needs and how far ahead
 * customers may book.
 */
export function BookingHoursSection() {
  const locations = useLocations();
  const active = (locations.data ?? []).filter((l) => l.status === 'ACTIVE');
  const [picked, setPicked] = useState<string | null>(null);
  const locationId = picked ?? active[0]?.id ?? null;

  return (
    <section aria-labelledby="booking-hours-heading" className="grid max-w-3xl gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 id="booking-hours-heading" className="text-lg font-semibold tracking-tight">
            Booking hours
          </h3>
          <p className="text-sm text-muted-foreground">
            When customers can book online. Full slots and times inside the notice aren&apos;t offered.
          </p>
        </div>
        {active.length > 1 && locationId && (
          <Select
            aria-label="Location"
            className="w-auto"
            value={locationId}
            onChange={(e) => setPicked(e.target.value)}
          >
            {active.map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}
              </option>
            ))}
          </Select>
        )}
      </div>
      {locationId && <HoursCard key={locationId} locationId={locationId} />}
    </section>
  );
}

function HoursCard({ locationId }: { locationId: string }) {
  const hours = useQuery({
    queryKey: hoursKey(locationId),
    queryFn: () => bookingServicesApi.hours(locationId),
  });
  return (
    <QueryState query={hours}>
      {(loaded) => <HoursForm key={loaded.version} locationId={locationId} hours={loaded} />}
    </QueryState>
  );
}

function HoursForm({ locationId, hours }: { locationId: string; hours: BookingHours }) {
  const [days, setDays] = useState<DayHours[]>(
    hours.days.map((d) => ({ opens: hhmm(d.opens) || null, closes: hhmm(d.closes) || null })),
  );
  const [slotMinutes, setSlotMinutes] = useState(String(hours.slotMinutes));
  const [cars, setCars] = useState(String(hours.carsPerSlot));
  const [notice, setNotice] = useState(String(hours.minNoticeMinutes));
  const [ahead, setAhead] = useState(String(hours.maxDaysAhead));
  const carsOk = /^\d{1,2}$/.test(cars) && Number(cars) >= 1 && Number(cars) <= 50;
  const badDay = days.findIndex((d) => d.opens !== null && (d.closes === null || d.closes <= d.opens));
  const valid = carsOk && badDay === -1;

  const save = useAction(
    () =>
      bookingServicesApi.setHours(locationId, {
        days,
        slotMinutes: Number(slotMinutes),
        carsPerSlot: Number(cars),
        minNoticeMinutes: Number(notice),
        maxDaysAhead: Number(ahead),
        version: hours.version,
      }),
    { success: 'Booking hours saved', invalidate: [hoursKey(locationId)] },
  );
  const setDay = (i: number, day: DayHours) => setDays(days.map((d, j) => (j === i ? day : d)));

  return (
    <Card>
      <CardContent className="pt-6">
        <form
          noValidate
          aria-label="Booking hours"
          className="grid gap-5"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              save.mutate(undefined);
            }
          }}
        >
          <ul className="grid gap-2" aria-label="Weekly hours">
            {days.map((d, i) => {
              const open = d.opens !== null;
              return (
                <li key={DAYS[i]} className="flex flex-wrap items-center gap-3 text-sm">
                  <label className="flex w-36 items-center gap-2">
                    <input
                      type="checkbox"
                      checked={open}
                      aria-label={`Open on ${DAYS[i]}`}
                      onChange={(e) =>
                        setDay(
                          i,
                          e.target.checked
                            ? { opens: '08:00', closes: '17:00' }
                            : { opens: null, closes: null },
                        )
                      }
                    />
                    <span className="font-medium">{DAYS[i]}</span>
                  </label>
                  {open ? (
                    <>
                      <Input
                        type="time"
                        className="w-32"
                        aria-label={`${DAYS[i]} opens`}
                        value={d.opens ?? ''}
                        onChange={(e) => setDay(i, { ...d, opens: e.target.value || null })}
                      />
                      <span className="text-muted-foreground">to</span>
                      <Input
                        type="time"
                        className="w-32"
                        aria-label={`${DAYS[i]} closes`}
                        value={d.closes ?? ''}
                        onChange={(e) => setDay(i, { ...d, closes: e.target.value || null })}
                      />
                      {badDay === i && (
                        <span role="alert" className="text-destructive">
                          Closing must be after opening
                        </span>
                      )}
                    </>
                  ) : (
                    <span className="text-muted-foreground">Closed</span>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="grid gap-4 sm:grid-cols-4">
            <Field label="Slot length">
              <Select value={slotMinutes} onChange={(e) => setSlotMinutes(e.target.value)}>
                {SLOTS.map((m) => (
                  <option key={m} value={m}>
                    {m < 60 ? `${m} min` : `${m / 60} h`}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Cars per slot" error={carsOk ? undefined : '1 to 50'}>
              <Input inputMode="numeric" value={cars} onChange={(e) => setCars(e.target.value)} />
            </Field>
            <Field label="Notice needed">
              <Select value={notice} onChange={(e) => setNotice(e.target.value)}>
                {NOTICE.map(([m, label]) => (
                  <option key={m} value={m}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Book up to">
              <Select value={ahead} onChange={(e) => setAhead(e.target.value)}>
                {AHEAD.map((d) => (
                  <option key={d} value={d}>
                    {d} days ahead
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" className="justify-self-start" disabled={!valid || save.isPending}>
            {save.isPending ? 'Saving…' : 'Save booking hours'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
