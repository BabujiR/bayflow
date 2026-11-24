import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { ConsoleDemoRequest, ConsoleInvitation } from '@/api/types';
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

const invitation = (overrides: Partial<ConsoleInvitation> = {}): ConsoleInvitation => ({
  id: 'i1',
  email: 'ana@example.com',
  contactName: 'Ana Silva',
  shopName: 'Lotus Auto Group',
  demoRequestId: 'd1',
  state: 'PENDING',
  expiresAt: '2026-12-01T15:00:00Z',
  sentCount: 1,
  lastSentAt: '2026-11-24T15:00:00Z',
  createdAt: '2026-11-24T15:00:00Z',
  createdBy: 'team',
  acceptedAt: null,
  workshopSlug: null,
  revokedAt: null,
  revokedBy: null,
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

  it('sends a setup invite from a demo request and shows the link once', async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('PLATFORM_ADMIN', 'team')),
      http.get('*/api/v1/auth/platform/demo-requests', () => HttpResponse.json(pageOf([request()]))),
      http.post('*/api/v1/auth/platform/invitations', async ({ request: req }) => {
        sent = await req.json();
        return HttpResponse.json(
          { invitation: invitation(), setupUrl: 'http://localhost:3000/setup?invite=tok-1', emailed: false },
          { status: 201 },
        );
      }),
    );
    renderConsole();

    await userEvent.click(await screen.findByRole('button', { name: 'Open Lotus Auto Group' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Send setup invite' }));
    const form = await screen.findByRole('form', { name: 'Send setup invite' });
    expect(within(form).getByLabelText("Owner's email")).toHaveValue('ana@example.com');
    expect(within(form).getByLabelText('Shop name')).toHaveValue('Lotus Auto Group');
    await userEvent.click(within(form).getByRole('button', { name: 'Send invite' }));

    expect(await screen.findByLabelText('Setup link')).toHaveValue(
      'http://localhost:3000/setup?invite=tok-1',
    );
    expect(screen.getByText(/E-mail isn't set up here, so nothing was sent/)).toBeInTheDocument();
    expect(sent).toEqual({
      email: 'ana@example.com',
      contactName: 'Ana Silva',
      shopName: 'Lotus Auto Group',
      demoRequestId: 'd1',
    });
  });

  it('lists invitations and resends or revokes them', async () => {
    const calls: string[] = [];
    server.use(
      sessionFor(user('PLATFORM_ADMIN', 'team')),
      http.get('*/api/v1/auth/platform/invitations', () =>
        HttpResponse.json(
          pageOf([
            invitation(),
            invitation({ id: 'i2', email: 'bo@example.com', state: 'ACCEPTED', workshopSlug: 'bo-motors' }),
          ]),
        ),
      ),
      http.post('*/api/v1/auth/platform/invitations/i1/resend', () => {
        calls.push('resend');
        return HttpResponse.json({
          invitation: invitation({ sentCount: 2 }),
          setupUrl: 'http://localhost:3000/setup?invite=tok-2',
          emailed: true,
        });
      }),
      http.post('*/api/v1/auth/platform/invitations/i1/revoke', () => {
        calls.push('revoke');
        return HttpResponse.json(invitation({ state: 'REVOKED' }));
      }),
    );
    renderConsole('/console/invitations');

    const pending = (await screen.findByText('ana@example.com')).closest('tr') as HTMLElement;
    expect(within(pending).getByText('Pending')).toBeInTheDocument();
    const used = screen.getByText('bo@example.com').closest('tr') as HTMLElement;
    expect(within(used).getByRole('link', { name: 'bo-motors' })).toBeInTheDocument();
    expect(within(used).queryByRole('button', { name: /Resend/ })).not.toBeInTheDocument();

    await userEvent.click(within(pending).getByRole('button', { name: 'Resend invite to ana@example.com' }));
    expect(await screen.findByLabelText('Setup link')).toHaveValue(
      'http://localhost:3000/setup?invite=tok-2',
    );
    expect(screen.getByText(/E-mailed to ana@example.com/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));

    await userEvent.click(within(pending).getByRole('button', { name: 'Revoke invite to ana@example.com' }));
    await vi.waitFor(() => expect(calls).toEqual(['resend', 'revoke']));
  });
});
