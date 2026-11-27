import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useRef } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import { ApiError } from '@/api/errors';
import type { DecodedVin, Vehicle } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';

import { useVinDecode, VinDecodeNote } from './vin-decode';
import { VinScanButton } from './VinScanner';

const currentYear = new Date().getFullYear();

const detailsSchema = z.object({
  registration: z.string().trim().min(1, 'Enter the registration').max(20, 'At most 20 characters'),
  make: z.string().trim().min(1, 'Enter the make').max(50, 'At most 50 characters'),
  model: z.string().trim().min(1, 'Enter the model').max(50, 'At most 50 characters'),
  year: z.coerce
    .number<string>()
    .int('Enter a year')
    .min(1900, 'Enter a year from 1900')
    .max(currentYear + 1, 'Too far ahead'),
  mileage: z.coerce.number<string>().int('Whole miles only').min(0, 'Cannot be negative'),
  trim: z.string().trim().max(60, 'At most 60 characters'),
  engine: z.string().trim().max(80, 'At most 80 characters'),
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
  /** Optional ('' when not known). */
  trim: string;
  engine: string;
}

/** The decoded details that differ from what is in the form (those already typed are kept). */
const DECODED_FIELDS = ['make', 'model', 'year', 'trim', 'engine'] as const;

function decodedValue(d: DecodedVin, field: (typeof DECODED_FIELDS)[number]): string {
  const value = d[field];
  return value === null || value === undefined ? '' : String(value);
}

/** Add a vehicle (with VIN) or edit one (VIN fixed). Server errors like "VIN taken" show on the field. */
/**
 * Errors the form shows next to its own fields. Callers pass this to useAction's onError so
 * the same message is not also shown as a toast.
 */
export function isVehicleFieldError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'vin-taken' || error.code === 'invalid-vin' || Object.keys(error.fieldErrors).length > 0)
  );
}

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
  // Staff look VINs up (year, make, model, trim and engine fill in); customers type them.
  const canDecode = useAuth().hasRole('SERVICE_ADVISOR', 'ADMIN') && !editing;
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    getValues,
    setValue,
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
      trim: vehicle?.trim ?? '',
      engine: vehicle?.engine ?? '',
    },
  });
  const vin = useWatch({ control, name: 'vin' }) ?? '';
  const decoded = useVinDecode(vin, canDecode);
  // Fill in what is still empty, once per decoded VIN; what staff typed is never overwritten.
  const filledFor = useRef<string | null>(null);
  useEffect(() => {
    const d = decoded.data;
    if (!d || filledFor.current === d.vin) {
      return;
    }
    filledFor.current = d.vin;
    for (const field of DECODED_FIELDS) {
      const value = decodedValue(d, field);
      if (value && !String(getValues(field) ?? '').trim()) {
        setValue(field, value, { shouldValidate: true });
      }
    }
  }, [decoded.data, getValues, setValue]);
  // Re-rendered as these change, so "Use decoded details" shows only while something differs.
  const current = useWatch({ control, name: DECODED_FIELDS });
  const differs =
    decoded.data !== undefined &&
    DECODED_FIELDS.some((field, i) => {
      const value = decodedValue(decoded.data!, field);
      return value !== '' && String(current[i] ?? '').trim() !== value;
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
          <Field
            label="VIN"
            error={errors.vin?.message}
            hint="17 characters, on the windscreen or door frame."
          >
            <Input autoComplete="off" className="uppercase" {...register('vin')} />
          </Field>
          <div className="mt-2">
            <VinScanButton onScan={(scanned) => setValue('vin', scanned, { shouldValidate: true })} />
          </div>
          {canDecode && (
            <div className="mt-2 flex flex-wrap items-start justify-between gap-2">
              <VinDecodeNote query={decoded} />
              {differs && (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    for (const field of DECODED_FIELDS) {
                      const value = decodedValue(decoded.data!, field);
                      if (value) {
                        setValue(field, value, { shouldValidate: true });
                      }
                    }
                  }}
                >
                  Use decoded details
                </Button>
              )}
            </div>
          )}
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
      <Field label="Trim (optional)" error={errors.trim?.message}>
        <Input {...register('trim')} />
      </Field>
      <Field label="Engine (optional)" error={errors.engine?.message}>
        <Input placeholder="2.0L 4-cyl Gasoline" {...register('engine')} />
      </Field>
      <div className="flex items-end sm:col-span-2">
        <Button type="submit" disabled={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
