import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test/render';
import { appointment, customer, pageOf, problem, server, sessionFor, user, vehicle } from '@/test/server';

describe('customer profile', () => {
  it('asks a new customer for their profile before anything else', async () => {
    let created = false;
    let sent: unknown;
    server.use(
      sessionFor(user('CUSTOMER', 'newbie')),
      http.get('*/api/v1/customers/me', () =>
        created ? HttpResponse.json(customer({ name: 'New Bie' })) : problem(404, 'not-found', 'Customer profile was not found.'),
      ),
      http.post('*/api/v1/customers/me', async ({ request }) => {
        sent = await request.json();
        created = true;
        return HttpResponse.json(customer({ name: 'New Bie' }), { status: 201 });
      }),
    );
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Complete your profile' })).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveValue('newbie@bayflow.local');

    await userEvent.type(screen.getByLabelText('Full name'), 'New Bie');
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }));

    expect(await screen.findByRole('heading', { name: 'Welcome, newbie' })).toBeInTheDocument();
    expect(sent).toEqual({ name: 'New Bie', email: 'newbie@bayflow.local' });
  });
});

describe('booking an appointment', () => {
  it('checks the form, then sends the booking in UTC and returns to the list', async () => {
    let booked: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('CUSTOMER')),
      http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([vehicle()]))),
      http.post('*/api/v1/appointments', async ({ request }) => {
        booked = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(appointment(), { status: 201 });
      }),
    );
    const { router } = renderApp('/appointments/new');

    await userEvent.click(await screen.findByRole('button', { name: 'Book appointment' }));
    expect(await screen.findByText('Choose a vehicle')).toBeInTheDocument();
    expect(screen.getByText('Choose a date and time')).toBeInTheDocument();

    // Wait for the vehicle list to load before choosing from it.
    await screen.findByRole('option', { name: /BFL 1234/ });
    await userEvent.selectOptions(screen.getByLabelText('Vehicle'), 'v0000000-0000-0000-0000-000000000001');
    fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2030-01-15T10:30' } });
    await userEvent.selectOptions(screen.getByLabelText('Service'), 'BRAKES');
    await userEvent.type(screen.getByLabelText('Notes for the workshop'), 'Squeal when braking');
    await userEvent.click(screen.getByRole('button', { name: 'Book appointment' }));

    await screen.findByRole('heading', { name: 'Appointments' });
    expect(router.state.location.pathname).toBe('/appointments');
    expect(booked).toEqual({
      vehicleId: 'v0000000-0000-0000-0000-000000000001',
      scheduledAt: new Date('2030-01-15T10:30').toISOString(),
      durationMinutes: 60,
      serviceType: 'BRAKES',
      notes: 'Squeal when braking',
    });
  });

  it('shows a double booking on the time field', async () => {
    server.use(
      sessionFor(user('CUSTOMER')),
      http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([vehicle()]))),
      http.post('*/api/v1/appointments', () =>
        problem(409, 'double-booking', 'This vehicle already has an appointment that overlaps the requested time.'),
      ),
    );
    renderApp('/appointments/new');

    // Wait for the vehicle list to load before choosing from it.
    await screen.findByRole('option', { name: /BFL 1234/ });
    await userEvent.selectOptions(screen.getByLabelText('Vehicle'), 'v0000000-0000-0000-0000-000000000001');
    fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2030-01-15T10:30' } });
    await userEvent.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(await screen.findByText(/already has an appointment that overlaps/)).toBeInTheDocument();
  });

  it('lets a customer cancel their booking after confirming', async () => {
    let cancelled = false;
    server.use(
      sessionFor(user('CUSTOMER')),
      http.get('*/api/v1/appointments', () =>
        HttpResponse.json(pageOf([appointment({ status: cancelled ? 'CANCELLED' : 'BOOKED' })])),
      ),
      http.post('*/api/v1/appointments/:id/cancel', () => {
        cancelled = true;
        return HttpResponse.json(appointment({ status: 'CANCELLED' }));
      }),
    );
    renderApp('/appointments');

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel appointment' }));

    expect(await screen.findByText('Cancelled', { selector: 'span' })).toBeInTheDocument();
    expect(cancelled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
  });
});
