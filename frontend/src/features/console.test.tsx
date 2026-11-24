import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { ConsoleDemoRequest } from '@/api/types';
import { renderConsole } from '@/test/render';
import { noSession, pageOf, server, sessionFor, user } from '@/test/server';

const request = (overrides: Partial<ConsoleDemoRequest> = {}): ConsoleDemoRequest => ({
  id: 'd1',
  firstName: 'Ana',
  lastName: 'Silva',
  email: 'ana@example.com',
  phone: '555 010 0199',
  shopName: 'Lotus Auto Group',
  shopType: 'GENERAL_REPAIR',
  locations: '4-10',
  city: 'Detroit, MI',
  message: 'Two shops today, opening a third.',
  status: 'NEW',
  notes: null,
  createdAt: '2026-11-24T15:00:00Z',
  updatedAt: null,
  updatedBy: null,
  version: 0,
  ...overrides,
});

describe('Ramplight console', () => {
  it('asks the team to sign in', async () => {
    server.use(noSession);
    renderConsole();

    expect(await screen.findByRole('form', { name: 'Sign in to the console' })).toBeInTheDocument();
  });

  it('turns workshop accounts away', async () => {
    server.use(sessionFor(user('ADMIN', 'owner')));
    renderConsole();

    expect(await screen.findByText('Not a console account')).toBeInTheDocument();
  });

  it('lists demo requests and moves one on with a note', async () => {
    let saved: unknown;
    server.use(
      sessionFor(user('PLATFORM_ADMIN', 'team')),
      http.get('*/api/v1/auth/platform/demo-requests', () => HttpResponse.json(pageOf([request()]))),
      http.put('*/api/v1/auth/platform/demo-requests/d1', async ({ request: req }) => {
        const body = (await req.json()) as { status: string; notes: string };
        saved = body;
        return HttpResponse.json({ ...request(), ...body, version: 1 });
      }),
    );
    renderConsole();

    const row = (await screen.findByText('Lotus Auto Group')).closest('tr') as HTMLElement;
    expect(within(row).getByText('4–10 locations')).toBeInTheDocument();
    expect(within(row).getByText('New')).toBeInTheDocument();

    await userEvent.click(within(row).getByRole('button', { name: 'Open Lotus Auto Group' }));
    const dialog = await screen.findByRole('dialog', { name: 'Lotus Auto Group' });
    expect(within(dialog).getByText('Two shops today, opening a third.')).toBeInTheDocument();
    await userEvent.selectOptions(within(dialog).getByLabelText('Status'), 'CONTACTED');
    await userEvent.type(within(dialog).getByLabelText('Notes'), 'Demo Thursday 10am');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await vi.waitFor(() =>
      expect(saved).toEqual({ status: 'CONTACTED', notes: 'Demo Thursday 10am', version: 0 }),
    );
  });
});
