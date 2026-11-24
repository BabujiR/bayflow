import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { tenantsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { LocationCount, ShopType } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { PRODUCT_NAME } from '@/lib/brand';

export const SHOP_TYPES: Record<ShopType, string> = {
  GENERAL_REPAIR: 'General repair',
  TIRES: 'Tires & wheels',
  QUICK_LUBE: 'Quick lube',
  COLLISION: 'Body & collision',
  DEALERSHIP: 'Dealership service',
  FLEET: 'Fleet maintenance',
  OTHER: 'Other',
};

export const LOCATION_COUNTS: Record<LocationCount, string> = {
  '1': '1 location',
  '2-3': '2–3 locations',
  '4-10': '4–10 locations',
  '11+': '11 or more',
};

const required = (what: string) => z.string().trim().min(1, what);

const schema = z.object({
  firstName: required('Enter your first name').max(60),
  lastName: required('Enter your last name').max(60),
  email: z.string().trim().email('Enter an email address').max(254),
  phone: z
    .string()
    .trim()
    .regex(/^[0-9+()\-. ]{7,30}$/, 'Enter a phone number'),
  shopName: required('Enter the shop name').max(120),
  shopType: z.string().min(1, 'Choose the shop type'),
  locations: z.string().min(1, 'Choose how many locations'),
  city: z.string().trim().max(120),
  message: z.string().trim().max(1000),
});

type Values = z.infer<typeof schema>;

/** "See Ramplight in action": a demo request, for one shop or a group of them. */
export function DemoRequestForm() {
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      shopName: '',
      shopType: '',
      locations: '',
      city: '',
      message: '',
    },
  });
  const errors = formState.errors;

  if (sentTo) {
    return (
      <Card className="w-full shadow-lg lg:max-w-md lg:justify-self-end">
        <CardHeader>
          <CardTitle className="text-xl">Thanks, we&apos;ll be in touch</CardTitle>
          <CardDescription role="status">
            We&apos;ll contact you at {sentTo} within one business day to set up your demo.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card id="demo" className="w-full scroll-mt-8 shadow-lg lg:max-w-md lg:justify-self-end">
      <CardHeader>
        <CardTitle className="text-xl">See {PRODUCT_NAME} in action</CardTitle>
        <CardDescription>A 30-minute walkthrough for your shop or your group of shops.</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          noValidate
          aria-label="Request a demo"
          className="grid gap-3"
          onSubmit={handleSubmit(async (values) => {
            setProblem(null);
            try {
              await tenantsApi.requestDemo({
                ...values,
                shopType: values.shopType as ShopType,
                locations: values.locations as LocationCount,
                city: values.city || null,
                message: values.message || null,
              });
              setSentTo(values.email);
            } catch (error) {
              setProblem(
                error instanceof ApiError && error.status === 429
                  ? 'Too many requests from here. Try again in an hour.'
                  : errorMessage(error),
              );
            }
          })}
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="First name" error={errors.firstName?.message}>
              <Input autoComplete="given-name" {...register('firstName')} />
            </Field>
            <Field label="Last name" error={errors.lastName?.message}>
              <Input autoComplete="family-name" {...register('lastName')} />
            </Field>
            <Field label="Email" error={errors.email?.message}>
              <Input type="email" autoComplete="email" {...register('email')} />
            </Field>
            <Field label="Phone" error={errors.phone?.message}>
              <Input type="tel" autoComplete="tel" {...register('phone')} />
            </Field>
          </div>
          <Field label="Shop or group name" error={errors.shopName?.message}>
            <Input autoComplete="organization" {...register('shopName')} />
          </Field>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Shop type" error={errors.shopType?.message}>
              <Select {...register('shopType')}>
                <option value="">Choose…</option>
                {Object.entries(SHOP_TYPES).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="How many locations?" error={errors.locations?.message}>
              <Select {...register('locations')}>
                <option value="">Choose…</option>
                {Object.entries(LOCATION_COUNTS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="City and state" hint="Optional">
            <Input autoComplete="address-level2" {...register('city')} />
          </Field>
          <Field label="Anything we should know?" hint="Optional: what you use today, what you need">
            <Textarea rows={2} {...register('message')} />
          </Field>
          {problem && (
            <p role="alert" className="text-sm text-destructive">
              {problem}
            </p>
          )}
          <Button type="submit" size="lg" disabled={formState.isSubmitting}>
            {formState.isSubmitting ? 'Sending…' : 'Request a demo'}
          </Button>
          <p className="text-xs text-muted-foreground">
            We only use these details to contact you about {PRODUCT_NAME}.
          </p>
        </form>
      </CardContent>
    </Card>
  );
}
