import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { renderApp } from '@/test/render';
import {
  appointment,
  bookingHours,
  bookingOptions,
  noSession,
  pageOf,
  problem,
  server,
  sessionFor,
  user,
} from '@/test/server';

/** Seven days from `from`, the first two with free times. */
function serveAvailability() {
  return http.get('*/api/v1/booking/availability', ({ request }) => {
    const url = new URL(request.url);
    const from = url.searchParams.get('from') ?? '2026-11-16';
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(`${from}T12:00:00Z`);
      d.setUTCDate(d.getUTCDate() + i);
      const date = d.toISOString().slice(0, 10);
      return { date, slots: i < 2 ? [`${date}T15:00:00Z`, `${date}T16:00:00Z`] : [] };
    });
    return HttpResponse.json({
      location: url.searchParams.get('location'),
      timeZone: 'America/New_York',
      days,
    });
  });
}

async function chooseFirstTime() {
  const times = await screen.findByRole('group', { name: 'Times' });
  await userEvent.click(within(times).getAllByRole('button')[0]);
}

async function fillDetails(phone = '') {
  await userEvent.type(screen.getByLabelText('First name'), 'Ana');
  await userEvent.type(screen.getByLabelText('Last name'), 'Silva');
  await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com');
  if (phone) {
    await userEvent.type(screen.getByLabelText('Phone (optional)'), phone);
  }
}

