import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
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
import { useLocationLookup } from '@/features/locations/use-locations';
import { useVinDecode, VinDecodeNote } from '@/features/vehicles/vin-decode';
import { formatDateTime, isoToLocalInput, localInputToIso, localInputToMillis } from '@/lib/format';
import { useAction } from '@/lib/use-action';

const APPOINTMENT_KEYS = [['appointments'], ['appointment']];

export const DURATIONS = [30, 60, 90, 120, 180, 240];

const VIN = /^[A-HJ-NPR-Z0-9]{17}$/i;

/** VIN and plate are asked for only when the car (booked online) doesn't have them yet. */
const checkInSchemaFor = (needsVin: boolean, needsPlate: boolean) =>
  z.object({
    mileage: z.coerce.number<string>().int('Whole miles only').min(0, 'Cannot be negative'),
    vin: needsVin
      ? z.string().trim().regex(VIN, 'A VIN has 17 letters and digits (no I, O or Q)')
      : z.string().optional(),
    registration: needsPlate ? z.string().trim().min(1, 'Enter the plate').max(20) : z.string().optional(),
  });

/** "8XYZ214 · Honda Civic"; a car booked online may not have a plate yet. */
export function vehicleLabel(v: Appointment['vehicle']): string {
  const name = [v.year || null, v.make, v.model].filter(Boolean).join(' ');
  return [v.registration, name || (v.vin ? `VIN ${v.vin}` : null)].filter(Boolean).join(' · ');
}

/**
 * Check-in records the odometer reading (and, for a car booked online, its VIN and plate); the
 * job card is then opened automatically.
 */
export function CheckInDialog({ appointment }: { appointment: Appointment }) {
  const needsVin = !appointment.vehicle.vin;
  const needsPlate = !appointment.vehicle.registration;
  const checkInSchema = useMemo(() => checkInSchemaFor(needsVin, needsPlate), [needsVin, needsPlate]);
  const [open, setOpen] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof checkInSchema>, unknown, z.output<typeof checkInSchema>>({
    resolver: zodResolver(checkInSchema),
    defaultValues: { mileage: '', vin: '', registration: '' },
  });
  // A VIN typed at the desk is looked up, so a typo shows before the job card opens.
  const vin = useWatch({ control, name: 'vin' }) ?? '';
  const decoded = useVinDecode(vin, open && needsVin);
  const checkIn = useAction(
    (v: z.output<typeof checkInSchema>) =>
      appointmentsApi.checkIn(
        appointment.id,
        v.mileage,
        needsVin || needsPlate
          ? {
              vin: needsVin ? v.vin?.trim().toUpperCase() : undefined,
              registration: needsPlate ? v.registration?.trim().toUpperCase() : undefined,
            }
          : undefined,
      ),
    {
      success: (a) =>
        a.jobCardId ? 'Checked in. Job card opened.' : 'Checked in. The job card will open shortly.',
      invalidate: APPOINTMENT_KEYS,
      onSuccess: () => {
        setOpen(false);
        reset();
      },
      onError: (error) => {
        setServerError(errorMessage(error));
        return true;
      },
    },
  );

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
      <DialogContent title="Check in vehicle" description={vehicleLabel(appointment.vehicle)}>
        <form
          noValidate
          className="grid gap-4"
          onSubmit={handleSubmit(async (values) => {
            setServerError(null);
            await checkIn.mutateAsync(values).catch(() => undefined);
          })}
        >
          {serverError && <Alert>{serverError}</Alert>}
          <Field label="Odometer reading (miles)" error={errors.mileage?.message}>
            <Input inputMode="numeric" autoFocus {...register('mileage')} />
          </Field>
          {(needsVin || needsPlate) && (
            <p className="text-sm text-muted-foreground">
              Booked online without{' '}
              {needsVin && needsPlate ? 'a VIN or plate' : needsVin ? 'a VIN' : 'a plate'}: the job card needs{' '}
              {needsVin && needsPlate ? 'both' : 'it'}.
            </p>
          )}
          {needsVin && (
            <Field label="VIN" error={errors.vin?.message}>
              <Input maxLength={17} className="font-mono uppercase" {...register('vin')} />
            </Field>
          )}
          {needsVin && <VinDecodeNote query={decoded} />}
          {needsPlate && (
            <Field label="Plate" error={errors.registration?.message}>
              <Input maxLength={20} className="uppercase" {...register('registration')} />
            </Field>
          )}
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

/** The new time is read in the appointment's location's time zone (`zone`). */
const rescheduleSchemaFor = (zone?: string) =>
  z.object({
    scheduledAt: z
      .string()
      .min(1, 'Choose a date and time')
      .refine((v) => localInputToMillis(v, zone) > Date.now(), 'Choose a time in the future'),
    durationMinutes: z.coerce.number<string>(),
  });
type RescheduleSchema = ReturnType<typeof rescheduleSchemaFor>;

export function RescheduleDialog({ appointment }: { appointment: Appointment }) {
  const [open, setOpen] = useState(false);
  const zone = useLocationLookup().zoneOf(appointment.locationId);
  const rescheduleSchema = useMemo(() => rescheduleSchemaFor(zone), [zone]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.input<RescheduleSchema>, unknown, z.output<RescheduleSchema>>({
    resolver: zodResolver(rescheduleSchema),
    defaultValues: {
      scheduledAt: isoToLocalInput(appointment.scheduledAt, zone),
      durationMinutes: String(appointment.durationMinutes),
    },
  });
  const reschedule = useAction(
    (values: { scheduledAt: string; durationMinutes: number }) =>
      appointmentsApi.reschedule(appointment.id, values),
    {
      success: (a) => `Moved to ${formatDateTime(a.scheduledAt, zone)}`,
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
      <DialogContent
        title="Reschedule appointment"
        description={`Currently ${formatDateTime(appointment.scheduledAt, zone)}`}
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={handleSubmit(async ({ scheduledAt, durationMinutes }) => {
            await reschedule
              .mutateAsync({ scheduledAt: localInputToIso(scheduledAt, zone), durationMinutes })
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
  const zone = useLocationLookup().zoneOf(appointment.locationId);
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
        description={`${formatDateTime(appointment.scheduledAt, zone)} · ${vehicleLabel(appointment.vehicle)}. This can't be undone.`}
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
