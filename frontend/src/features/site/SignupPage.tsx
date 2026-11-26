import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router';
import { z } from 'zod';

import { tenantsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { InviteDetails, Role } from '@/api/types';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { PRODUCT_NAME } from '@/lib/brand';
import { formatDate, timeZones } from '@/lib/format';
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

/** Problems with the setup link itself (the server's codes), shown instead of the form. */
const INVITE_PROBLEMS = ['invite-invalid', 'invite-expired', 'invite-used'];

/**
 * Workshop sign-up, by invite only: Ramplight's team sends a setup link
 * (<site>/setup?invite=…). It creates the workshop and its first admin, then goes on to the
 * workshop's address. Without a working link it explains how to get one.
 */
export function SignupPage() {
  const [params] = useSearchParams();
  const token = params.get('invite')?.trim() ?? '';
  const [linkProblem, setLinkProblem] = useState<string | null>(null);
  const invite = useQuery({
    queryKey: ['invite', token],
    queryFn: () => tenantsApi.checkInvite(token),
    enabled: token !== '',
    retry: false,
    staleTime: Infinity,
  });

  if (!token) {
    return (
      <InviteNeeded
        title="Ramplight is invite-only"
        message="Workshops join with a setup link from our team. Request a demo and we'll take it from there."
      />
    );
  }
  if (invite.isPending) {
    return (
      <div className="mt-16 flex justify-center" role="status" aria-label="Checking your setup link">
        <Spinner />
      </div>
    );
  }
  if (linkProblem || invite.isError) {
    return (
      <InviteNeeded
        title="This setup link doesn't work"
        message={linkProblem ?? errorMessage(invite.error)}
      />
    );
  }
  return <SignupForm token={token} invite={invite.data} onLinkProblem={setLinkProblem} />;
}

function InviteNeeded({ title, message }: { title: string; message: string }) {
  return (
    <Card className="mx-auto mt-6 w-full max-w-xl">
      <CardHeader>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        <CardDescription>{message}</CardDescription>
      </CardHeader>
      <CardFooter className="flex flex-wrap gap-3">
        <Button asChild>
          <a href="/#demo">Request a demo</a>
        </Button>
        <Button asChild variant="outline">
          <Link to="/">Go to your workshop</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}

function SignupForm({
  token,
  invite,
  onLinkProblem,
}: {
  token: string;
  invite: InviteDetails;
  onLinkProblem: (message: string) => void;
}) {
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
      workshopName: invite.shopName ?? '',
      slug: '',
      currency: 'USD',
      timeZone: zones.includes(browserZone) ? browserZone : 'UTC',
      adminUsername: '',
      adminEmail: invite.email,
      adminPassword: '',
      confirmPassword: '',
    },
  });

  const [ownerAlso, setOwnerAlso] = useState<Role[]>([]);
  const toggleOwnerAlso = (role: Role, on: boolean) =>
    setOwnerAlso((current) =>
      on ? [...current.filter((r) => r !== role), role] : current.filter((r) => r !== role),
    );

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
        inviteToken: token,
        ...(ownerAlso.length ? { ownerAlso } : {}),
      });
      browser.go(workshopUrl(created.slug, '/login?welcome=1'));
    } catch (error) {
      if (error instanceof ApiError) {
        if (INVITE_PROBLEMS.includes(error.code)) {
          onLinkProblem(error.message);
          return;
        }
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
          Your workshop gets its own {PRODUCT_NAME} address. You'll be its admin and can invite your team
          afterwards.
        </CardDescription>
        <p className="text-sm text-muted-foreground" data-testid="invite-for">
          Invited as <strong className="text-foreground">{invite.email}</strong> · this link works until{' '}
          {formatDate(invite.expiresAt)}.
        </p>
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
          <fieldset className="mt-6 grid gap-2">
            <legend className="mb-1 text-sm font-medium">Do you also work in the shop?</legend>
            <p className="-mt-1 text-sm text-muted-foreground">
              You&apos;ll run the workshop as its admin. Tick what else you do, and you&apos;ll get those
              screens too. You can change it later.
            </p>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={ownerAlso.includes('SERVICE_ADVISOR')}
                onChange={(e) => toggleOwnerAlso('SERVICE_ADVISOR', e.target.checked)}
              />
              I also run the front desk (bookings, check-in, invoices)
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={ownerAlso.includes('MECHANIC')}
                onChange={(e) => toggleOwnerAlso('MECHANIC', e.target.checked)}
              />
              I also work on cars (My jobs)
            </label>
          </fieldset>
        </CardContent>
        <CardFooter className="flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting || slugUnavailable}>
            {isSubmitting && <Spinner className="text-primary-foreground" />}
            Create workshop
          </Button>
          <p className="text-sm text-muted-foreground">
            Already on {PRODUCT_NAME}?{' '}
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
