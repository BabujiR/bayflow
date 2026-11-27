import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ManagedBooking } from '@/api/types';
import { rememberClaim } from '@/features/booking/claim';
import { renderApp } from '@/test/render';
import { customer, noSession, problem, server, sessionFor, user } from '@/test/server';

const TOKEN = 'A'.repeat(43);

function managed(overrides: Partial<ManagedBooking> = {}): ManagedBooking {
  return {
    status: 'BOOKED',
    startsAt: '2030-11-19T15:00:00Z',
    timeZone: 'America/New_York',
    durationMinutes: 45,
    locationCode: 'main',
    locationName: 'Main',
    address: '1 Main St',
    manyLocations: false,
    serviceName: 'Oil change',
    car: '2019 Honda Civic',
    shopName: 'Demo Auto',
    firstName: 'Ana',
    canChange: true,
    blocked: 'NONE',
    noticeMinutes: 120,
    reschedulesLeft: 3,
    cancelledByCustomer: false,
    hasAccount: false,
    ...overrides,
  };
}

function serveAvailability() {
  return http.get('*/api/v1/booking/availability', ({ request }) => {
    const from = new URL(request.url).searchParams.get('from') ?? '2030-11-18';
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(`${from}T12:00:00Z`);
      d.setUTCDate(d.getUTCDate() + i);
      const date = d.toISOString().slice(0, 10);
      return { date, slots: i === 0 ? [`${date}T16:00:00Z`] : [] };
    });
    return HttpResponse.json({ location: 'main', timeZone: 'America/New_York', days });
  });
}

afterEach(() => sessionStorage.clear());

describe("an online booking's private link (/book/manage)", () => {
  it('still shows the booking when the "no sign-in" answer comes after it', async () => {
    server.use(
      // A visitor without a sign-in: the session check fails only after the booking has loaded.
      http.post('*/api/v1/auth/refresh', async () => {
        await delay(300);
        return problem(401, 'invalid-refresh-token', 'Please sign in again.');
      }),
      http.post('*/api/v1/booking/manage', () => HttpResponse.json(managed())),
    );
    renderApp(`/book/manage?token=${TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: 'Your booking' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    // After the session check has failed, the booking is still there.
    await new Promise((resolve) => setTimeout(resolve, 500));
    expect(screen.getByRole('heading', { name: 'Your booking' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Loading')).not.toBeInTheDocument();
  });

  it('shows the booking and moves it to another free time', async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.post('*/api/v1/booking/manage', () => HttpResponse.json(managed())),
      serveAvailability(),
      http.post('*/api/v1/booking/manage/reschedule', async ({ request }) => {
        sent = await request.json();
        const body = sent as { startsAt: string };
        return HttpResponse.json(managed({ startsAt: body.startsAt, reschedulesLeft: 2 }));
      }),
    );
    renderApp(`/book/manage?token=${TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: 'Your booking' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Hi Ana, here are your visit's details.")).toBeInTheDocument();
    expect(screen.getByText('2019 Honda Civic')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create an account' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Change time' }));
    const times = await screen.findByRole('group', { name: 'Times' });
    await userEvent.click(within(times).getAllByRole('button')[0]);
    await userEvent.click(screen.getByRole('button', { name: /^Move to / }));

    expect(await screen.findByText(/We've e-mailed you the new time/)).toBeInTheDocument();
    expect(sent).toMatchObject({ token: TOKEN });
    expect(String((sent as { startsAt: string }).startsAt)).toMatch(/T16:00:00Z$/);
  });

  it('cancels with a reason', async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.post('*/api/v1/booking/manage', () => HttpResponse.json(managed())),
      http.post('*/api/v1/booking/manage/cancel', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(
          managed({ status: 'CANCELLED', canChange: false, blocked: 'CANCELLED', cancelledByCustomer: true }),
        );
      }),
    );
    renderApp(`/book/manage?token=${TOKEN}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel booking' }, { timeout: 10_000 }));
    await userEvent.type(screen.getByLabelText('Reason (optional)'), 'Sold the car');
    await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }));

    expect(await screen.findByText('Your booking is cancelled. The shop has been told.')).toBeInTheDocument();
    expect(screen.getByText('Cancelled')).toBeInTheDocument();
    expect(screen.getByText('You cancelled this booking.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Book another visit' })).toHaveAttribute('href', '/book');
    expect(sent).toEqual({ token: TOKEN, reason: 'Sold the car' });
  });

  it('says when it is too late to change online', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/booking/manage', () =>
        HttpResponse.json(managed({ canChange: false, blocked: 'TOO_LATE', hasAccount: true })),
      ),
    );
    renderApp(`/book/manage?token=${TOKEN}`);

    expect(
      await screen.findByText(/Changes online close 2 hours before the visit/, {}, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Change time' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Create an account' })).not.toBeInTheDocument();
  });

  it('explains an old or wrong link', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/booking/manage', () => problem(404, 'booking-link-invalid', 'Not valid')),
    );
    renderApp(`/book/manage?token=${TOKEN}`);

    expect(
      await screen.findByText(/Use the link in your latest e-mail from the shop/, {}, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });
});

describe('claiming an online booking', () => {
  it('adds it to the account once the customer is signed in', async () => {
    let claimed: unknown;
    rememberClaim(TOKEN);
    server.use(
      sessionFor(user('CUSTOMER')),
      http.post('*/api/v1/customers/me/claim', async ({ request }) => {
        claimed = await request.json();
        return HttpResponse.json(customer());
      }),
    );
    renderApp('/');

    await vi.waitFor(() => expect(claimed).toEqual({ token: TOKEN }), { timeout: 10_000 });
    await vi.waitFor(() => expect(sessionStorage.getItem('bayflow.booking-claim')).toBeNull());
  });
});
