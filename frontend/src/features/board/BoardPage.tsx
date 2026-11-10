import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { appointmentsApi } from '@/api/endpoints';
import type { Appointment, AppointmentStatus } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { AppointmentStatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { APPOINTMENT_STATUS_LABELS, formatTime, SERVICE_TYPE_LABELS, todayIso } from '@/lib/format';

import { AppointmentActions } from '../appointments/AppointmentActions';

const COLUMNS: AppointmentStatus[] = ['BOOKED', 'CONFIRMED', 'ARRIVED', 'CHECKED_IN'];

/** The service advisor's day: appointments in columns by status, with the next action on each. */
export function BoardPage() {
  const [date, setDate] = useState(todayIso);
  const filters = { date, size: 100 };
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
            <div className="flex items-end gap-2">
              <div className="grid gap-1.5">
                <Label htmlFor="board-date">Day</Label>
                <Input id="board-date" type="date" value={date} onChange={(e) => setDate(e.target.value || todayIso())} />
              </div>
              {date !== todayIso() && (
                <Button variant="ghost" onClick={() => setDate(todayIso())}>
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
                        <span className="rounded-full bg-background px-2 text-xs text-muted-foreground">{items.length}</span>
                      </h2>
                      {items.length === 0 ? (
                        <p className="px-1 text-xs text-muted-foreground">None</p>
                      ) : (
                        items.map((a) => <BoardCard key={a.id} appointment={a} />)
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
                        {formatTime(a.scheduledAt)} · {a.vehicle.registration} · {a.customerName}
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

function BoardCard({ appointment: a }: { appointment: Appointment }) {
  return (
    <Card>
      <CardContent className="grid gap-2 p-3 text-sm">
        <div className="flex items-baseline justify-between gap-2">
          <span className="font-semibold">{formatTime(a.scheduledAt)}</span>
          <span className="text-xs text-muted-foreground">{a.durationMinutes} min</span>
        </div>
        <div>
          <span className="font-medium">{a.vehicle.registration}</span>{' '}
          <span className="text-muted-foreground">
            {a.vehicle.make} {a.vehicle.model}
          </span>
        </div>
        <div className="text-muted-foreground">
          <Link to={`/customers/${a.customerId}`} className="hover:underline">
            {a.customerName}
          </Link>{' '}
          · {SERVICE_TYPE_LABELS[a.serviceType]}
        </div>
        {a.notes && <p className="rounded bg-muted px-2 py-1 text-xs">{a.notes}</p>}
        <AppointmentActions appointment={a} />
      </CardContent>
    </Card>
  );
}