describe('public booking page (/book)', () => {
  it('books a visit without an account, step by step', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      noSession,
      http.get('*/api/v1/booking/options', () => HttpResponse.json(bookingOptions())),
      serveAvailability(),
      http.post('*/api/v1/booking/appointments', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          {
            startsAt: sent.startsAt,
            timeZone: 'America/New_York',
            locationName: 'Main',
            serviceName: 'Oil change',
            email: 'ana@example.com',
            emailed: true,
          },
          { status: 201 },
        );
      }),
    );
    renderApp('/book');

    expect(
      await screen.findByRole('heading', { name: 'Book a visit' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    // One location: no location question.
    expect(screen.queryByLabelText('Location')).not.toBeInTheDocument();
    expect(screen.getByText('from $54')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(screen.getByText('Choose a service')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: /Oil change/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    // The car: year, make and model.
    await userEvent.selectOptions(await screen.findByLabelText('Year'), '2019');
    await userEvent.type(screen.getByLabelText('Make'), 'Honda');
    await userEvent.type(screen.getByLabelText('Model'), 'Civic');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await chooseFirstTime();
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    // A phone number brings the text-message consent and its privacy note.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    await fillDetails('555 010 0199');
    expect(screen.getByText(/never shared with or sold to third parties/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /Text me about this visit/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Book my visit' }));

    expect(await screen.findByRole('heading', { name: "You're booked!" })).toBeInTheDocument();
    expect(screen.getByText(/A confirmation is on its way to ana@example.com/)).toBeInTheDocument();
    expect(screen.getByText('2019 Honda Civic')).toBeInTheDocument();
    expect(sent).toMatchObject({
      location: 'main',
      service: 'oil-change',
      description: null,
      vin: null,
      year: 2019,
      make: 'Honda',
      model: 'Civic',
      firstName: 'Ana',
      lastName: 'Silva',
      email: 'ana@example.com',
      phone: '555 010 0199',
      smsConsent: true,
      website: '',
    });
    expect(String(sent?.startsAt)).toMatch(/T15:00:00Z$/);
  });

  it("takes the location from the link and offers only that location's services", async () => {
    server.use(
      noSession,
      http.get('*/api/v1/booking/options', () =>
        HttpResponse.json(
          bookingOptions({
            locations: [
              { code: 'main', name: 'Main', address: '1 Main St', timeZone: 'America/New_York' },
              { code: 'east', name: 'East', address: '9 East Ave', timeZone: 'America/New_York' },
            ],
            services: [
              {
                code: 'tires',
                name: 'Tire swap',
                description: null,
                priceKind: 'FIXED',
                price: 40,
                durationMinutes: null,
                locations: ['main'],
              },
              ...bookingOptions().services,
            ],
          }),
        ),
      ),
    );
    renderApp('/book?location=east');

    const select = await screen.findByLabelText('Location', {}, { timeout: 10_000 });
    expect(select).toHaveValue('east');
    expect(screen.getByText('9 East Ave')).toBeInTheDocument();
    expect(screen.queryByText('Tire swap')).not.toBeInTheDocument();
    await userEvent.selectOptions(select, 'main');
    expect(screen.getByText('Tire swap')).toBeInTheDocument();
  });

  it('describes "Something else", takes a VIN, and goes back to the times when one was just taken', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      noSession,
      http.get('*/api/v1/booking/options', () => HttpResponse.json(bookingOptions())),
      serveAvailability(),
      http.post('*/api/v1/booking/appointments', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return problem(409, 'slot-taken', 'That time was just taken. Choose another.');
      }),
    );
    renderApp('/book');

    await userEvent.click(await screen.findByRole('radio', { name: /Something else/ }, { timeout: 10_000 }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(screen.getByText('Tell us briefly what your car needs')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("What's going on?"), 'Squeaky brakes');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await userEvent.click(await screen.findByRole('tab', { name: 'VIN' }));
    await userEvent.type(screen.getByLabelText('VIN'), 'NOT-A-VIN');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(screen.getByText(/A VIN has 17 letters and digits/)).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('VIN'));
    await userEvent.type(screen.getByLabelText('VIN'), '1hgcv1f3xla012345');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await chooseFirstTime();
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await fillDetails();
    // No phone: no consent asked.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Book my visit' }));

    expect(await screen.findByText('That time was just taken. Choose another.')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Times' })).toBeInTheDocument();
    expect(sent).toMatchObject({
      service: null,
      description: 'Squeaky brakes',
      vin: '1HGCV1F3XLA012345',
      year: null,
      phone: null,
      smsConsent: false,
    });
  });

  it('says so when the workshop is suspended', async () => {
    server.use(
      noSession,
      http.get('*/api/v1/booking/options', () =>
        problem(403, 'workshop-suspended', "This workshop isn't taking online bookings right now."),
      ),
    );
    renderApp('/book');

    expect(
      await screen.findByText(/isn't taking online bookings right now/, {}, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });
});

describe('Settings → Online booking: booking hours', () => {
  it("saves a location's weekly hours and slot rules", async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.put('*/api/v1/booking-services/hours/:locationId', async ({ request, params }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(bookingHours({ locationId: String(params.locationId), version: 1 }));
      }),
    );
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Booking hours' }, { timeout: 10_000 });
    expect(within(form).getByLabelText('Open on Saturday')).not.toBeChecked();
    await userEvent.click(within(form).getByLabelText('Open on Saturday'));
    fireEvent.change(within(form).getByLabelText('Saturday closes'), { target: { value: '12:00' } });
    await userEvent.selectOptions(within(form).getByLabelText('Slot length'), '60');
    await userEvent.clear(within(form).getByLabelText('Cars per slot'));
    await userEvent.type(within(form).getByLabelText('Cars per slot'), '3');
    await userEvent.click(within(form).getByRole('button', { name: 'Save booking hours' }));

    await vi.waitFor(() =>
      expect(sent).toMatchObject({ slotMinutes: 60, carsPerSlot: 3, minNoticeMinutes: 120, version: 0 }),
    );
    expect((sent?.days as unknown[])[5]).toEqual({ opens: '08:00', closes: '12:00' });
    expect((sent?.days as unknown[])[6]).toEqual({ opens: null, closes: null });
  });
});

describe('check-in of a car booked online', () => {
  it('asks for the VIN and plate the job card needs', async () => {
    let body: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/appointments', () =>
        HttpResponse.json(
          pageOf([
            appointment({
              id: 'a-online',
              status: 'ARRIVED',
              bookedOnline: true,
              serviceName: 'Oil change',
              vehicle: {
                id: 'v9',
                vin: null as unknown as string,
                registration: null as unknown as string,
                make: 'Honda',
                model: 'Civic',
                year: 2019,
              },
            }),
          ]),
        ),
      ),
      http.post('*/api/v1/appointments/a-online/check-in', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(appointment({ id: 'a-online', status: 'CHECKED_IN', jobCardId: 'j1' }));
      }),
    );
    renderApp('/board');

    const arrived = await screen.findByRole('region', { name: 'Arrived' }, { timeout: 10_000 });
    expect(within(arrived).getByText('Online')).toBeInTheDocument();
    expect(within(arrived).getByText(/Oil change/)).toBeInTheDocument();
    await userEvent.click(within(arrived).getByRole('button', { name: 'Check in' }));
    const dialog = await screen.findByRole('dialog', { name: 'Check in vehicle' });
    await userEvent.type(within(dialog).getByLabelText('Odometer reading (miles)'), '42000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check in' }));
    expect(await within(dialog).findByText(/A VIN has 17 letters/)).toBeInTheDocument();
    expect(within(dialog).getByText('Enter the plate')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('VIN'), '1hgcv1f3xla012345');
    await userEvent.type(within(dialog).getByLabelText('Plate'), '8xyz214');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check in' }));
    await vi.waitFor(() =>
      expect(body).toEqual({ mileage: 42000, vin: '1HGCV1F3XLA012345', registration: '8XYZ214' }),
    );
  });
});
