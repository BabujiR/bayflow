import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { bookingApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { PublicBooked, PublicBookingOptions } from '@/api/types';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { BrandBanner } from '@/features/branding/BrandBanner';
import { PoweredBy } from '@/features/branding/PoweredBy';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { todayIso } from '@/lib/format';
import { cn } from '@/lib/utils';
import { WorkshopNotFoundPage } from '@/pages/WorkshopNotFoundPage';

import { durationLabel, priceLabel } from './BookingServicesSection';

export const BOOKING_OPTIONS_KEY = ['booking', 'options'] as const;

const STEPS = ['Service', 'Car', 'Time', 'Your details'] as const;
type Step = 0 | 1 | 2 | 3;

/** "Something else": the customer says what the car needs. */
const OTHER = 'other';
const VIN = /^[A-HJ-NPR-Z0-9]{17}$/i;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[0-9+()\-. ]{7,30}$/;
const NEXT_YEAR = new Date().getFullYear() + 1;
const YEARS = Array.from({ length: NEXT_YEAR - 1950 + 1 }, (_, i) => NEXT_YEAR - i);

/** "2026-11-16" plus `days`, as a date (no time zone involved). */
function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

const dayLabel = (date: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat(undefined, { ...options, timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));

const timeLabel = (iso: string, zone: string) =>
  new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', timeZone: zone }).format(
    new Date(iso),
  );

const whenLabel = (iso: string, zone: string) =>
  new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: zone,
  }).format(new Date(iso));

/**
 * The public booking page (`<workshop>/book`): customers book without an account, from a link
 * on Google Maps or the shop's site. `?location=<code>` picks the location (shops with one
 * location never ask); `?service=<code>` picks the service. Confirmed at once, by e-mail too.
 */
