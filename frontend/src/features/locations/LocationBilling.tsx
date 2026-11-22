import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { locationsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Location } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useAction } from '@/lib/use-action';

import { LOCATIONS_QUERY_KEY } from './use-locations';

/** A percentage 0–100 with up to 3 decimals; '' allowed when optional. */
const percent = (optional: boolean) =>
  z
    .string()
    .trim()
    .refine((v) => (optional && v === '') || (/^\d{1,3}(\.\d{1,3})?$/.test(v) && Number(v) <= 100), {
      message: 'A percentage from 0 to 100, e.g. 8.875',
    });

/** An amount with up to 2 decimals; '' = off. */
const amount = z
  .string()
  .trim()
  .refine((v) => v === '' || /^\d{1,6}(\.\d{1,2})?$/.test(v), { message: 'An amount like 3.50' });

const schema = z.object({
  partsTaxRate: percent(false),
  labourTaxRate: percent(false),
  feesTaxRate: percent(false),
  shopSuppliesPercent: percent(true),
  shopSuppliesCap: amount,
  disposalFee: amount,
  paymentTermsDays: z.string(),
  invoiceFooter: z.string().trim().max(500, 'At most 500 characters'),
});

type FormValues = z.infer<typeof schema>;

export const PAYMENT_TERMS = [
  { days: 0, label: 'Due on receipt' },
  { days: 7, label: '7 days' },
  { days: 15, label: '15 days' },
  { days: 30, label: '30 days' },
];

const text = (value: number | null | undefined) =>
  value === null || value === undefined ? '' : String(value);
const numberOrNull = (value: string) => (value === '' ? null : Number(value));

/** ADMIN: a location's tax rates, shop fees and invoice terms, copied onto each new invoice. */
export function LocationBillingButton({ location }: { location: Location }) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Tax and fees for ${location.name}`}>
          Tax & fees
        </Button>
      </DialogTrigger>
      <DialogContent
        title={`Tax and fees · ${location.name}`}
        description="Used on new invoices from this location. Changing them never changes invoices already made."
        className="max-w-lg"
      >
        <BillingForm location={location} onSaved={() => setOpen(false)} />
      </DialogContent>
    </Dialog>
  );
}

function BillingForm({ location, onSaved }: { location: Location; onSaved: () => void }) {
  const queryClient = useQueryClient();
  const b = location.billing;
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      partsTaxRate: text(b?.partsTaxRate ?? 0),
      labourTaxRate: text(b?.labourTaxRate ?? 0),
      feesTaxRate: text(b?.feesTaxRate ?? 0),
      shopSuppliesPercent: text(b?.shopSuppliesPercent),
      shopSuppliesCap: text(b?.shopSuppliesCap),
      disposalFee: text(b?.disposalFee),
      paymentTermsDays: String(b?.paymentTermsDays ?? 0),
      invoiceFooter: b?.invoiceFooter ?? '',
    },
  });
  const [problem, setProblem] = useState<string | null>(null);
  const save = useAction(
    (values: FormValues) =>
      locationsApi.updateBilling(
        location.id,
        {
          partsTaxRate: Number(values.partsTaxRate),
          labourTaxRate: Number(values.labourTaxRate),
          feesTaxRate: Number(values.feesTaxRate),
          shopSuppliesPercent: numberOrNull(values.shopSuppliesPercent),
          shopSuppliesCap: numberOrNull(values.shopSuppliesCap),
          disposalFee: numberOrNull(values.disposalFee),
          paymentTermsDays: Number(values.paymentTermsDays),
          invoiceFooter: values.invoiceFooter || null,
        },
        location.version,
      ),
    {
      success: (l) => `Tax and fees saved for ${l.name}`,
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: LOCATIONS_QUERY_KEY });
        onSaved();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.isClientError) {
          setProblem(error.message);
          return true;
        }
        return false;
      },
    },
  );

  return (
    <form noValidate onSubmit={handleSubmit((values) => save.mutateAsync(values))} className="grid gap-4">
      <fieldset className="grid gap-3 sm:grid-cols-3">
        <legend className="mb-2 text-sm font-medium">Tax rates (%)</legend>
        <Field label="Parts" error={errors.partsTaxRate?.message}>
          <Input inputMode="decimal" {...register('partsTaxRate')} />
        </Field>
        <Field label="Labour" error={errors.labourTaxRate?.message}>
          <Input inputMode="decimal" {...register('labourTaxRate')} />
        </Field>
        <Field label="Fees" error={errors.feesTaxRate?.message}>
          <Input inputMode="decimal" {...register('feesTaxRate')} />
        </Field>
      </fieldset>
      <fieldset className="grid gap-3 sm:grid-cols-3">
        <legend className="mb-2 text-sm font-medium">Fees (leave empty for none)</legend>
        <Field label="Shop supplies (% of labour)" error={errors.shopSuppliesPercent?.message}>
          <Input inputMode="decimal" {...register('shopSuppliesPercent')} />
        </Field>
        <Field label="Shop supplies cap" error={errors.shopSuppliesCap?.message}>
          <Input inputMode="decimal" {...register('shopSuppliesCap')} />
        </Field>
        <Field label="Disposal fee" error={errors.disposalFee?.message}>
          <Input inputMode="decimal" {...register('disposalFee')} />
        </Field>
      </fieldset>
      <Field label="Payment terms">
        <Select {...register('paymentTermsDays')}>
          {PAYMENT_TERMS.map((t) => (
            <option key={t.days} value={t.days}>
              {t.label}
            </option>
          ))}
        </Select>
      </Field>
      <Field
        label="Invoice footer"
        error={errors.invoiceFooter?.message}
        hint="Printed at the bottom of every invoice, e.g. your warranty or bank details."
      >
        <Textarea rows={3} {...register('invoiceFooter')} />
      </Field>
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
      <Button type="submit" disabled={isSubmitting}>
        Save tax and fees
      </Button>
    </form>
  );
}
