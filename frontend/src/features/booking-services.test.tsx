import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { BookingServiceItem } from '@/api/types';
import { renderApp } from '@/test/render';
import { location, server, sessionFor, user } from '@/test/server';

const service = (overrides: Partial<BookingServiceItem> = {}): BookingServiceItem => ({
  id: 's1',
  code: 'oil-change',
  name: 'Oil change',
  description: 'Oil and filter, fluids topped up',
  priceKind: 'FROM',
  price: 54,
  durationMinutes: 45,
  locationId: null,
  active: true,
  sortOrder: 1,
  updatedAt: '2026-11-26T10:00:00Z',
  version: 0,
  ...overrides,
});

describe('Settings → Online booking (admin)', () => {
  it('shows each service as customers see it, and reorders them', async () => {
    let order: unknown;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/booking-services', () =>
        HttpResponse.json([
          service(),
          service({
            id: 's2',
            code: 'brakes',
            name: 'Brakes',
            priceKind: 'QUOTE',
            price: null,
            durationMinutes: 120,
          }),
          service({
            id: 's3',
            code: 'tires',
            name: 'Tires',
            priceKind: 'NONE',
            price: null,
            durationMinutes: null,
            active: false,
          }),
        ]),
      ),
      http.put('*/api/v1/booking-services/order', async ({ request }) => {
        order = await request.json();
        return HttpResponse.json([]);
      }),
    );
    renderApp('/settings?tab=booking');

    const list = await screen.findByRole('list', { name: 'Bookable services' }, { timeout: 10_000 });
    expect(within(list).getByText('from $54 · about 45 min')).toBeInTheDocument();
    expect(within(list).getByText('Ask for a quote · about 2 h')).toBeInTheDocument();
    expect(within(list).getByText('No price or time shown')).toBeInTheDocument();
    expect(within(list).getByText('Hidden')).toBeInTheDocument();
    expect(within(list).getByText('Something else')).toBeInTheDocument();

    await userEvent.click(within(list).getByRole('button', { name: 'Move Oil change down' }));
    await vi.waitFor(() => expect(order).toEqual({ ids: ['s2', 's1', 's3'] }));
  });

  it('adds a service with a price and a typical time', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.post('*/api/v1/booking-services', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(service({ id: 's9', name: 'Wheel alignment' }), { status: 201 });
      }),
    );
    renderApp('/settings?tab=booking');

    await userEvent.click(await screen.findByRole('button', { name: 'Add service' }, { timeout: 10_000 }));
    const form = await screen.findByRole('form', { name: 'New service' });
    await userEvent.type(within(form).getByLabelText('Name'), 'Wheel alignment');
    await userEvent.selectOptions(within(form).getByLabelText('Price shown'), 'FROM');
    expect(within(form).getByRole('button', { name: 'Save service' })).toBeDisabled();
    await userEvent.type(within(form).getByLabelText('Price (USD)'), '89');
    await userEvent.selectOptions(within(form).getByLabelText('Typical time'), '60');
    expect(within(form).getByText(/Customers see:/)).toHaveTextContent(
      'Wheel alignment · from $89 · about 1 h',
    );
    await userEvent.click(within(form).getByRole('button', { name: 'Save service' }));

    await vi.waitFor(() =>
      expect(sent).toEqual({
        name: 'Wheel alignment',
        description: null,
        priceKind: 'FROM',
        price: 89,
        durationMinutes: 60,
        locationId: null,
        active: true,
      }),
    );
  });
});

describe('Settings → Locations: booking links', () => {
  it("copies a location's booking link", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/auth/locations', () =>
        HttpResponse.json([location({ code: 'downtown', name: 'Downtown' })]),
      ),
    );
    renderApp('/locations');

    await userEvent.click(await screen.findByRole('button', { name: 'Copy booking link for Downtown' }));
    expect(writeText).toHaveBeenCalledWith('http://demo.localhost:3000/book?location=downtown');
  });
});
