import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { ApiError } from '@/api/errors';
import type { Vehicle } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';

const currentYear = new Date().getFullYear();

const detailsSchema = z.object({
  registration: z.string().trim().min(1, 'Enter the registration').max(20, 'At most 20 characters'),
  make: z.string().trim().min(1, 'Enter the make').max(50, 'At most 50 characters'),
  model: z.string().trim().min(1, 'Enter the model').max(50, 'At most 50 characters'),
  year: z.coerce.number<string>().int('Enter a year').min(1900, 'Enter a year from 1900').max(currentYear + 1, 'Too far ahead'),
  mileage: z.coerce.number<string>().int('Whole miles only').min(0, 'Cannot be negative'),
});

const createSchema = detailsSchema.extend({
  vin: z
    .string()
    .trim()
    .toUpperCase()
    .regex(/^[A-HJ-NPR-Z0-9]{17}$/, '17 letters and digits (no I, O or Q)'),
});

/** Editing keeps the VIN as it is (it cannot change). */
const editSchema = detailsSchema.extend({ vin: z.string() });

type CreateValues = z.infer<typeof createSchema>;
type FormInput = z.input<typeof createSchema>;

export interface VehicleFormResult {
  vin: string;
  registration: string;
  make: string;
  model: string;
  year: number;
  mileage: number;
}

/** Add a vehicle (with VIN) or edit one (VIN fixed). Server errors like "VIN taken" show on the field. */
export function VehicleForm({
  vehicle,
  submitLabel,
  onSubmit,
}: {
  vehicle?: Vehicle;
  submitLabel: string;
  onSubmit: (values: VehicleFormResult) => Promise<unknown>;
}) {
  const editing = !!vehicle;
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormInput, unknown, CreateValues>({
    resolver: zodResolver((editing ? editSchema : createSchema) as typeof createSchema),
    defaultValues: {
      vin: vehicle?.vin ?? '',
      registration: vehicle?.registration ?? '',
      make: vehicle?.make ?? '',
      model: vehicle?.model ?? '',
      year: vehicle ? String(vehicle.year) : '',
      mileage: vehicle ? String(vehicle.mileage) : '',
    },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values);
      if (!editing) {
        reset();
      }
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'vin-taken' || error.code === 'invalid-vin') {
          setError('vin', { message: error.message });
        }
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in values) {
            setError(field as keyof FormInput, { message });
          }
        }
      }
    }
  });

  return (
    <form noValidate onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      {!editing && (
        <div className="sm:col-span-2">
          <Field label="VIN" error={errors.vin?.message} hint="17 characters, on the windscreen or door frame.">
            <Input autoComplete="off" className="uppercase" {...register('vin')} />
          </Field>
        </div>
      )}
      <Field label="Registration" error={errors.registration?.message}>
        <Input {...register('registration')} />
      </Field>
      <Field label="Make" error={errors.make?.message}>
        <Input {...register('make')} />
      </Field>
      <Field label="Model" error={errors.model?.message}>
        <Input {...register('model')} />
      </Field>
      <Field label="Year" error={errors.year?.message}>
        <Input inputMode="numeric" {...register('year')} />
      </Field>
      <Field label="Mileage (miles)" error={errors.mileage?.message}>
        <Input inputMode="numeric" {...register('mileage')} />
      </Field>
      <div className="flex items-end sm:col-span-2">
        <Button type="submit" disabled={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
