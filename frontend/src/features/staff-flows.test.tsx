import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { JobCard } from '@/api/types';
import { renderApp } from '@/test/render';
import { appointment, jobCard, pageOf, server, sessionFor, user } from '@/test/server';

const awaiting: Partial<JobCard> = {
  status: 'AWAITING_APPROVAL',
  findings: [{ id: 'f1', description: 'Front pads at 2 mm', severity: 'HIGH', recordedBy: 'mechanic1', createdAt: '2026-11-10T14:10:00Z' }],
  recommendations: [
    {
      id: 'r1',
      description: 'Replace front pads',
      estimatedPartsCost: 120.5,
      estimatedLabourHours: 1.5,
      estimatedLabourCost: 142.5,
      estimatedTotal: 263,
      approvalStatus: 'PENDING',
    },
  ],
  estimatedTotal: 263,
  submittedAt: '2026-11-10T14:20:00Z',
};

/** The detail page of the test card (its URL id must match the card's own id). */
const CARD_PATH = `/job-cards/${jobCard().id}`;

function serveCard(card: JobCard) {
  return http.get('*/api/v1/job-cards/:id', () => HttpResponse.json(card));
}

describe("advisor's board", () => {
  it('groups the day by status and checks a vehicle in with its mileage', async () => {
    let checkInBody: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/appointments', () =>
        HttpResponse.json(
          pageOf([
            appointment({ id: 'a-booked', status: 'BOOKED' }),
            appointment({ id: 'a-arrived', status: 'ARRIVED', vehicle: { id: 'v2', vin: 'X', registration: 'ARR 001', make: 'Ford', model: 'Focus', year: 2019 } }),
          ]),
        ),
      ),
      http.post('*/api/v1/appointments/a-arrived/check-in', async ({ request }) => {
        checkInBody = await request.json();
        return HttpResponse.json(appointment({ id: 'a-arrived', status: 'CHECKED_IN', jobCardId: 'j1' }));
      }),
    );
    renderApp('/board');

    const booked = await screen.findByRole('region', { name: 'Booked' });
    const arrived = screen.getByRole('region', { name: 'Arrived' });
    expect(within(booked).getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
    expect(within(arrived).getByText('ARR 001')).toBeInTheDocument();

    await userEvent.click(within(arrived).getByRole('button', { name: 'Check in' }));
    const dialog = await screen.findByRole('dialog', { name: 'Check in vehicle' });
    await userEvent.type(within(dialog).getByLabelText('Odometer reading (miles)'), '12480');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check in' }));

    await vi.waitFor(() => expect(checkInBody).toEqual({ mileage: 12480 }));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});

describe('job card actions by role', () => {
  it('lets the customer approve a recommendation', async () => {
    let decided = false;
    server.use(
      sessionFor(user('CUSTOMER')),
      serveCard(jobCard(awaiting)),
      http.post('*/api/v1/job-cards/:id/recommendations/r1/approve', () => {
        decided = true;
        return HttpResponse.json(
          jobCard({
            ...awaiting,
            status: 'APPROVED',
            approvedEstimate: 263,
            recommendations: [{ ...awaiting.recommendations![0]!, approvalStatus: 'APPROVED', decidedBy: 'customer1' }],
          }),
        );
      }),
    );
    renderApp(CARD_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Approve Replace front pads' }));

    expect(await screen.findByText('by customer1')).toBeInTheDocument();
    expect(decided).toBe(true);
    expect(screen.queryByRole('button', { name: 'Approve Replace front pads' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start work' })).not.toBeInTheDocument();
  });

  it('shows the mechanic "Start work" on an approved card but no approval buttons', async () => {
    server.use(sessionFor(user('MECHANIC', 'mechanic1')), serveCard(jobCard({ ...awaiting, status: 'APPROVED' })));
    renderApp(CARD_PATH);

    expect(await screen.findByRole('button', { name: 'Start work' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Approve/ })).not.toBeInTheDocument();
  });

  it('gives the advisor inspection tools on an inspected card', async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      serveCard(jobCard({ status: 'INSPECTED', findings: awaiting.findings })),
    );
    renderApp(CARD_PATH);

    expect(await screen.findByRole('form', { name: 'Add recommendation' })).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Add finding' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finish inspection (no work needed)' })).toBeInTheDocument();
  });

  it('is read-only for admins', async () => {
    server.use(sessionFor(user('ADMIN', 'admin1')), serveCard(jobCard({ ...awaiting })));
    renderApp(CARD_PATH);

    expect(await screen.findByText('Replace front pads')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Approve/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Complete job' })).not.toBeInTheDocument();
  });

  it('lets the mechanic log work for an approved item', async () => {
    let logged: unknown;
    server.use(
      sessionFor(user('MECHANIC', 'mechanic1')),
      serveCard(
        jobCard({
          ...awaiting,
          status: 'IN_PROGRESS',
          recommendations: [{ ...awaiting.recommendations![0]!, approvalStatus: 'APPROVED' }],
        }),
      ),
      http.post('*/api/v1/job-cards/:id/work-logs', async ({ request }) => {
        logged = await request.json();
        return HttpResponse.json(jobCard({ ...awaiting, status: 'IN_PROGRESS' }));
      }),
    );
    renderApp(CARD_PATH);

    const form = await screen.findByRole('form', { name: 'Log work' });
    await userEvent.type(within(form).getByLabelText('Work done'), 'Fitted new pads');
    await userEvent.click(within(form).getByRole('button', { name: 'Log work' }));

    await vi.waitFor(() => expect(logged).toEqual({ recommendationId: 'r1', description: 'Fitted new pads' }));
  });
});

describe('admin user accounts', () => {
  it('creates a staff account and can disable others but not themselves', async () => {
    let created: unknown;
    let patched: unknown;
    const me = user('ADMIN', 'admin1');
    const other = { ...user('MECHANIC', 'mechanic1'), id: 'u-mech' };
    server.use(
      sessionFor(me),
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([me, other]))),
      http.post('*/api/v1/auth/users', async ({ request }) => {
        created = await request.json();
        return HttpResponse.json({ ...user('SERVICE_ADVISOR', 'advisor2'), id: 'u-new' }, { status: 201 });
      }),
      http.patch('*/api/v1/auth/users/u-mech', async ({ request }) => {
        patched = await request.json();
        return HttpResponse.json({ ...other, enabled: false });
      }),
    );
    renderApp('/users');

    expect(await screen.findByRole('button', { name: 'Disable mechanic1' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disable admin1' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Disable mechanic1' }));
    await vi.waitFor(() => expect(patched).toEqual({ enabled: false }));

    await userEvent.click(screen.getByRole('button', { name: 'New staff account' }));
    const dialog = await screen.findByRole('dialog', { name: 'New staff account' });
    await userEvent.type(within(dialog).getByLabelText('Username'), 'advisor2');
    await userEvent.type(within(dialog).getByLabelText('Email'), 'advisor2@bayflow.local');
    await userEvent.type(within(dialog).getByLabelText('Temporary password'), 'Temp-Pass-2026');
    await userEvent.selectOptions(within(dialog).getByLabelText('Role'), 'SERVICE_ADVISOR');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }));

    await vi.waitFor(() =>
      expect(created).toEqual({
        username: 'advisor2',
        email: 'advisor2@bayflow.local',
        password: 'Temp-Pass-2026',
        role: 'SERVICE_ADVISOR',
      }),
    );
  });
});
