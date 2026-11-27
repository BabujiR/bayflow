import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { DecodedVin } from '@/api/types';
import { renderApp } from '@/test/render';
import { appointment, customer, jobCard, pageOf, server, sessionFor, user, vehicle } from '@/test/server';

const CUSTOMER_ID = 'c0000000-0000-0000-0000-000000000001';
const CARD_ID = 'j0000000-0000-0000-0000-000000000001';
const APPOINTMENT_ID = 'a0000000-0000-0000-0000-000000000001';

function decoded(overrides: Partial<DecodedVin> = {}): DecodedVin {
  return {
    vin: '1HGCM82633A004352',
    year: 2003,
    make: 'Honda',
    model: 'Accord',
    trim: 'EX',
    engine: '3.0L 6-cyl Gasoline',
    bodyClass: 'Coupe',
    warnings: [],
    ...overrides,
  };
}

/** An estimate's job card (not visited yet) and its ESTIMATE appointment. */
function estimateCard(status: 'ESTIMATE' | 'BOOKED' = 'ESTIMATE') {
  server.use(
    http.get('*/api/v1/job-cards/:id', () =>
      HttpResponse.json(
        jobCard({
          arrivedAt: null,
          status: 'INSPECTED',
          findings: [
            {
              id: 'f1',
              description: "Customer's request: Brakes squeal",
              severity: 'LOW',
              recordedBy: 'advisor1',
              createdAt: '2026-11-10T14:00:00Z',
            },
          ],
        }),
      ),
    ),
    http.get('*/api/v1/appointments/:id', () =>
      HttpResponse.json(appointment({ status, scheduledAt: '2030-01-15T15:00:00Z' })),
    ),
  );
}

describe('quick estimates', () => {
  it('starts an estimate for a customer and opens its job card', async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers/:id', () => HttpResponse.json(customer({ id: CUSTOMER_ID }))),
      http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([vehicle()]))),
      http.post('*/api/v1/appointments/estimates', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(appointment({ status: 'ESTIMATE', jobCardId: CARD_ID }), { status: 201 });
      }),
    );
    estimateCard();
    const { router } = renderApp(`/estimates/new?customerId=${CUSTOMER_ID}`);

    // The select is there before the customer's cars have loaded: wait for the car itself.
    await screen.findByRole('option', { name: /BFL 1234/ });
    await userEvent.selectOptions(screen.getByLabelText('Vehicle'), 'v0000000-0000-0000-0000-000000000001');
    await userEvent.type(screen.getByLabelText('What the customer is asking for'), 'Brakes squeal');
    await userEvent.click(screen.getByRole('button', { name: 'Start estimate' }));

    expect(await screen.findByText('Estimate · not scheduled yet')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/job-cards/${CARD_ID}`);
    expect(sent).toEqual({ vehicleId: 'v0000000-0000-0000-0000-000000000001', request: 'Brakes squeal' });
    expect(screen.getByText("Customer's request: Brakes squeal")).toBeInTheDocument();
  });

  it('fills in a new car from its VIN without overwriting what was typed', async () => {
    const lookups: string[] = [];
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers/:id', () => HttpResponse.json(customer({ id: CUSTOMER_ID }))),
      http.get('*/api/v1/vehicles/decode', ({ request }) => {
        lookups.push(new URL(request.url).searchParams.get('vin') ?? '');
        return HttpResponse.json(decoded());
      }),
    );
    renderApp(`/estimates/new?customerId=${CUSTOMER_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Add a vehicle' }));
    await userEvent.type(screen.getByLabelText('Model'), 'Accord Coupe');
    await userEvent.type(screen.getByLabelText('VIN'), '1hgcm82633a004352');

    expect(await screen.findByText('2003 Honda Accord EX · 3.0L 6-cyl Gasoline')).toBeInTheDocument();
    expect(lookups).toEqual(['1HGCM82633A004352']);
    expect(screen.getByLabelText('Make')).toHaveValue('Honda');
    expect(screen.getByLabelText('Year')).toHaveValue('2003');
    expect(screen.getByLabelText('Trim (optional)')).toHaveValue('EX');
    // Typed first, so kept; the decoded model is one click away.
    expect(screen.getByLabelText('Model')).toHaveValue('Accord Coupe');
    await userEvent.click(screen.getByRole('button', { name: 'Use decoded details' }));
    expect(screen.getByLabelText('Model')).toHaveValue('Accord');
    expect(screen.queryByRole('button', { name: 'Use decoded details' })).not.toBeInTheDocument();
  });

  it('books the visit when the customer goes ahead', async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.post('*/api/v1/appointments/:id/schedule', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(appointment({ status: 'BOOKED' }));
      }),
    );
    estimateCard();
    renderApp(`/job-cards/${CARD_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Schedule the work' }));
    fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2030-01-15T10:00' } });
    await userEvent.click(screen.getByRole('button', { name: 'Book the visit' }));

    await vi.waitFor(() =>
      expect(sent).toEqual({ scheduledAt: '2030-01-15T15:00:00.000Z', durationMinutes: 60 }),
    );
  });

  it('closes an estimate the customer won’t go ahead with', async () => {
    const calls: string[] = [];
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.post('*/api/v1/job-cards/:id/close-estimate', () => {
        calls.push('close');
        return HttpResponse.json(jobCard({ arrivedAt: null, status: 'DECLINED' }));
      }),
      http.post('*/api/v1/appointments/:id/cancel', () => {
        calls.push('cancel');
        return HttpResponse.json(appointment({ id: APPOINTMENT_ID, status: 'CANCELLED' }));
      }),
    );
    estimateCard();
    renderApp(`/job-cards/${CARD_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Close estimate' }));
    expect(await screen.findByText('Estimate closed')).toBeInTheDocument();
    expect(calls).toEqual(['close', 'cancel']);
    // Not at the shop: nothing to start or complete.
    expect(screen.queryByRole('button', { name: 'Complete job' })).not.toBeInTheDocument();
  });
});