export function GuestBookingPage() {
  const options = useQuery({
    queryKey: BOOKING_OPTIONS_KEY,
    queryFn: bookingApi.options,
    staleTime: 5 * 60_000,
    retry: (count, error) => !(error instanceof ApiError && error.isClientError) && count < 2,
  });
  const notFound = options.error instanceof ApiError && options.error.code === 'workshop-not-found';

  return (
    <div className="min-h-svh bg-gradient-to-b from-primary/5 to-background">
      <div className="flex min-h-svh flex-col">
        <BrandBanner />
        <header className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3 px-4 py-4">
          <div className="flex min-w-0 items-center gap-3">
            <WorkshopLogo />
            {options.data && (
              <span className="truncate font-semibold" data-testid="booking-shop-name">
                {options.data.shopName}
              </span>
            )}
          </div>
          <ThemeToggle />
        </header>
        <main className="mx-auto w-full max-w-2xl flex-1 px-4 pb-16">
          {options.isPending ? (
            <div className="grid gap-3" aria-label="Loading">
              <Skeleton className="h-8 w-56" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : notFound ? (
            <WorkshopNotFoundPage />
          ) : options.isError ? (
            <Alert>
              {options.error instanceof ApiError && options.error.code === 'workshop-suspended'
                ? "This workshop isn't taking online bookings right now. Please call the shop."
                : errorMessage(options.error)}
            </Alert>
          ) : (
            <BookingFlow options={options.data} />
          )}
        </main>
        <PoweredBy />
      </div>
    </div>
  );
}

function BookingFlow({ options }: { options: PublicBookingOptions }) {
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const linked = options.locations.find(
    (l) => l.code.toLowerCase() === params.get('location')?.toLowerCase(),
  );
  const [location, setLocation] = useState(
    linked?.code ?? (options.locations.length === 1 ? options.locations[0].code : ''),
  );
  const offered = options.services.filter((s) => s.locations === null || s.locations.includes(location));
  const linkedService = params.get('service');
  const [service, setService] = useState<string | null>(
    linkedService && options.services.some((s) => s.code === linkedService) ? linkedService : null,
  );
  const [description, setDescription] = useState('');
  const [step, setStep] = useState<Step>(0);
  const [carMode, setCarMode] = useState<'model' | 'vin'>('model');
  const [year, setYear] = useState('');
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [vin, setVin] = useState('');
  const [slot, setSlot] = useState<string | null>(null);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [smsConsent, setSmsConsent] = useState(false);
  const [website, setWebsite] = useState('');
  const [touched, setTouched] = useState(false);
  const [timeError, setTimeError] = useState<string | null>(null);
  const [booked, setBooked] = useState<PublicBooked | null>(null);

  const here = options.locations.find((l) => l.code === location);
  const chosen = options.services.find((s) => s.code === service);
  const serviceName = service === OTHER ? 'Something else' : chosen?.name;

  const serviceOk =
    location !== '' && (service === OTHER ? description.trim().length >= 3 : service !== null);
  const carOk =
    carMode === 'vin' ? VIN.test(vin.trim()) : year !== '' && make.trim() !== '' && model.trim() !== '';
  const phoneOk = phone.trim() === '' || PHONE.test(phone.trim());
  const detailsOk = firstName.trim() !== '' && lastName.trim() !== '' && EMAIL.test(email.trim()) && phoneOk;
  const car =
    carMode === 'vin' ? `VIN ${vin.trim().toUpperCase()}` : `${year} ${make.trim()} ${model.trim()}`.trim();

  const book = useMutation({
    mutationFn: () =>
      bookingApi.book({
        location,
        service: service === OTHER ? null : service,
        description: description.trim() || null,
        vin: carMode === 'vin' ? vin.trim().toUpperCase() : null,
        year: carMode === 'model' ? Number(year) : null,
        make: carMode === 'model' ? make.trim() : null,
        model: carMode === 'model' ? model.trim() : null,
        startsAt: slot!,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        phone: phone.trim() || null,
        smsConsent: phone.trim() !== '' && smsConsent,
        website,
      }),
    onSuccess: setBooked,
    onError: (error) => {
      if (error instanceof ApiError && (error.code === 'slot-taken' || error.code === 'double-booking')) {
        setSlot(null);
        setTimeError(errorMessage(error));
        void queryClient.invalidateQueries({ queryKey: ['booking', 'availability'] });
        setStep(2);
      } else if (
        error instanceof ApiError &&
        ['invalid-vin', 'vehicle-required', 'vin-registered'].includes(error.code)
      ) {
        setStep(1);
      }
    },
  });

  if (booked) {
    return <Booked booked={booked} car={car} manyLocations={options.locations.length > 1} />;
  }

  const next = () => {
    setTouched(true);
    const ok = [serviceOk, carOk, slot !== null, detailsOk][step];
    if (!ok) {
      return;
    }
    setTouched(false);
    if (step < 3) {
      setStep((step + 1) as Step);
    } else {
      book.mutate();
    }
  };

  return (
    <div className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Book a visit</h1>
        <p className="text-sm text-muted-foreground">
          No account needed. Already have one?{' '}
          <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
      <Stepper step={step} onGo={(s) => s < step && setStep(s)} />
      <Card>
        <CardContent className="pt-6">
          <form
            noValidate
            aria-label={STEPS[step]}
            onSubmit={(e) => {
              e.preventDefault();
              next();
            }}
          >
            <div key={step} className="gb-step grid gap-5">
              {step === 0 && (
                <>
                  {options.locations.length > 1 && (
                    <Field
                      label="Location"
                      error={touched && location === '' ? 'Choose a location' : undefined}
                      hint={here?.address ?? undefined}
                    >
                      <Select
                        value={location}
                        onChange={(e) => {
                          const code = e.target.value;
                          setLocation(code);
                          // A service not offered at the new location is dropped.
                          const there = options.services.find((s) => s.code === service);
                          if (there?.locations && !there.locations.includes(code)) {
                            setService(null);
                          }
                        }}
                      >
                        <option value="">Choose a location…</option>
                        {options.locations.map((l) => (
                          <option key={l.code} value={l.code}>
                            {l.name}
                          </option>
                        ))}
                      </Select>
                    </Field>
                  )}
                  <fieldset className="grid gap-2">
                    <legend className="mb-2 text-sm font-medium">What does your car need?</legend>
                    {offered.map((s) => {
                      const details = [
                        priceLabel(s, options.currency),
                        durationLabel(s.durationMinutes) && `about ${durationLabel(s.durationMinutes)}`,
                      ].filter(Boolean);
                      return (
                        <Choice
                          key={s.code}
                          name="service"
                          checked={service === s.code}
                          onSelect={() => setService(s.code)}
                          title={s.name}
                          side={details[0]}
                          detail={[s.description, details[1]].filter(Boolean).join(' · ')}
                        />
                      );
                    })}
                    <Choice
                      name="service"
                      checked={service === OTHER}
                      onSelect={() => setService(OTHER)}
                      title="Something else"
                      detail="Tell us what's going on and we'll take a look."
                    />
                    {touched && service === null && (
                      <p role="alert" className="text-sm text-destructive">
                        Choose a service
                      </p>
                    )}
                  </fieldset>
                  {service === OTHER && (
                    <Field
                      label="What's going on?"
                      error={
                        touched && description.trim().length < 3
                          ? 'Tell us briefly what your car needs'
                          : undefined
                      }
                    >
                      <Textarea
                        rows={3}
                        maxLength={500}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="e.g. A squeak from the front when braking"
                      />
                    </Field>
                  )}
                </>
              )}

              {step === 1 && (
                <>
                  <div className="grid gap-1">
                    <h2 className="font-medium">Your car</h2>
                    <p className="text-sm text-muted-foreground">
                      Year, make and model, or the VIN. Either is fine.
                    </p>
                  </div>
                  <div
                    role="tablist"
                    aria-label="How to describe your car"
                    className="grid grid-cols-2 rounded-lg bg-muted p-1"
                  >
                    {(['model', 'vin'] as const).map((m) => (
                      <button
                        key={m}
                        type="button"
                        role="tab"
                        aria-selected={carMode === m}
                        onClick={() => {
                          setCarMode(m);
                          setTouched(false);
                        }}
                        className={cn(
                          'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                          carMode === m
                            ? 'bg-background shadow-sm'
                            : 'text-muted-foreground hover:text-foreground',
                        )}
                      >
                        {m === 'model' ? 'Year, make & model' : 'VIN'}
                      </button>
                    ))}
                  </div>
                  {carMode === 'model' ? (
                    <div className="grid gap-4 sm:grid-cols-3">
                      <Field label="Year" error={touched && year === '' ? 'Choose the year' : undefined}>
                        <Select value={year} onChange={(e) => setYear(e.target.value)}>
                          <option value="">Year…</option>
                          {YEARS.map((y) => (
                            <option key={y} value={y}>
                              {y}
                            </option>
                          ))}
                        </Select>
                      </Field>
                      <Field
                        label="Make"
                        error={touched && make.trim() === '' ? 'Enter the make' : undefined}
                      >
                        <Input
                          value={make}
                          maxLength={50}
                          onChange={(e) => setMake(e.target.value)}
                          placeholder="Honda"
                        />
                      </Field>
                      <Field
                        label="Model"
                        error={touched && model.trim() === '' ? 'Enter the model' : undefined}
                      >
                        <Input
                          value={model}
                          maxLength={50}
                          onChange={(e) => setModel(e.target.value)}
                          placeholder="Civic"
                        />
                      </Field>
                    </div>
                  ) : (
                    <Field
                      label="VIN"
                      error={
                        touched && !VIN.test(vin.trim())
                          ? 'A VIN has 17 letters and digits (no I, O or Q)'
                          : undefined
                      }
                      hint="On the dashboard by the windshield, or on your registration card."
                    >
                      <Input
                        value={vin}
                        maxLength={17}
                        autoCapitalize="characters"
                        className="font-mono uppercase"
                        onChange={(e) => setVin(e.target.value)}
                        placeholder="1HGCV1F3XLA012345"
                      />
                    </Field>
                  )}
                  {book.error instanceof ApiError && book.error.code === 'vin-registered' && (
                    <Alert>{errorMessage(book.error)}</Alert>
                  )}
                </>
              )}

              {step === 2 && here && (
                <TimePicker
                  location={here.code}
                  zone={here.timeZone}
                  slot={slot}
                  onPick={(s) => {
                    setSlot(s);
                    setTimeError(null);
                  }}
                  error={timeError ?? (touched && slot === null ? 'Choose a time' : null)}
                />
              )}

              {step === 3 && (
                <>
                  <Summary
                    rows={[
                      ['Service', serviceName ?? ''],
                      ['Car', car],
                      ['When', slot && here ? whenLabel(slot, here.timeZone) : ''],
                      ...(options.locations.length > 1 && here
                        ? [['Where', here.name] as [string, string]]
                        : []),
                    ]}
                  />
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field
                      label="First name"
                      error={touched && firstName.trim() === '' ? 'Enter your first name' : undefined}
                    >
                      <Input
                        value={firstName}
                        maxLength={50}
                        autoComplete="given-name"
                        onChange={(e) => setFirstName(e.target.value)}
                      />
                    </Field>
                    <Field
                      label="Last name"
                      error={touched && lastName.trim() === '' ? 'Enter your last name' : undefined}
                    >
                      <Input
                        value={lastName}
                        maxLength={50}
                        autoComplete="family-name"
                        onChange={(e) => setLastName(e.target.value)}
                      />
                    </Field>
                  </div>
                  <Field
                    label="Email"
                    error={touched && !EMAIL.test(email.trim()) ? 'Enter your email address' : undefined}
                    hint="We'll send your confirmation here."
                  >
                    <Input
                      type="email"
                      value={email}
                      maxLength={254}
                      autoComplete="email"
                      onChange={(e) => setEmail(e.target.value)}
                    />
                  </Field>
                  <Field
                    label="Phone (optional)"
                    error={touched && !phoneOk ? 'Enter a phone number, or leave it empty' : undefined}
                  >
                    <Input
                      type="tel"
                      value={phone}
                      maxLength={30}
                      autoComplete="tel"
                      onChange={(e) => setPhone(e.target.value)}
                    />
                  </Field>
                  {phone.trim() !== '' && (
                    <div className="gb-step grid gap-1 rounded-lg border bg-muted/40 p-3">
                      <label className="flex items-start gap-2 text-sm">
                        <input
                          type="checkbox"
                          className="mt-0.5"
                          checked={smsConsent}
                          onChange={(e) => setSmsConsent(e.target.checked)}
                        />
                        <span>Text me about this visit (reminders and when my car is ready).</span>
                      </label>
                      <p className="pl-6 text-xs text-muted-foreground">
                        ⓘ Your number is only used by {options.shopName} for your visits. It is never shared
                        with or sold to third parties.
                      </p>
                    </div>
                  )}
                  {/* Only bots fill this in; people never see it. */}
                  <div aria-hidden="true" className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
                    <label>
                      Website
                      <input
                        tabIndex={-1}
                        autoComplete="off"
                        value={website}
                        onChange={(e) => setWebsite(e.target.value)}
                      />
                    </label>
                  </div>
                  {book.isError &&
                    !(
                      book.error instanceof ApiError &&
                      ['slot-taken', 'double-booking'].includes(book.error.code)
                    ) && <Alert>{errorMessage(book.error)}</Alert>}
                </>
              )}
            </div>
            <div className="mt-6 flex items-center justify-between gap-2">
              {step > 0 ? (
                <Button type="button" variant="ghost" onClick={() => setStep((step - 1) as Step)}>
                  Back
                </Button>
              ) : (
                <span />
              )}
              <Button type="submit" disabled={book.isPending}>
                {step < 3 ? 'Continue' : book.isPending ? 'Booking…' : 'Book my visit'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function Stepper({ step, onGo }: { step: Step; onGo: (step: Step) => void }) {
  return (
    <ol className="grid grid-cols-4 gap-2" aria-label="Steps">
      {STEPS.map((label, i) => {
        const done = i < step;
        return (
          <li key={label} aria-current={i === step ? 'step' : undefined}>
            <button
              type="button"
              disabled={!done}
              onClick={() => onGo(i as Step)}
              className="grid w-full gap-1.5 text-left disabled:cursor-default"
            >
              <span
                className={cn(
                  'h-1.5 rounded-full transition-colors duration-300',
                  i <= step ? 'bg-primary' : 'bg-muted',
                )}
              />
              <span className={cn('text-xs', i === step ? 'font-medium' : 'text-muted-foreground')}>
                {done ? '✓ ' : ''}
                {label}
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}

function Choice({
  name,
  checked,
  onSelect,
  title,
  side,
  detail,
}: {
  name: string;
  checked: boolean;
  onSelect: () => void;
  title: string;
  side?: string;
  detail?: string;
}) {
  return (
    <label
      className={cn(
        'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-all hover:border-primary/60',
        checked && 'border-primary bg-primary/5 ring-1 ring-primary',
      )}
    >
      <input type="radio" name={name} className="mt-1" checked={checked} onChange={onSelect} />
      <span className="grid min-w-0 flex-1 gap-0.5">
        <span className="flex items-baseline justify-between gap-2">
          <span className="font-medium">{title}</span>
          {side && <span className="shrink-0 text-sm font-medium text-primary">{side}</span>}
        </span>
        {detail && <span className="text-sm text-muted-foreground">{detail}</span>}
      </span>
    </label>
  );
}

function TimePicker({
  location,
  zone,
  slot,
  onPick,
  error,
}: {
  location: string;
  zone: string;
  slot: string | null;
  onPick: (slot: string) => void;
  error: string | null;
}) {
  const today = useMemo(() => todayIso(new Date(), zone), [zone]);
  const [from, setFrom] = useState(today);
  const availability = useQuery({
    queryKey: ['booking', 'availability', location, from],
    queryFn: () => bookingApi.availability(location, from, 7),
  });
  const days = availability.data?.days ?? [];
  const [day, setDay] = useState<string | null>(null);
  const firstOpen = days.find((d) => d.slots.length > 0)?.date ?? null;
  const shown = days.find((d) => d.date === day) ?? days.find((d) => d.date === firstOpen);

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-medium">Pick a time</h2>
        <div className="flex gap-1">
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="Earlier days"
            disabled={from <= today}
            onClick={() => {
              setFrom(addDays(from, -7) < today ? today : addDays(from, -7));
              setDay(null);
            }}
          >
            ←
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="Later days"
            onClick={() => {
              setFrom(addDays(from, 7));
              setDay(null);
            }}
          >
            →
          </Button>
        </div>
      </div>
      {availability.isPending ? (
        <Skeleton className="h-32 w-full" />
      ) : availability.isError ? (
        <Alert>{errorMessage(availability.error)}</Alert>
      ) : (
        <>
          <div className="grid grid-cols-7 gap-1" role="group" aria-label="Days">
            {days.map((d) => {
              const open = d.slots.length > 0;
              const selected = shown?.date === d.date;
              return (
                <button
                  key={d.date}
                  type="button"
                  disabled={!open}
                  aria-pressed={selected}
                  aria-label={dayLabel(d.date, { weekday: 'long', day: 'numeric', month: 'long' })}
                  onClick={() => setDay(d.date)}
                  className={cn(
                    'grid justify-items-center gap-0.5 rounded-lg border px-1 py-2 text-xs transition-colors',
                    selected
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'hover:border-primary/60',
                    !open && 'cursor-not-allowed opacity-40',
                  )}
                >
                  <span>{dayLabel(d.date, { weekday: 'short' })}</span>
                  <span className="text-base font-semibold">{dayLabel(d.date, { day: 'numeric' })}</span>
                </button>
              );
            })}
          </div>
          {shown && shown.slots.length > 0 ? (
            <div
              key={shown.date}
              className="gb-step grid grid-cols-3 gap-2 sm:grid-cols-4"
              role="group"
              aria-label="Times"
            >
              {shown.slots.map((s) => (
                <button
                  key={s}
                  type="button"
                  aria-pressed={slot === s}
                  onClick={() => onPick(s)}
                  className={cn(
                    'rounded-lg border px-2 py-2 text-sm font-medium transition-all hover:border-primary/60',
                    slot === s && 'scale-[1.03] border-primary bg-primary text-primary-foreground',
                  )}
                >
                  {timeLabel(s, zone)}
                </button>
              ))}
            </div>
          ) : (
            <p className="rounded-lg bg-muted px-3 py-4 text-center text-sm text-muted-foreground">
              No free times these days. Try later days with →.
            </p>
          )}
          <p className="text-xs text-muted-foreground">Times are the shop&apos;s local time.</p>
        </>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function Summary({ rows }: { rows: [string, string][] }) {
  return (
    <dl className="grid gap-1 rounded-lg bg-muted/50 p-3 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="flex justify-between gap-3">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="text-right font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Booked({
  booked,
  car,
  manyLocations,
}: {
  booked: PublicBooked;
  car: string;
  manyLocations: boolean;
}) {
  return (
    <Card>
      <CardContent className="grid justify-items-center gap-4 py-10 text-center" role="status">
        <svg viewBox="0 0 52 52" className="gb-pop size-16 text-success" aria-hidden="true">
          <circle cx="26" cy="26" r="24" fill="currentColor" opacity="0.15" />
          <path
            className="gb-tick"
            d="M15 27l7 7 15-16"
            fill="none"
            stroke="currentColor"
            strokeWidth="4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">You&apos;re booked!</h1>
          <p className="text-muted-foreground">{whenLabel(booked.startsAt, booked.timeZone)}</p>
        </div>
        <div className="w-full max-w-sm text-left">
          <Summary
            rows={[
              ['Service', booked.serviceName],
              ['Car', car],
              ...(manyLocations ? [['Where', booked.locationName] as [string, string]] : []),
            ]}
          />
        </div>
        <p className="max-w-sm text-sm text-muted-foreground">
          {booked.emailed
            ? `A confirmation is on its way to ${booked.email}.`
            : 'Keep this page for your records: the confirmation e-mail could not be sent just now.'}{' '}
          Need to change it? Reply to the e-mail or call the shop.
        </p>
      </CardContent>
    </Card>
  );
}
