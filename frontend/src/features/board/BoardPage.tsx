import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { appointmentsApi } from '@/api/endpoints';
import type { Appointment, AppointmentStatus } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { AppointmentStatusBadge } from '@/components/common/StatusBadge';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { useMyLocations, usePreferredLocation } from '@/features/locations/use-locations';
import { APPOINTMENT_STATUS_LABELS, formatTime, SERVICE_TYPE_LABELS, todayIso } from '@/lib/format';

import { AppointmentActions } from '../appointments/AppointmentActions';

const COLUMNS: AppointmentStatus[] = ['BOOKED', 'CONFIRMED', 'ARRIVED', 'CHECKED_IN'];

/**
 * The service advisor's day at one location: appointments in columns by status, with the next
 * action on each. A day belongs to one time zone, so with several locations the board shows the
 * switcher's location, or the one picked here when the switcher says "All locations".
 */
export function BoardPage() {
  const mine = useMyLocations();
  const preferred = usePreferredLocation();
  const [picked, setPicked] = useState('');
  const location = mine.find((l) => l.id === picked) ?? preferred;
  const zone = location?.timeZone;
  const several = mine.length > 1;
  const [chosenDate, setDate] = useState<string | null>(null);
  const date = chosenDate ?? todayIso(new Date(), zone);
  const filters = { date, size: 100, ...(several && location ? { locationId: location.id } : {}) };
  const appointments = useQuery({
    queryKey: ['appointments', filters],
    queryFn: () => appointmentsApi.list(filters),
    refetchInterval: 30_000,
  });

  return (
    <>
      <PageHeader
        title="Today's board"
        description="Confirm bookings, greet arrivals and check vehicles in."
        actions={
          <>
            {several && (
              <div className="grid gap-1.5">
                <Label htmlFor="board-location">Location</Label>
                <Select
                  id="board-location"
                  value={location?.id ?? ''}
                  onChange={(e) => setPicked(e.target.value)}
                >
                  {mine.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </Select>
              </div>
            )}
            <div className="flex items-end gap-2">
              <div className="grid gap-1.5">
                <Label htmlFor="board-date">Day</Label>
                <Input
                  id="board-date"
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value || null)}
                />
              </div>
              {date !== todayIso(new Date(), zone) && (
                <Button variant="ghost" onClick={() => setDate(null)}>
                  Today
                </Button>
              )}
            </div>
            <Button asChild className="self-end">
              <Link to="/appointments/new">Book a service</Link>
            </Button>
          </>
        }
      />
      <QueryState query={appointments}>
        {(page) => {
          const closed = page.content.filter((a) => a.status === 'CANCELLED' || a.status === 'NO_SHOW');
          return (
            <div className="grid gap-6">
              <div className="grid gap-4 lg:grid-cols-4">
                {COLUMNS.map((status) => {
                  const items = page.content.filter((a) => a.status === status);
                  return (
                    <section
                      key={status}
                      aria-label={APPOINTMENT_STATUS_LABELS[status]}
                      className="grid content-start gap-3 rounded-xl bg-muted/50 p-3"
                    >
                      <h2 className="flex items-center justify-between px-1 text-sm font-semibold">
                        {APPOINTMENT_STATUS_LABELS[status]}
                        <span className="rounded-full bg-background px-2 text-xs text-muted-foreground">
                          {items.length}
                        </span>
                      </h2>
                      {items.length === 0 ? (
                        <p className="px-1 text-xs text-muted-foreground">None</p>
                      ) : (
                        items.map((a) => <BoardCard key={a.id} appointment={a} zone={zone} />)
                      )}
                    </section>
                  );
                })}
              </div>
              {closed.length > 0 && (
                <section aria-label="Cancelled and no-shows" className="grid gap-2">
                  <h2 className="text-sm font-semibold text-muted-foreground">Cancelled and no-shows</h2>
                  <ul className="grid gap-1 text-sm">
                    {closed.map((a) => (
                      <li key={a.id} className="flex flex-wrap items-center gap-2">
                        {formatTime(a.scheduledAt, zone)} · {a.vehicle.registration} · {a.customerName}
                        <AppointmentStatusBadge status={a.status} />
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>
          );
        }}
      </QueryState>
    </>
  );
}

function BoardCard({ appointment: a, zone }: { appointment: Appointment; zone?: string }) {
  return (
    <Card>
      <CardContent className="grid gap-2 p-3 text-sm">
        <div className="flex items-baseline justify-between gap-2">
          <span className="font-semibold">{formatTime(a.scheduledAt, zone)}</span>
          <span className="text-xs text-muted-foreground">{a.durationMinutes} min</span>
        </div>
        <div>
          <span className="font-medium">{a.vehicle.registration ?? 'No plate yet'}</span>{' '}
          <span className="text-muted-foreground">
            {a.vehicle.make ? `${a.vehicle.make} ${a.vehicle.model}` : `VIN ${a.vehicle.vin}`}
          </span>
          {a.bookedOnline && (
            <Badge variant="outline" className="ml-2">
              Online
            </Badge>
          )}
        </div>
        <div className="text-muted-foreground">
          <Link to={`/customers/${a.customerId}`} className="hover:underline">
            {a.customerName}
          </Link>{' '}
          · {a.bookedOnline && a.serviceName ? a.serviceName : SERVICE_TYPE_LABELS[a.serviceType]}
        </div>
        {a.notes && <p className="rounded bg-muted px-2 py-1 text-xs">{a.notes}</p>}
        <AppointmentActions appointment={a} />
      </CardContent>
    </Card>
  );
}
