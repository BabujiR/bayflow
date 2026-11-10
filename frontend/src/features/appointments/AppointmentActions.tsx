import { Link } from 'react-router';

import { appointmentsApi } from '@/api/endpoints';
import type { Appointment } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import { useAction } from '@/lib/use-action';

import { CancelDialog, CheckInDialog, RescheduleDialog } from './AppointmentDialogs';

const KEYS = [['appointments'], ['appointment']];

/** The next steps for an appointment, depending on its status and the user's role. */
export function AppointmentActions({ appointment }: { appointment: Appointment }) {
  const { hasRole } = useAuth();
  const advisor = hasRole('SERVICE_ADVISOR');
  const customer = hasRole('CUSTOMER');
  const { status } = appointment;
  const open = status === 'BOOKED' || status === 'CONFIRMED';
  const pastStart = new Date(appointment.scheduledAt).getTime() < Date.now();

  const confirm = useAction(() => appointmentsApi.confirm(appointment.id), { success: 'Confirmed', invalidate: KEYS });
  const arrive = useAction(() => appointmentsApi.arrive(appointment.id), {
    success: 'Marked as arrived',
    invalidate: KEYS,
  });
  const noShow = useAction(() => appointmentsApi.noShow(appointment.id), {
    success: 'Marked as no-show',
    invalidate: KEYS,
  });

  return (
    <div className="flex flex-wrap items-center gap-2">
      {advisor && status === 'BOOKED' && (
        <Button size="sm" disabled={confirm.isPending} onClick={() => confirm.mutate(undefined)}>
          Confirm
        </Button>
      )}
      {advisor && status === 'CONFIRMED' && (
        <Button size="sm" disabled={arrive.isPending} onClick={() => arrive.mutate(undefined)}>
          Mark arrived
        </Button>
      )}
      {advisor && status === 'ARRIVED' && <CheckInDialog appointment={appointment} />}
      {(advisor || customer) && open && <RescheduleDialog appointment={appointment} />}
      {advisor && open && pastStart && (
        <Button size="sm" variant="outline" disabled={noShow.isPending} onClick={() => noShow.mutate(undefined)}>
          No-show
        </Button>
      )}
      {(advisor || customer) && open && <CancelDialog appointment={appointment} />}
      {status === 'CHECKED_IN' &&
        (appointment.jobCardId ? (
          <Button asChild size="sm" variant="outline">
            <Link to={`/job-cards/${appointment.jobCardId}`}>Job card</Link>
          </Button>
        ) : (
          <span className="text-xs text-muted-foreground">Job card opening…</span>
        ))}
    </div>
  );
}
