import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { workshopApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Workshop } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { formatMoney, setWorkshopZone, timeZones } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { useWorkshop, WORKSHOP_QUERY_KEY } from './use-workshop';

/** Currencies offered first; any other ISO code can be typed. */
const COMMON_CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD', 'NZD', 'CAD'];

const settingsSchema = z.object({
  displayName: z.string().trim().min(1, 'Enter the workshop name').max(100, 'At most 100 characters'),
  phone: z.string().trim().max(30, 'At most 30 characters'),
  email: z.union([z.literal(''), z.email('Enter a valid email address')]),
  address: z.string().trim().max(300, 'At most 300 characters'),
  labourRate: z.coerce
    .number<string>({ error: 'Enter a number' })
    .min(0, 'Cannot be negative')
    .max(10_000, 'At most 10,000')
    .refine((v) => Math.abs(v * 100 - Math.round(v * 100)) < 1e-6, 'At most 2 decimal places'),
  currency: z
    .string()
    .trim()
    .toUpperCase()
    .regex(/^[A-Z]{3}$/, 'A three-letter code such as USD'),
  timeZone: z.string().min(1, 'Choose a time zone'),
});

type SettingsInput = z.input<typeof settingsSchema>;
type SettingsOutput = z.output<typeof settingsSchema>;

/** The workshop ADMIN's settings: name, contact details, labour rate, currency and time zone. */
export function WorkshopSettingsPage() {
  const workshop = useWorkshop();
  return (
    <>
      <PageHeader
        title="Workshop settings"
        description="How your workshop appears to customers, and the rate, currency and time zone it works in."
      />
      <QueryState query={workshop}>{(w) => <SettingsForm key={w.version} workshop={w} />}</QueryState>
    </>
  );
}

function SettingsForm({ workshop }: { workshop: Workshop }) {
  const queryClient = useQueryClient();
  const zones = useMemo(() => {
    const all = timeZones();
    return all.includes(workshop.timeZone) ? all : [workshop.timeZone, ...all];
  }, [workshop.timeZone]);
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<SettingsInput, unknown, SettingsOutput>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      displayName: workshop.displayName,
      phone: workshop.phone ?? '',
      email: workshop.email ?? '',
      address: workshop.address ?? '',
      labourRate: String(workshop.labourRate),
      currency: workshop.currency,
      timeZone: workshop.timeZone,
    },
  });
  const save = useAction(workshopApi.updateSettings, {
    success: 'Workshop settings saved',
    onSuccess: (saved) => {
      setWorkshopZone(saved.timeZone);
      queryClient.setQueryData(WORKSHOP_QUERY_KEY, saved);
      // Dates and amounts on every other screen follow the new settings.
      void queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== WORKSHOP_QUERY_KEY[0] });
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.code === 'invalid-currency') {
          setError('currency', { message: error.message });
          return true;
        }
        if (error.code === 'invalid-time-zone') {
          setError('timeZone', { message: error.message });
          return true;
        }
        if (error.code === 'concurrent-update') {
          void queryClient.invalidateQueries({ queryKey: WORKSHOP_QUERY_KEY });
        }
      }
      return false;
    },
  });

  const currency = watch('currency');
  const rate = Number(watch('labourRate'));
  const preview =
    /^[A-Za-z]{3}$/.test(currency ?? '') && Number.isFinite(rate) ? safeMoney(rate, currency) : null;

  return (
    <form
      noValidate
      aria-label="Workshop settings"
      className="grid max-w-3xl gap-6"
      onSubmit={handleSubmit(async (values) => {
        await save
          .mutateAsync({
            displayName: values.displayName,
            phone: values.phone || undefined,
            email: values.email || undefined,
            address: values.address || undefined,
            labourRate: values.labourRate,
            currency: values.currency,
            timeZone: values.timeZone,
            version: workshop.version,
          })
          .catch(() => undefined);
      })}
    >
      <Card>
        <CardHeader>
          <CardTitle>Workshop</CardTitle>
          <CardDescription>Shown to staff and customers in the header and on their screens.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Workshop name" error={errors.displayName?.message}>
            <Input autoComplete="organization" {...register('displayName')} />
          </Field>
          <Field label="Phone" error={errors.phone?.message}>
            <Input type="tel" autoComplete="tel" {...register('phone')} />
          </Field>
          <Field label="Email" error={errors.email?.message}>
            <Input type="email" autoComplete="email" {...register('email')} />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Address" error={errors.address?.message}>
              <Textarea rows={2} autoComplete="street-address" {...register('address')} />
            </Field>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Money and time</CardTitle>
          <CardDescription>
            A job card keeps the rate and currency it opened with, so changes apply to new job cards only.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-3">
          <Field
            label="Labour rate per hour"
            error={errors.labourRate?.message}
            hint={preview ? `${preview} per hour` : undefined}
          >
            <Input inputMode="decimal" {...register('labourRate')} />
          </Field>
          <Field label="Currency" error={errors.currency?.message} hint="ISO code, e.g. USD">
            <Input
              list="workshop-currencies"
              autoCapitalize="characters"
              maxLength={3}
              {...register('currency')}
            />
          </Field>
          <datalist id="workshop-currencies">
            {COMMON_CURRENCIES.map((code) => (
              <option key={code} value={code} />
            ))}
          </datalist>
          <Field
            label="Time zone"
            error={errors.timeZone?.message}
            hint="Defines 'today' on the board and every time shown."
          >
            <Select {...register('timeZone')}>
              {zones.map((zone) => (
                <option key={zone} value={zone}>
                  {zone.replaceAll('_', ' ')}
                </option>
              ))}
            </Select>
          </Field>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting || !isDirty}>
          Save settings
        </Button>
      </div>
    </form>
  );
}

function safeMoney(amount: number, currency: string): string | null {
  try {
    return formatMoney(amount, currency.toUpperCase());
  } catch {
    return null;
  }
}
