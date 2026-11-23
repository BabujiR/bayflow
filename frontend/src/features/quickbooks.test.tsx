import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { QuickBooksStatus } from '@/api/types';
import { renderApp } from '@/test/render';
import { server, sessionFor, user } from '@/test/server';

const connected = (overrides: Partial<QuickBooksStatus> = {}): QuickBooksStatus => ({
  available: true,
  connected: true,
  ready: false,
  environment: 'sandbox',
  companyName: 'Sandbox Co',
  country: 'US',
  connectedAt: '2026-11-20T10:00:00Z',
  connectedBy: 'owner',
  mapping: null,
  ...overrides,
});

describe('QuickBooks Online (R2b)', () => {
  it('starts connecting with the page to come back to', async () => {
    let returnUrl: unknown;
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/quickbooks', () =>
        HttpResponse.json(connected({ connected: false, companyName: null })),
      ),
      http.post('*/api/v1/quickbooks/connect', async ({ request }) => {
        returnUrl = ((await request.json()) as { returnUrl: string }).returnUrl;
        // A failure keeps the browser here: the test only checks what was asked for.
        return HttpResponse.json({ status: 503, title: 'Service Unavailable' }, { status: 503 });
      }),
    );
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Connect to QuickBooks' }));

    await vi.waitFor(() => expect(returnUrl).toBe(`${window.location.origin}/settings`));
  });

  it('says it is connected and saves where lines go', async () => {
    let saved: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/quickbooks', () => HttpResponse.json(connected())),
      http.get('*/api/v1/quickbooks/items', () =>
        HttpResponse.json([
          { id: '2', name: 'Labor', type: 'Service' },
          { id: '1', name: 'Parts', type: 'NonInventory' },
          { id: '3', name: 'Shop fees', type: 'Service' },
        ]),
      ),
      http.get('*/api/v1/quickbooks/accounts', () =>
        HttpResponse.json([{ id: '35', name: 'Checking', type: 'Bank' }]),
      ),
      http.put('*/api/v1/quickbooks/mapping', async ({ request }) => {
        saved = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(connected({ ready: true }));
      }),
    );
    renderApp('/settings?quickbooks=connected');

    expect(await screen.findByText(/QuickBooks is connected/)).toBeInTheDocument();
    const section = screen.getByRole('region', { name: 'QuickBooks Online' });
    expect(await within(section).findByText('Connected to Sandbox Co')).toBeInTheDocument();

    await userEvent.selectOptions(await within(section).findByLabelText('Parts'), '1');
    await userEvent.selectOptions(within(section).getByLabelText('Labour'), '2');
    await userEvent.selectOptions(within(section).getByLabelText('Fees'), '3');
    await within(section).findByRole('option', { name: 'Checking' });
    await userEvent.selectOptions(within(section).getByLabelText('Deposit payments to'), '35');
    await userEvent.click(within(section).getByRole('button', { name: 'Save' }));

    await vi.waitFor(() =>
      expect(saved).toEqual({
        partsItemId: '1',
        partsItemName: 'Parts',
        labourItemId: '2',
        labourItemName: 'Labor',
        feesItemId: '3',
        feesItemName: 'Shop fees',
        depositAccountId: '35',
        depositAccountName: 'Checking',
      }),
    );
  });
});
