import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { renderApp } from '@/test/render';
import { jobCard, problem, server, sessionFor, user, workshop } from '@/test/server';

describe('workshop settings (SaaS phase 3)', () => {
  it('shows the workshop name in the header for every role', async () => {
    server.use(sessionFor(user('MECHANIC', 'mechanic1')));
    renderApp('/');

    expect(await screen.findByTestId('workshop-name')).toHaveTextContent('Bayflow Downtown');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).queryByRole('link', { name: 'Workshop settings' })).not.toBeInTheDocument();
  });

  it('lets the admin change the rate, currency and time zone, sending the version read', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.put('*/api/v1/auth/tenant/settings', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          workshop({
            displayName: 'Bayflow Auckland',
            labourRate: 140,
            currency: 'NZD',
            timeZone: 'Pacific/Auckland',
            version: 1,
          }),
        );
      }),
    );
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Workshop settings' });
    const name = within(form).getByLabelText('Workshop name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Bayflow Auckland');
    const rate = within(form).getByLabelText('Labour rate per hour');
    await userEvent.clear(rate);
    await userEvent.type(rate, '140');
    const currency = within(form).getByLabelText('Currency');
    await userEvent.clear(currency);
    await userEvent.type(currency, 'nzd');
    await userEvent.selectOptions(within(form).getByLabelText('Time zone'), 'Pacific/Auckland');
    await userEvent.click(within(form).getByRole('button', { name: 'Save settings' }));

    await vi.waitFor(() =>
      expect(sent).toMatchObject({
        displayName: 'Bayflow Auckland',
        labourRate: 140,
        currency: 'NZD',
        timeZone: 'Pacific/Auckland',
        version: 0,
      }),
    );
    expect(await screen.findByTestId('workshop-name')).toHaveTextContent('Bayflow Auckland');
  });

  it('checks the form before sending and shows server errors on the field', async () => {
    let calls = 0;
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.put('*/api/v1/auth/tenant/settings', () => {
        calls += 1;
        return problem(400, 'invalid-currency', "'ABC' is not a currency code.");
      }),
    );
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Workshop settings' });
    const rate = within(form).getByLabelText('Labour rate per hour');
    await userEvent.clear(rate);
    await userEvent.type(rate, '-5');
    await userEvent.click(within(form).getByRole('button', { name: 'Save settings' }));
    expect(await within(form).findByText('Cannot be negative')).toBeInTheDocument();
    expect(calls).toBe(0);

    await userEvent.clear(rate);
    await userEvent.type(rate, '95');
    const currency = within(form).getByLabelText('Currency');
    await userEvent.clear(currency);
    await userEvent.type(currency, 'ABC');
    await userEvent.click(within(form).getByRole('button', { name: 'Save settings' }));
    expect(await within(form).findByText("'ABC' is not a currency code.")).toBeInTheDocument();
  });

  it('is only for admins', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/settings');

    expect(await screen.findByRole('heading', { name: 'Not permitted' })).toBeInTheDocument();
  });

  it("labels job card amounts in the card's own currency", async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/job-cards/:id', () =>
        HttpResponse.json(
          jobCard({
            status: 'INSPECTED',
            currency: 'EUR',
            labourRate: 120,
            findings: [
              {
                id: 'f1',
                description: 'Pads worn',
                severity: 'HIGH',
                recordedBy: 'mechanic1',
                createdAt: '2026-11-10T14:10:00Z',
              },
            ],
          }),
        ),
      ),
    );
    renderApp(`/job-cards/${jobCard().id}`);

    expect(await screen.findByLabelText('Parts (€)')).toBeInTheDocument();
    expect(screen.getByText(/at €120\.00\/h/)).toBeInTheDocument();
  });
});
