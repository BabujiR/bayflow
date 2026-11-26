import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { bookingServicesApi } from '@/api/endpoints';
import type { BookingServiceItem, BookingServiceRequest, PriceKind } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useLocations } from '@/features/locations/use-locations';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { useAction } from '@/lib/use-action';

export const BOOKING_SERVICES_KEY = ['booking-services'] as const;

const PRICE_KINDS: Record<PriceKind, string> = {
  NONE: "Don't show a price",
  FROM: 'From a price, e.g. "from $54"',
  FIXED: 'Fixed price, e.g. "$79"',
  FREE: 'Free',
  QUOTE: 'Ask for a quote',
};

const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240, 480];

/** "$54" for whole amounts, "$54.50" otherwise: how customers read a price. */
function money(amount: number | null, currency: string): string {
  if (amount === null) {
    return '';
  }
  const cents = Number.isInteger(amount) ? 0 : 2;
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: cents,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** How the price shows to customers, e.g. "from $54". Empty when no price is shown. */
export function priceLabel(s: Pick<BookingServiceItem, 'priceKind' | 'price'>, currency: string): string {
  switch (s.priceKind) {
    case 'FROM':
      return `from ${money(s.price, currency)}`;
    case 'FIXED':
      return money(s.price, currency);
    case 'FREE':
      return 'Free';
    case 'QUOTE':
      return 'Ask for a quote';
    default:
      return '';
  }
}

/** "45 min", "1.5 h", "8 h". */
export function durationLabel(minutes: number | null): string {
  if (minutes === null) {
    return '';
  }
  return minutes < 60 ? `${minutes} min` : `${Number((minutes / 60).toFixed(1))} h`;
}

/**
 * Settings → Online booking (admins): the services customers can book, with the price and the
 * typical time they see, in the order they see them. "Something else" is always offered.
 */
export function BookingServicesSection() {
  const services = useQuery({ queryKey: BOOKING_SERVICES_KEY, queryFn: bookingServicesApi.list });
  const workshop = useWorkshop();
  const locations = useLocations();
  const currency = workshop.data?.currency ?? 'USD';
  const active = (locations.data ?? []).filter((l) => l.status === 'ACTIVE');
  const locationName = (id: string | null) => active.find((l) => l.id === id)?.name ?? 'an archived location';
  const [editing, setEditing] = useState<BookingServiceItem | 'new' | null>(null);
  const reorder = useAction((ids: string[]) => bookingServicesApi.reorder(ids), {
    invalidate: [[...BOOKING_SERVICES_KEY]],
  });

  return (
    <section aria-labelledby="booking-services-heading" className="grid max-w-3xl gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="booking-services-heading" className="text-xl font-semibold tracking-tight">
            Online booking
          </h2>
          <p className="text-sm text-muted-foreground">
            The services customers can pick when they book online, with the price and time they see, in this
            order. Hidden ones stay here for later.
          </p>
        </div>
        <Button variant="outline" onClick={() => setEditing('new')}>
          Add service
        </Button>
      </div>
      <QueryState query={services}>
        {(list) => (
          <Card>
            <CardContent className="grid gap-2 pt-6">
              <ol className="grid gap-2" aria-label="Bookable services">
                {list.map((s, index) => {
                  const details = [
                    priceLabel(s, currency),
                    durationLabel(s.durationMinutes) && `about ${durationLabel(s.durationMinutes)}`,
                    s.locationId && `${locationName(s.locationId)} only`,
                  ].filter(Boolean);
                  const move = (to: number) => {
                    const ids = list.map((x) => x.id);
                    ids.splice(to, 0, ...ids.splice(index, 1));
                    reorder.mutate(ids);
                  };
                  return (
                    <li
                      key={s.id}
                      className="flex flex-wrap items-center justify-between gap-2 border-b pb-2 last:border-0 last:pb-0"
                    >
                      <div className="min-w-0 flex-1 text-sm">
                        <span className="font-medium">{s.name}</span>{' '}
                        {!s.active && <Badge variant="outline">Hidden</Badge>}
                        <div className="text-muted-foreground">
                          {details.length ? details.join(' · ') : 'No price or time shown'}
                        </div>
                      </div>
                      <div className="flex gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label={`Move ${s.name} up`}
                          disabled={index === 0 || reorder.isPending}
                          onClick={() => move(index - 1)}
                        >
                          ↑
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label={`Move ${s.name} down`}
                          disabled={index === list.length - 1 || reorder.isPending}
                          onClick={() => move(index + 1)}
                        >
                          ↓
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`Edit ${s.name}`}
                          onClick={() => setEditing(s)}
                        >
                          Edit
                        </Button>
                      </div>
                    </li>
                  );
                })}
                <li className="flex items-center justify-between gap-2 pt-1 text-sm">
                  <div>
                    <span className="font-medium">Something else</span>{' '}
                    <span className="text-muted-foreground">· always offered, the customer describes it</span>
                  </div>
                </li>
              </ol>
            </CardContent>
          </Card>
        )}
      </QueryState>
      {editing && (
        <ServiceDialog
          service={editing === 'new' ? null : editing}
          currency={currency}
          locations={active.map((l) => ({ id: l.id, name: l.name }))}
          onClose={() => setEditing(null)}
        />
      )}
    </section>
  );
}

