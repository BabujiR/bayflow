import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { bookingApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { ManagedBooking } from '@/api/types';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/textarea';

import { AccountLink } from './GuestBookingPage';
import { BookingShell, PublicQueryState, Summary, TimePicker, whenLabel } from './booking-ui';

const manageKey = (token: string) => ['booking', 'manage', token];

/** "2 hours", "1 day". */
function noticeLabel(minutes: number): string {
  if (minutes % 1440 === 0) {
    return minutes === 1440 ? '1 day' : `${minutes / 1440} days`;
  }
  if (minutes % 60 === 0) {
    return minutes === 60 ? '1 hour' : `${minutes / 60} hours`;
  }
  return `${minutes} minutes`;
}

/**
 * An online booking's private link (`/book/manage?token=…`, from its e-mails): no sign-in.
 * The customer sees the booking and, until the shop's notice before the visit, moves it to
 * another free time or cancels it.
 */
export function ManageBookingPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const booking = useQuery({
    queryKey: manageKey(token),
    queryFn: () => bookingApi.manage(token),
    enabled: token !== '',
    retry: (count, error) => !(error instanceof ApiError && error.isClientError) && count < 2,
  });

  return (
    <BookingShell shopName={booking.data?.shopName}>
      {token === '' ? (
        <Alert>This link is missing its code. Use the link in your e-mail from the shop.</Alert>
      ) : (
        <PublicQueryState
          query={booking}
          errorText={(e) =>
            e.code === 'booking-link-invalid'
              ? "This link isn't valid any more. Use the link in your latest e-mail from the shop."
              : undefined
          }
        >
          {(b) => <Manage token={token} booking={b} />}
        </PublicQueryState>
      )}
    </BookingShell>
  );
}

function Manage({ token, booking }: { token: string; booking: ManagedBooking }) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'view' | 'move' | 'cancel'>('view');
  const [slot, setSlot] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const update = (b: ManagedBooking, message: string) => {
    queryClient.setQueryData(manageKey(token), b);
    setMode('view');
    setSlot(null);
    setNotice(message);
  };
  const move = useMutation({
    mutationFn: () => bookingApi.reschedule(token, slot!),
    onSuccess: (b) =>
      update(b, `Moved to ${whenLabel(b.startsAt, b.timeZone)}. We've e-mailed you the new time.`),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'slot-taken') {
        setSlot(null);
        void queryClient.invalidateQueries({ queryKey: ['booking', 'availability'] });
      }
    },
  });
  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(token, reason.trim() || null),
    onSuccess: (b) => update(b, 'Your booking is cancelled. The shop has been told.'),
  });
  const cancelled = booking.status === 'CANCELLED' || booking.status === 'NO_SHOW';

  return (
    <div className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Your booking</h1>
        <p className="text-sm text-muted-foreground">
          Hi {booking.firstName}, here are your visit&apos;s details.
        </p>
      </div>
      {notice && (
        <div
          role="status"
          className="gb-step rounded-lg border border-success/30 bg-success/10 px-4 py-3 text-sm"
        >
          {notice}
        </div>
      )}
      <Card>
        <CardContent className="grid gap-5 pt-6">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className={cancelled ? 'text-lg font-semibold line-through' : 'text-lg font-semibold'}>
              {whenLabel(booking.startsAt, booking.timeZone)}
            </p>
            {cancelled ? (
              <Badge variant="destructive">Cancelled</Badge>
            ) : booking.status === 'CONFIRMED' ? (
              <Badge variant="success">Confirmed</Badge>
            ) : booking.status === 'BOOKED' ? (
              <Badge>Booked</Badge>
            ) : (
              <Badge variant="secondary">At the shop</Badge>
            )}
          </div>
          <Summary
            rows={[
              ['Service', booking.serviceName],
              ['Car', booking.car],
              ...(booking.locationName && (booking.manyLocations || booking.address)
                ? [
                    [
                      'Where',
                      booking.manyLocations
                        ? [booking.locationName, booking.address].filter(Boolean).join(', ')
                        : (booking.address ?? ''),
                    ] as [string, string],
                  ]
                : []),
            ]}
          />

          {mode === 'view' && booking.canChange && (
            <div className="flex flex-wrap gap-2">
              <Button
                onClick={() => setMode('move')}
                disabled={booking.reschedulesLeft === 0}
                title={
                  booking.reschedulesLeft === 0 ? 'Moved too many times: please call the shop' : undefined
                }
              >
                Change time
              </Button>
              <Button variant="outline" onClick={() => setMode('cancel')}>
                Cancel booking
              </Button>
            </div>
          )}
          {mode === 'view' && booking.canChange && booking.reschedulesLeft === 0 && (
            <p className="text-sm text-muted-foreground">
              This booking was moved a few times already. To change it again, please call the shop.
            </p>
          )}
          {mode === 'view' && !booking.canChange && <BlockedNote booking={booking} />}

          {mode === 'move' && booking.locationCode && (
            <div className="gb-step grid gap-4 border-t pt-5">
              <TimePicker
                location={booking.locationCode}
                zone={booking.timeZone}
                slot={slot}
                onPick={setSlot}
                error={move.isError ? errorMessage(move.error) : null}
              />
              <div className="flex justify-between gap-2">
                <Button variant="ghost" onClick={() => setMode('view')}>
                  Back
                </Button>
                <Button disabled={slot === null || move.isPending} onClick={() => move.mutate()}>
                  {move.isPending
                    ? 'Moving…'
                    : slot
                      ? `Move to ${whenLabel(slot, booking.timeZone)}`
                      : 'Choose a time'}
                </Button>
              </div>
            </div>
          )}

          {mode === 'cancel' && (
            <form
              className="gb-step grid gap-4 border-t pt-5"
              aria-label="Cancel booking"
              onSubmit={(e) => {
                e.preventDefault();
                cancel.mutate();
              }}
            >
              <p className="font-medium">Cancel this visit?</p>
              <Field
                label="Reason (optional)"
                hint="Helps the shop. For example: sold the car, fixed it myself."
              >
                <Textarea
                  rows={2}
                  maxLength={300}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </Field>
              {cancel.isError && <Alert>{errorMessage(cancel.error)}</Alert>}
              <div className="flex justify-between gap-2">
                <Button type="button" variant="ghost" onClick={() => setMode('view')}>
                  Keep my booking
                </Button>
                <Button type="submit" variant="destructive" disabled={cancel.isPending}>
                  {cancel.isPending ? 'Cancelling…' : 'Yes, cancel it'}
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
      {cancelled && (
        <p className="text-center text-sm">
          <Link to="/book" className="font-medium text-primary underline-offset-4 hover:underline">
            Book another visit
          </Link>
        </p>
      )}
      {!booking.hasAccount && (
        <div className="text-center">
          <AccountLink token={token} />
        </div>
      )}
    </div>
  );
}

function BlockedNote({ booking: b }: { booking: ManagedBooking }) {
  const text: Record<ManagedBooking['blocked'], string> = {
    NONE: '',
    CANCELLED: b.cancelledByCustomer ? 'You cancelled this booking.' : 'The shop cancelled this booking.',
    ARRIVED: "You're at the shop: this visit can't be changed here any more.",
    PAST: 'This visit has passed.',
    TOO_LATE: `Changes online close ${noticeLabel(b.noticeMinutes)} before the visit. Please call the shop.`,
    LOCATION_CLOSED: 'Please call the shop to change this booking.',
  };
  return <p className="text-sm text-muted-foreground">{text[b.blocked]}</p>;
}
