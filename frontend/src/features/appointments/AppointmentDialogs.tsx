import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { appointmentsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { Appointment } from '@/api/types';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogClose, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { formatDateTime, isoToLocalInput, localInputToIso } from '@/lib/format';
import { useAction } from '@/lib/use-action';

const APPOINTMENT_KEYS = [['appointments'], ['appointment']];

export const DURATIONS = [30, 60, 90, 120, 180, 240];

const checkInSchema = z.object({
  mileage: z.coerce.number<string>().int('Whole miles only').min(0, 'Cannot be negative'),
});

/** Check-in records the odometer reading; the job card is then opened automatically. */
export function CheckInDialog({ appointment }: { appointment: Appointment }) {
  const [open, setOpen] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof checkInSchema>, unknown, z.output<typeof checkInSchema>>({
    resolver: zodResolver(checkInSchema),
    defaultValues: { mileage: '' },
  });
  const checkIn = useAction((mileage: number) => appointmentsApi.checkIn(appointment.id, mileage), {
    success: (a) => (a.jobCardId ? 'Checked in. Job card opened.' : 'Checked in. The job card will open shortly.'),
    invalidate: APPOINTMENT_KEYS,
    onSuccess: () => {
      setOpen(false);
      reset();
    },
    onError: (error) => {
      setServerError(errorMessage(error));
      return true;
    },
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        setServerError(null);
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">Check in</Button>
      </DialogTrigger>
      <DialogContent
        title="Check in vehicle"
        description={`${appointment.vehicle.registration} · ${appointment.vehicle.make} ${appointment.vehicle.model}`}
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={handleSubmit(async ({ mileage }) => {
            setServerError(null);
            await checkIn.mutateAsync(mileage).catch(() => undefined);
          })}
        >
          {serverError && <Alert>{serverError}</Alert>}
          <Field label="Odometer reading (miles)" error={errors.mileage?.message}>
            <Input inputMode="numeric" autoFocus {...register('mileage')} />
          </Field>
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="ghost">
                Cancel
              </Button>
            </DialogClose>
            <Button type="submit" disabled={isSubmitting}>
              Check in
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const rescheduleSchema = z.object({
  scheduledAt: z
    .string()
    .min(1, 'Choose a date and time')
    .refine((v) => new Date(v).getTime() > Date.now(), 'Choose a time in the future'),
  durationMinutes: z.coerce.number<string>(),
});

export function RescheduleDialog({ appointment }: { appointment: Appointment }) {
  const [open, setOpen] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof rescheduleSchema>, unknown, z.output<typeof rescheduleSchema>>({
    resolver: zodResolver(rescheduleSchema),
    defaultValues: {
      scheduledAt: isoToLocalInput(appointment.scheduledAt),
      durationMinutes: String(appointment.durationMinutes),
    },
  });
  const reschedule = useAction(
    (values: { scheduledAt: string; durationMinutes: number }) =>
      appointmentsApi.reschedule(appointment.id, values),
    {
      success: (a) => `Moved to ${formatDateTime(a.scheduledAt)}`,
      invalidate: APPOINTMENT_KEYS,
      onSuccess: () => setOpen(false),
      onError: (error) => {
        if (error instanceof ApiError && error.isClientError) {
          setError('scheduledAt', { message: error.message });
          return true;
        }
        return false;
      },
    },
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          Reschedule
        </Button>
      </DialogTrigger>
      <DialogContent title="Reschedule appointment" description={`Currently ${formatDateTime(appointment.scheduledAt)}`}>
        <form
          noValidate
          className="grid gap-4"
          onSubmit={handleSubmit(async ({ scheduledAt, durationMinutes }) => {
            await reschedule
              .mutateAsync({ scheduledAt: localInputToIso(scheduledAt), durationMinutes })
              .catch(() => undefined);
          })}
        >
          <Field label="New date and time" error={errors.scheduledAt?.message}>
            <Input type="datetime-local" {...register('scheduledAt')} />
          </Field>
          <Field label="Length">
            <Select {...register('durationMinutes')}>
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
                Keep current time
              </Button>
            </DialogClose>
            <Button type="submit" disabled={isSubmitting}>
              Reschedule
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Asks before cancelling, since a cancelled booking cannot be reopened. */
export function CancelDialog({ appointment }: { appointment: Appointment }) {
  const [open, setOpen] = useState(false);
  const cancel = useAction(() => appointmentsApi.cancel(appointment.id), {
    success: 'Appointment cancelled',
    invalidate: APPOINTMENT_KEYS,
    onSuccess: () => setOpen(false),
  });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost" className="text-destructive">
          Cancel
        </Button>
      </DialogTrigger>
      <DialogContent
        title="Cancel this appointment?"
        description={`${formatDateTime(appointment.scheduledAt)} · ${appointment.vehicle.registration}. This can't be undone.`}
      >
        <div className="flex justify-end gap-2">
          <DialogClose asChild>
            <Button variant="ghost">Keep it</Button>
          </DialogClose>
          <Button variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate(undefined)}>
            Cancel appointment
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
