import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CustomerEstimate, EstimateLinkStatus, JobCard } from '@/api/types';
import { renderApp } from '@/test/render';
import { jobCard, noSession, problem, server, sessionFor, user } from '@/test/server';

const TOKEN = 'E'.repeat(43);

function estimate(overrides: Partial<CustomerEstimate> = {}): CustomerEstimate {
  return {
    shopName: 'Demo Garage',
    locationName: 'Main',
    shopPhone: '555-0100',
    firstName: 'Jane',
    vehicle: '2019 Honda Civic (BFL 1234)',
    status: 'AWAITING_APPROVAL',
    canDecide: true,
    findings: [{ description: 'Front pads at 2 mm', severity: 'HIGH' }],
    items: [
      { id: 'pads', description: 'Replace front pads', price: 263, status: 'PENDING' },
      { id: 'wipers', description: 'Replace wiper blades', price: 63.75, status: 'PENDING' },
    ],
    total: 326.75,
    currency: 'USD',
    expiresAt: '2030-12-01T15:00:00Z',
    decidedAt: null,
    signerName: null,
    ...overrides,
  };
}

/** jsdom has no canvas: a pad that draws nothing but signs all the same. */
beforeEach(() => {
  const context = {
    scale: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    setTransform: vi.fn(),
    clearRect: vi.fn(),
  };
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
    context as unknown as CanvasRenderingContext2D,
  );
  vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/png;base64,U0lH');
});

function sign() {
  const pad = screen.getByRole('img', { name: 'Sign here' });
  fireEvent.pointerDown(pad, { clientX: 10, clientY: 10, pointerId: 1 });
  fireEvent.pointerMove(pad, { clientX: 60, clientY: 40, pointerId: 1 });
  fireEvent.pointerUp(pad, { pointerId: 1 });
}

describe("the customer's estimate link", () => {
  it('approves one item, declines the other and signs', async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.post('*/api/v1/estimate-approval/view', () => HttpResponse.json(estimate())),
      http.post('*/api/v1/estimate-approval/decide', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(
          estimate({
            status: 'PARTIALLY_APPROVED',
            canDecide: false,
            decidedAt: '2030-11-20T15:00:00Z',
            signerName: 'Jane Doe',
            items: [
              { id: 'pads', description: 'Replace front pads', price: 263, status: 'APPROVED' },
              { id: 'wipers', description: 'Replace wiper blades', price: 63.75, status: 'DECLINED' },
            ],
          }),
        );
      }),
    );
    renderApp(`/estimate?token=${TOKEN}`);

    expect(
      await screen.findByRole('heading', { name: 'Your estimate' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Hi Jane, here's what we recommend for your 2019 Honda Civic/),
    ).toBeInTheDocument();
    expect(screen.getByText('Front pads at 2 mm')).toBeInTheDocument();

    // Nothing answered yet: confirming says what is missing.
    await userEvent.click(screen.getByRole('button', { name: 'Confirm and sign' }));
    expect(screen.getByText('Approve or decline each item above, then confirm.')).toBeInTheDocument();
    expect(screen.getByText('Sign in the box')).toBeInTheDocument();

    const [pads, wipers] = screen.getAllByTestId('estimate-item');
    await userEvent.click(within(pads!).getByRole('button', { name: 'Approve' }));
    await userEvent.click(within(wipers!).getByRole('button', { name: 'Decline' }));
    expect(screen.getByTestId('approved-total')).toHaveTextContent('$263.00');
    await userEvent.type(screen.getByLabelText('Your full name'), 'Jane Doe');
    sign();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm and sign' }));

    expect(await screen.findByText(/Thank you, Jane Doe\. You signed this estimate/)).toBeInTheDocument();
    const [pads2] = screen.getAllByTestId('estimate-item');
    expect(within(pads2!).getByText('Approved')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm and sign' })).not.toBeInTheDocument();
    expect(sent).toEqual({
      token: TOKEN,
      decisions: [
        { itemId: 'pads', approve: true },
        { itemId: 'wipers', approve: false },
      ],
      signerName: 'Jane Doe',
      signature: 'data:image/png;base64,U0lH',
    });
  });

  it('explains a replaced link', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/estimate-approval/view', () =>
        problem(
          410,
          'link-replaced',
          'A newer link was sent for this estimate. Open the latest e-mail from the shop.',
        ),
      ),
    );
    renderApp(`/estimate?token=${TOKEN}`);

    expect(
      await screen.findByText(/A newer link was sent for this estimate/, {}, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });
});

describe('the approval link on the job card', () => {
  it('lets the advisor e-mail it and shows when it was sent', async () => {
    const card = jobCard({
      status: 'AWAITING_APPROVAL',
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
    } as Partial<JobCard>);
    const status: EstimateLinkStatus = {
      sentTo: 'jane@example.com',
      sentBy: 'advisor1',
      sentAt: '2026-11-10T14:20:00Z',
      lastSentAt: '2026-11-10T14:20:00Z',
      expiresAt: '2026-11-24T14:20:00Z',
      firstViewedAt: null,
      remindersSent: 0,
      nextReminderAt: '2026-11-11T14:20:00Z',
      decidedAt: null,
      signerName: null,
      signed: false,
      open: true,
    };
    let posted = false;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/job-cards/:id', () => HttpResponse.json(card)),
      http.post('*/api/v1/job-cards/:id/estimate-link', () => {
        posted = true;
        return HttpResponse.json(status);
      }),
    );
    renderApp(`/job-cards/${card.id}`);

    const title = await screen.findByText('Customer approval link', {}, { timeout: 10_000 });
    const panel = title.closest('div.rounded-xl') as HTMLElement;
    expect(within(panel).getByText('No link sent yet.')).toBeInTheDocument();
    await userEvent.click(within(panel).getByRole('button', { name: 'E-mail approval link' }));

    expect(await within(panel).findByText('jane@example.com')).toBeInTheDocument();
    expect(within(panel).getByText('Not yet')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Send again' })).toBeInTheDocument();
    expect(posted).toBe(true);
  });
});
