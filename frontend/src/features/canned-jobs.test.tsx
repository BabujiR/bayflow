import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { CannedJob } from '@/api/types';
import { renderApp } from '@/test/render';
import { jobCard, server, sessionFor, user } from '@/test/server';

const CARD_PATH = `/job-cards/${jobCard().id}`;

function cannedJob(overrides: Partial<CannedJob> = {}): CannedJob {
  return {
    id: 'c1',
    locationId: null,
    name: 'Front brakes – pads & rotors',
    category: 'Brakes',
    description: 'Replace front brake pads and rotors',
    labourHours: 1.5,
    parts: [
      {
        id: 'p1',
        partNumber: 'BP-220',
        name: 'Front brake pad set',
        quantity: 1,
        unitPrice: 65,
        lineTotal: 65,
      },
      { id: 'p2', partNumber: 'RT-118', name: 'Front rotor', quantity: 2, unitPrice: 48.5, lineTotal: 97 },
    ],
    partsTotal: 162,
    active: true,
    updatedAt: '2026-11-20T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

describe('canned jobs (R3a)', () => {
  it('lets a manager add a canned job with its usual parts', async () => {
    let created: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('MANAGER', 'maria')),
      http.post('*/api/v1/canned-jobs', async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(cannedJob({ name: 'Oil change' }), { status: 201 });
      }),
    );
    renderApp('/canned-jobs');

    await userEvent.click(await screen.findByRole('button', { name: 'New canned job' }));
    const dialog = await screen.findByRole('dialog', { name: 'New canned job' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByText('Enter a name')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('Name'), 'Oil change');
    await userEvent.type(within(dialog).getByLabelText('Category'), 'Maintenance');
    await userEvent.type(within(dialog).getByLabelText('Description'), 'Synthetic oil and filter');
    await userEvent.type(within(dialog).getByLabelText('Labour (hours)'), '0.5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add part' }));
    await userEvent.type(within(dialog).getByLabelText('Part no. 1'), 'OF-1');
    await userEvent.type(within(dialog).getByLabelText('Part 1'), 'Oil filter');
    await userEvent.type(within(dialog).getByLabelText('Unit price 1'), '12.50');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await vi.waitFor(() =>
      expect(created).toEqual({
        locationId: null,
        name: 'Oil change',
        category: 'Maintenance',
        description: 'Synthetic oil and filter',
        labourHours: 0.5,
        parts: [{ partNumber: 'OF-1', name: 'Oil filter', quantity: 1, unitPrice: 12.5 }],
        version: null,
      }),
    );
  });

  it('fills a recommendation from a canned job and remembers which one', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/job-cards/:id', () => HttpResponse.json(jobCard({ status: 'INSPECTED' }))),
      http.get('*/api/v1/canned-jobs', () => HttpResponse.json([cannedJob()])),
      http.post('*/api/v1/job-cards/:id/recommendations', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(jobCard({ status: 'INSPECTED' }));
      }),
    );
    renderApp(CARD_PATH);

    const form = await screen.findByRole('form', { name: 'Add recommendation' });
    await userEvent.selectOptions(await within(form).findByLabelText('From canned job'), 'c1');
    expect(within(form).getByLabelText('Recommended work')).toHaveValue(
      'Replace front brake pads and rotors',
    );
    await userEvent.click(within(form).getByRole('button', { name: 'Add' }));

    await vi.waitFor(() =>
      expect(sent).toEqual({
        description: 'Replace front brake pads and rotors',
        estimatedPartsCost: 162,
        estimatedLabourHours: 1.5,
        cannedJobId: 'c1',
      }),
    );
  });

  it("lets the mechanic add the canned job's usual parts and labour in one go", async () => {
    let work: Record<string, unknown> | undefined;
    const item = {
      id: 'r1',
      description: 'Replace front brake pads and rotors',
      estimatedPartsCost: 162,
      estimatedLabourHours: 1.5,
      estimatedLabourCost: 142.5,
      estimatedTotal: 304.5,
      approvalStatus: 'APPROVED' as const,
      cannedJobId: 'c1',
    };
    server.use(
      sessionFor(user('MECHANIC', 'mechanic1')),
      http.get('*/api/v1/job-cards/:id', () =>
        HttpResponse.json(jobCard({ status: 'IN_PROGRESS', recommendations: [item] })),
      ),
      http.get('*/api/v1/canned-jobs/c1', () => HttpResponse.json(cannedJob())),
      http.post('*/api/v1/job-cards/:id/recommendations/r1/work', async ({ request }) => {
        work = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(jobCard({ status: 'IN_PROGRESS', recommendations: [item] }));
      }),
    );
    renderApp(CARD_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Add usual parts & labour' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add usual parts & labour' });
    await userEvent.click(await within(dialog).findByLabelText('Use Front brake pad set'));
    const rotors = within(dialog).getByLabelText('Quantity of Front rotor');
    await userEvent.clear(rotors);
    await userEvent.type(rotors, '1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add to the job' }));

    await vi.waitFor(() =>
      expect(work).toEqual({
        parts: [{ partNumber: 'RT-118', name: 'Front rotor', quantity: 1, unitPrice: 48.5 }],
        hours: 1.5,
      }),
    );
  });
});
