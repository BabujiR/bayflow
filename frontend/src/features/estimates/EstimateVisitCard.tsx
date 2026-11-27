import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { appointmentsApi, jobCardsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { JobCard } from '@/api/types';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogClose, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { DURATIONS } from '@/features/appointments/AppointmentDialogs';
import { formatDateTime, localInputToIso, localInputToMillis } from '@/lib/format';
import { useAction } from '@/lib/use-action';

/** "today", "1 day", "12 days": how long an estimate has been waiting. */
export function estimateAge(createdAt: string, now: number = Date.now()): string {
  const days = Math.floor((now - Date.parse(createdAt)) / 86_400_000);
  return days <= 0 ? 'today' : `${days} day${days === 1 ? '' : 's'}`;
}

function startedAgo(createdAt: string): string {
  const age = estimateAge(createdAt);
  return age === 'today' ? 'Started today.' : `Started ${age} ago.`;
}

/**
 * A quick estimate before its visit (`arrivedAt` empty): when the customer goes ahead, book the
 * visit; when they won't, close it. At check-in this same card goes on with the work.
 */
export function EstimateVisitCard({ card, zone }: { card: JobCard; zone?: string }) {
  const appointment = useQuery({
    queryKey: ['appointment', card.appointmentId],
    queryFn: () => appointmentsApi.get(card.appointmentId),
  });
  const queryClient = useQueryClient();
  const close = useAction(
    async () => {
      const closed = await jobCardsApi.closeEstimate(card.id);
      // Its visit too (already cancelled is fine).
      await appointmentsApi.cancel(card.appointmentId).catch((e: unknown) => {
        if (!(e instanceof ApiError && e.isClientError)) {
          throw e;
        }
      });
      return closed;
    },
    {
      success: 'Estimate closed',
      invalidate: [['job-cards'], ['appointments'], ['appointment']],
      onSuccess: (closed) => queryClient.setQueryData(['job-card', card.id], closed),
    },
  );

  const status = appointment.data?.status;
  const closed = card.status === 'DECLINED';
  return (
    <section
      aria-label="Estimate"
      className="grid gap-3 rounded-lg border border-dashed bg-muted/40 p-4 text-sm"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="grid gap-1">
          <p className="font-medium">
            {closed
              ? 'Estimate closed'
              : status === 'BOOKED' || status === 'CONFIRMED'
                ? `Estimate · visit booked for ${formatDateTime(appointment.data?.scheduledAt, zone)}`
                : 'Estimate · not scheduled yet'}
          </p>
          <p className="text-muted-foreground">
            {closed
              ? 'The customer didn’t go ahead.'
              : `${startedAgo(card.createdAt)} Work can start once the car is checked in; this card carries on then.`}
          </p>
        </div>
        {!closed && (
          <div className="flex flex-wrap gap-2">
            {status === 'ESTIMATE' && (
              <ScheduleEstimateDialog appointmentId={card.appointmentId} zone={zone} />
            )}
            <Button
              size="sm"
              variant="outline"
              disabled={close.isPending}
              onClick={() => close.mutate(undefined)}
            >
              Close estimate
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}

function ScheduleEstimateDialog({ appointmentId, zone }: { appointmentId: string; zone?: string }) {
  const [open, setOpen] = useState(false);
  const [scheduledAt, setScheduledAt] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [error, setError] = useState<string | null>(null);
  const inFuture = useMemo(
    () => scheduledAt !== '' && localInputToMillis(scheduledAt, zone) > Date.now(),
    [scheduledAt, zone],
  );
  const schedule = useAction(
    () =>
      appointmentsApi.schedule(appointmentId, {
        scheduledAt: localInputToIso(scheduledAt, zone),
        durationMinutes,
      }),
    {
      success: (a) => `Visit booked for ${formatDateTime(a.scheduledAt, zone)}`,
      invalidate: [['appointments'], ['appointment'], ['job-cards']],
      onSuccess: () => setOpen(false),
      onError: (e) => {
        setError(errorMessage(e));
        return true;
      },
    },
  );

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        setError(null);
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">Schedule the work</Button>
      </DialogTrigger>
      <DialogContent
        title="Schedule the work"
        description="Book the visit; the customer gets the usual confirmation."
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!inFuture) {
              setError('Choose a time in the future.');
              return;
            }
            setError(null);
            schedule.mutateAsync(undefined).catch(() => undefined);
          }}
        >
          {error && <Alert>{error}</Alert>}
          <Field label="Date and time">
            <Input
              type="datetime-local"
              value={scheduledAt}
              onChange={(e) => setScheduledAt(e.target.value)}
            />
          </Field>
          <Field label="Length">
            <Select value={durationMinutes} onChange={(e) => setDurationMinutes(Number(e.target.value))}>
              {DURATIONS.map((minutes) => (
                <option key={minutes} value={minutes}>
                  {minutes < 60 ? `${minutes} minutes` : `${minutes / 60} hour${minutes === 60 ? '' : 's'}`}
                </option>
              ))}
            </Select>
          </Field>
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="ghost">
                Not yet
              </Button>
            </DialogClose>
            <Button type="submit" disabled={schedule.isPending}>
              Book the visit
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
