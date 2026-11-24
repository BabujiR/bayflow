import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { DueService } from '@/api/types';
import { renderApp } from '@/test/render';
import { jobCard, server, sessionFor, user } from '@/test/server';

const oil = (overrides: Partial<DueService> = {}): DueService => ({
  intervalId: 'i1',
  name: 'Oil change',
  lastDoneAt: '2026-06-01T15:00:00Z',
  lastMileage: 42000,
  dueMileage: 47000,
  dueAt: '2026-12-01T15:00:00Z',
  overdue: false,
  cannedJobId: null,
  ...overrides,
});

describe('maintenance reminders (R3b)', () => {
  it('lists the vehicles due soon with their services', async () => {
    let asked: string | null = null;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/service-due', ({ request }) => {
        asked = new URL(request.url).searchParams.get('days');
        return HttpResponse.json([
          {
            vehicleId: 'v1',
            registration: 'DUE 1',
            customerName: 'Ana Silva',
            customerUserId: 'u1',
            locationId: '00000000-0000-0000-0000-000000000001',
            dueAt: '2026-11-20T15:00:00Z',
            remindedAt: null,
            services: [
              oil({ overdue: true }),
              oil({ intervalId: 'i2', name: 'Tire rotation', dueMileage: null }),
            ],
          },
        ]);
      }),
    );
    renderApp('/due-soon');

    const row = (await screen.findByText('DUE 1')).closest('tr') as HTMLElement;
    expect(within(row).getByText('Ana Silva')).toBeInTheDocument();
    expect(within(row).getByText('Overdue')).toBeInTheDocument();
    expect(within(row).getByText('Tire rotation')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Book DUE 1' })).toHaveAttribute(
      'href',
      '/appointments/new',
    );
    expect(asked).toBe('30');
  });

  it('offers what is due on the vehicle as a one-click recommendation', async () => {
    let vehicleAsked: URL | undefined;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/job-cards/:id', () => HttpResponse.json(jobCard({ status: 'INSPECTED' }))),
      http.get('*/api/v1/service-due/vehicles/:vehicleId', ({ request }) => {
        vehicleAsked = new URL(request.url);
        return HttpResponse.json([oil()]);
      }),
    );
    renderApp(`/job-cards/${jobCard().id}`);

    const form = await screen.findByRole('form', { name: 'Add recommendation' });
    await userEvent.click(await within(form).findByRole('button', { name: /Oil change/ }));
    expect(within(form).getByLabelText('Recommended work')).toHaveValue('Oil change');
    expect(vehicleAsked?.searchParams.get('mileage')).toBe('12480');
  });

  it('lets an admin add a service interval', async () => {
    let created: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.post('*/api/v1/service-intervals', async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'i9', ...created, version: 0 }, { status: 201 });
      }),
    );
    renderApp('/settings');

    const section = await screen.findByRole('region', { name: 'Service intervals' });
    await userEvent.click(within(section).getByRole('button', { name: 'Add interval' }));
    const dialog = await screen.findByRole('dialog', { name: 'New service interval' });
    await userEvent.type(within(dialog).getByLabelText('Service'), 'Wipers');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByText('Give the miles, the months or both.')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('Or every (months)'), '12');
    await userEvent.type(within(dialog).getByLabelText('Keywords'), 'wiper, wiper blades');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await vi.waitFor(() =>
      expect(created).toEqual({
        name: 'Wipers',
        miles: null,
        months: 12,
        keywords: ['wiper', 'wiper blades'],
        cannedJobIds: [],
        active: true,
        version: null,
      }),
    );
  });
});
