import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';

import { tenantsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { timeZones } from '@/lib/format';
import { BASE_DOMAIN, browser, workshopUrl } from '@/lib/site';
import { SLUG_PATTERN, suggestSlug } from '@/lib/slug';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD', 'NZD', 'CAD'];

const schema = z
  .object({
    workshopName: z.string().trim().min(1, 'Enter your workshop name').max(100, 'At most 100 characters'),
    slug: z
      .string()
      .trim()
      .toLowerCase()
      .regex(
        SLUG_PATTERN,
        '3-30 lower-case letters, digits and single hyphens, not starting or ending with one',
      ),
    currency: z.string().regex(/^[A-Z]{3}$/, 'Choose a currency'),
    timeZone: z.string().min(1, 'Choose a time zone'),
    adminUsername: z
      .string()
      .trim()
      .min(3, 'At least 3 characters')
      .max(50, 'At most 50 characters')
      .regex(/^[A-Za-z0-9._-]+$/, "Only letters, digits, '.', '_' and '-'"),
    adminEmail: z.email('Enter a valid email address'),
    adminPassword: z.string().min(8, 'At least 8 characters').max(100, 'At most 100 characters'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.adminPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match',
  });

type SignupValues = z.input<typeof schema>;
type SignupOutput = z.output<typeof schema>;

const FIELDS = [
  'workshopName',
  'slug',
  'adminUsername',
  'adminEmail',
  'adminPassword',
  'currency',
  'timeZone',
] as const;

/** Self-service sign-up: a new workshop and its first admin, then on to the workshop's address. */
export function SignupPage() {
  const zones = useMemo(() => timeZones(), []);
  const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const [formError, setFormError] = useState<string | null>(null);
  const [slugEdited, setSlugEdited] = useState(false);
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues, unknown, SignupOutput>({
    resolver: zodResolver(schema),
    defaultValues: {
      workshopName: '',
      slug: '',
      currency: 'USD',
      timeZone: zones.includes(browserZone) ? browserZone : 'UTC',
      adminUsername: '',
      adminEmail: '',
      adminPassword: '',
      confirmPassword: '',
    },
  });

  const name = watch('workshopName');
  useEffect(() => {
    if (!slugEdited) {
      setValue('slug', suggestSlug(name ?? ''));
    }
  }, [name, slugEdited, setValue]);

  const slug = (watch('slug') ?? '').trim().toLowerCase();
  const debounced = useDebounced(slug, 300);
  const availability = useQuery({
    queryKey: ['slug-availability', debounced],
    queryFn: () => tenantsApi.availability(debounced),
    enabled: SLUG_PATTERN.test(debounced),
    staleTime: 10_000,
  });

  const slugHint = !SLUG_PATTERN.test(slug)
    ? `Your workshop's address: your-workshop.${BASE_DOMAIN}`
    : availability.data?.slug === slug
      ? availability.data.available
        ? `${slug}.${BASE_DOMAIN} is available`
        : availability.data.reason === 'reserved'
          ? `${slug} is reserved. Choose another address.`
          : `${slug}.${BASE_DOMAIN} is already taken`
      : `Checking ${slug}.${BASE_DOMAIN}…`;
  const slugUnavailable = availability.data?.slug === slug && !availability.data.available;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const created = await tenantsApi.signup({
        workshopName: values.workshopName,
        slug: values.slug,
        currency: values.currency,
        timeZone: values.timeZone,
        adminUsername: values.adminUsername,
        adminEmail: values.adminEmail,
        adminPassword: values.adminPassword,
      });
      browser.go(workshopUrl(created.slug, '/login?welcome=1'));
    } catch (error) {
      if (error instanceof ApiError) {
        if (['slug-taken', 'slug-reserved', 'invalid-slug'].includes(error.code)) {
          setError('slug', { message: error.message });
          return;
        }
        if (error.code === 'invalid-currency') {
          setError('currency', { message: error.message });
          return;
        }
        if (error.code === 'invalid-time-zone') {
          setError('timeZone', { message: error.message });
          return;
        }
        const fieldErrors = Object.entries(error.fieldErrors).filter(([field]) =>
          (FIELDS as readonly string[]).includes(field),
        );
        if (fieldErrors.length) {
          fieldErrors.forEach(([field, message]) => setError(field as (typeof FIELDS)[number], { message }));
          return;
        }
        if (error.code === 'too-many-requests') {
          setFormError('Too many sign-ups from this network. Please try again later.');
          return;
        }
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <Card className="mx-auto mt-6 w-full max-w-xl">
      <CardHeader>
        <h1 className="text-2xl font-bold tracking-tight">Set up your workshop</h1>
        <CardDescription>
          Your workshop gets its own Bayflow address. You'll be its admin and can invite your team afterwards.
        </CardDescription>
      </CardHeader>
      <form noValidate aria-label="Set up your workshop" onSubmit={onSubmit}>
        <CardContent className="grid gap-6">
          {formError && <Alert>{formError}</Alert>}
          <fieldset className="grid gap-4">
            <CardTitle className="text-base">Your workshop</CardTitle>
            <Field label="Workshop name" error={errors.workshopName?.message}>
              <Input autoComplete="organization" autoFocus {...register('workshopName')} />
            </Field>
            <Field
              label="Workshop address"
              error={errors.slug?.message ?? (slugUnavailable ? slugHint : undefined)}
              hint={slugHint}
            >
              <Input
                autoCapitalize="none"
                spellCheck={false}
                {...register('slug', { onChange: () => setSlugEdited(true) })}
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Currency" error={errors.currency?.message}>
                <Select {...register('currency')}>
                  {CURRENCIES.map((code) => (
                    <option key={code} value={code}>
                      {code}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Time zone" error={errors.timeZone?.message}>
                <Select {...register('timeZone')}>
                  {zones.map((zone) => (
                    <option key={zone} value={zone}>
                      {zone.replaceAll('_', ' ')}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          </fieldset>
          <fieldset className="grid gap-4">
            <CardTitle className="text-base">Your admin account</CardTitle>
            <Field label="Username" error={errors.adminUsername?.message}>
              <Input autoComplete="username" {...register('adminUsername')} />
            </Field>
            <Field label="Email" error={errors.adminEmail?.message}>
              <Input type="email" autoComplete="email" {...register('adminEmail')} />
            </Field>
            <Field label="Password" error={errors.adminPassword?.message} hint="At least 8 characters.">
              <Input type="password" autoComplete="new-password" {...register('adminPassword')} />
            </Field>
            <Field label="Confirm password" error={errors.confirmPassword?.message}>
              <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
            </Field>
          </fieldset>
        </CardContent>
        <CardFooter className="flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting || slugUnavailable}>
            {isSubmitting && <Spinner className="text-primary-foreground" />}
            Create workshop
          </Button>
          <p className="text-sm text-muted-foreground">
            Already on Bayflow?{' '}
            <Link to="/" className="font-medium text-primary hover:underline">
              Go to your workshop
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return debounced;
}
