import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { z } from 'zod';

import { appointmentsApi, customersApi, vehiclesApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { ServiceType } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { PageHeader } from '@/components/common/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useMyLocations, usePreferredLocation } from '@/features/locations/use-locations';
import {
  formatDateTime,
  localInputToIso,
  localInputToMillis,
  SERVICE_TYPE_LABELS,
  zoneLabel,
} from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { DURATIONS } from './AppointmentDialogs';

const SERVICE_TYPES = Object.keys(SERVICE_TYPE_LABELS) as [ServiceType, ...ServiceType[]];

/** The date and time are read in the chosen location's time zone (`zone`). */
const schemaFor = (zone?: string) =>
  z.object({
    locationId: z.string(),
    vehicleId: z.string().min(1, 'Choose a vehicle'),
    scheduledAt: z
      .string()
      .min(1, 'Choose a date and time')
      .refine((v) => localInputToMillis(v, zone) > Date.now(), 'Choose a time in the future'),
    durationMinutes: z.coerce.number<string>(),
    serviceType: z.enum(SERVICE_TYPES, 'Choose a service'),
    notes: z.string().max(1000, 'At most 1000 characters'),
  });

type Input_ = z.input<ReturnType<typeof schemaFor>>;
type Output = z.output<ReturnType<typeof schemaFor>>;

/**
 * Book a service. Customers pick one of their vehicles; advisors first find the customer
 * (or arrive here from a customer or vehicle page with it already chosen).
 */
export function BookAppointmentPage() {
  const { hasRole } = useAuth();
  const advisor = hasRole('SERVICE_ADVISOR');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [customerId, setCustomerId] = useState(params.get('customerId') ?? '');
  const [search, setSearch] = useState('');

  const customers = useQuery({
    queryKey: ['customers', { q: search }],
    queryFn: () => customersApi.list({ q: search, size: 20 }),
    enabled: advisor && !customerId,
  });
  const vehicles = useQuery({
    queryKey: ['vehicles', advisor ? { customerId } : 'mine'],
    queryFn: () => vehiclesApi.list(advisor ? { customerId, size: 100 } : { size: 100 }),
    enabled: !advisor || !!customerId,
  });

  // Organizations with several locations: where the work is done, and so which time zone the
  // date and time are in. Preselected: the switcher's location, else the first by name.
  const mine = useMyLocations();
  const preferred = usePreferredLocation();
  const [locationId, setLocationId] = useState('');
  const chosen = mine.find((l) => l.id === locationId) ?? preferred;
  const zone = chosen?.timeZone;
  const schema = useMemo(() => schemaFor(zone), [zone]);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Input_, unknown, Output>({
    resolver: zodResolver(schema),
    defaultValues: {
      locationId: '',
      vehicleId: params.get('vehicleId') ?? '',
      scheduledAt: '',
      durationMinutes: '60',
      serviceType: 'ROUTINE_SERVICE',
      notes: '',
    },
  });

  const book = useAction(appointmentsApi.book, {
    success: (a) => `Booked for ${formatDateTime(a.scheduledAt, zone)}`,
    invalidate: [['appointments']],
    onSuccess: () => navigate('/appointments'),
    onError: (error) => {
      if (
        error instanceof ApiError &&
        (error.code === 'double-booking' || error.code === 'scheduled-in-past')
      ) {
        setError('scheduledAt', { message: error.message });
        return true;
      }
      if (
        error instanceof ApiError &&
        (error.code === 'location-required' ||
          error.code === 'invalid-location' ||
          error.code === 'not-found')
      ) {
        setError('locationId', { message: error.message });
        return true;
      }
      return false;
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    await book
      .mutateAsync({
        vehicleId: values.vehicleId,
        // With one location the API picks it; with several, send the chosen one.
        locationId: mine.length > 1 ? chosen?.id : undefined,
        scheduledAt: localInputToIso(values.scheduledAt, zone),
        durationMinutes: values.durationMinutes,
        serviceType: values.serviceType,
        notes: values.notes.trim() || undefined,
      })
      .catch(() => undefined);
  });

  const vehicleList = vehicles.data?.content ?? [];

  return (
    <>
      <PageHeader title="Book a service" description="Choose the vehicle, a time and what it needs." />
      <Card className="max-w-2xl">
        <CardContent className="grid gap-5 pt-6">
          {advisor && !customerId && (
            <div className="grid gap-2">
              <Label htmlFor="customer-search">Customer</Label>
              <Input
                id="customer-search"
                placeholder="Search by name, email or phone"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <ul className="grid gap-1">
                {(customers.data?.content ?? []).map((c) => (
                  <li key={c.id}>
                    <Button
                      variant="ghost"
                      className="w-full justify-start"
                      onClick={() => setCustomerId(c.id)}
                    >
                      {c.name} <span className="text-muted-foreground">· {c.email}</span>
                    </Button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {advisor && customerId && (
            <p className="text-sm">
              Booking for the selected customer.{' '}
              <Button variant="link" className="h-auto p-0" onClick={() => setCustomerId('')}>
                Change customer
              </Button>
            </p>
          )}
          {(!advisor || customerId) && (
            <form noValidate onSubmit={onSubmit} className="grid gap-4">
              {mine.length > 1 && (
                <Field
                  label="Location"
                  error={errors.locationId?.message}
                  hint={
                    chosen
                      ? `${chosen.address ?? chosen.name} · times in ${zoneName(chosen.timeZone)}`
                      : undefined
                  }
                >
                  <Select
                    {...register('locationId')}
                    value={chosen?.id ?? ''}
                    onChange={(e) => setLocationId(e.target.value)}
                  >
                    {mine.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}
                      </option>
                    ))}
                  </Select>
                </Field>
              )}
              <Field
                label="Vehicle"
                error={errors.vehicleId?.message}
                hint={
                  !vehicles.isPending && vehicleList.length === 0 ? (
                    <>
                      No vehicles yet.{' '}
                      <Link
                        to={advisor ? `/customers/${customerId}` : '/vehicles'}
                        className="text-primary underline"
                      >
                        Add one first
                      </Link>
                      .
                    </>
                  ) : undefined
                }
              >
                <Select {...register('vehicleId')}>
                  <option value="">Select a vehicle…</option>
                  {vehicleList.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.registration} — {v.year} {v.make} {v.model}
                    </option>
                  ))}
                </Select>
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Date and time"
                  error={errors.scheduledAt?.message}
                  hint={
                    mine.length > 1 && chosen
                      ? `${chosen.name} time (${zoneLabel(new Date(), zone)})`
                      : undefined
                  }
                >
                  <Input type="datetime-local" {...register('scheduledAt')} />
                </Field>
                <Field label="Length">
                  <Select {...register('durationMinutes')}>
                    {DURATIONS.map((minutes) => (
                      <option key={minutes} value={minutes}>
                        {minutes < 60
                          ? `${minutes} minutes`
                          : `${minutes / 60} hour${minutes === 60 ? '' : 's'}`}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <Field label="Service" error={errors.serviceType?.message}>
                <Select {...register('serviceType')}>
                  {SERVICE_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {SERVICE_TYPE_LABELS[type]}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field
                label="Notes for the workshop"
                error={errors.notes?.message}
                hint="Optional, e.g. a noise when braking."
              >
                <Textarea {...register('notes')} />
              </Field>
              <div className="flex gap-2">
                <Button type="submit" disabled={isSubmitting}>
                  Book appointment
                </Button>
                <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
                  Cancel
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </>
  );
}

const zoneName = (zone: string) => zone.replaceAll('_', ' ');