function ServiceDialog({
  service,
  currency,
  locations,
  onClose,
}: {
  service: BookingServiceItem | null;
  currency: string;
  locations: { id: string; name: string }[];
  onClose: () => void;
}) {
  const [name, setName] = useState(service?.name ?? '');
  const [description, setDescription] = useState(service?.description ?? '');
  const [priceKind, setPriceKind] = useState<PriceKind>(service?.priceKind ?? 'NONE');
  const [price, setPrice] = useState(service?.price != null ? String(service.price) : '');
  const [duration, setDuration] = useState(
    service?.durationMinutes != null ? String(service.durationMinutes) : '',
  );
  const [locationId, setLocationId] = useState(service?.locationId ?? '');
  const [active, setActive] = useState(service?.active ?? true);
  const needsPrice = priceKind === 'FROM' || priceKind === 'FIXED';
  const priceOk =
    !needsPrice ||
    (/^\d{1,5}(\.\d{1,2})?$/.test(price.trim()) && Number(price) >= 0 && Number(price) <= 99999.99);
  const valid = name.trim() !== '' && priceOk;

  const save = useAction(
    () => {
      const body: BookingServiceRequest = {
        name: name.trim(),
        description: description.trim() || null,
        priceKind,
        price: needsPrice ? Number(price) : null,
        durationMinutes: duration === '' ? null : Number(duration),
        locationId: locationId || null,
        active,
        version: service?.version,
      };
      return service ? bookingServicesApi.update(service.id, body) : bookingServicesApi.create(body);
    },
    {
      success: (saved) => `${saved.name} saved`,
      invalidate: [[...BOOKING_SERVICES_KEY]],
      onSuccess: onClose,
    },
  );
  const preview = [
    priceLabel({ priceKind, price: needsPrice && priceOk ? Number(price) : null }, currency),
    durationLabel(duration === '' ? null : Number(duration)) &&
      `about ${durationLabel(duration === '' ? null : Number(duration))}`,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={service ? `Edit ${service.name}` : 'New service'}
        description="What customers see when they book online."
      >
        <form
          noValidate
          aria-label={service ? `Edit ${service.name}` : 'New service'}
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              save.mutate(undefined);
            }
          }}
        >
          <Field label="Name" error={name.trim() === '' && service ? 'Enter a name' : undefined}>
            <Input
              value={name}
              maxLength={80}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Oil change"
            />
          </Field>
          <Field label="Short description" hint="Optional, one line.">
            <Input
              value={description}
              maxLength={200}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Oil and filter, fluids topped up"
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Price shown">
              <Select value={priceKind} onChange={(e) => setPriceKind(e.target.value as PriceKind)}>
                {Object.entries(PRICE_KINDS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
            {needsPrice && (
              <Field
                label={`Price (${currency})`}
                error={price !== '' && !priceOk ? 'Enter an amount, e.g. 54' : undefined}
              >
                <Input
                  inputMode="decimal"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  placeholder="54"
                />
              </Field>
            )}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Typical time">
              <Select value={duration} onChange={(e) => setDuration(e.target.value)}>
                <option value="">Don&apos;t show</option>
                {DURATIONS.map((m) => (
                  <option key={m} value={m}>
                    About {durationLabel(m)}
                  </option>
                ))}
              </Select>
            </Field>
            {locations.length > 1 && (
              <Field label="Offered at">
                <Select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
                  <option value="">Every location</option>
                  {locations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} only
                    </option>
                  ))}
                </Select>
              </Field>
            )}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Show on the booking page
          </label>
          <p className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground" aria-live="polite">
            Customers see: <strong className="text-foreground">{name.trim() || 'Service name'}</strong>
            {preview ? ` · ${preview}` : ''}
          </p>
          <Button type="submit" className="justify-self-start" disabled={!valid || save.isPending}>
            {save.isPending ? 'Saving…' : 'Save service'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
