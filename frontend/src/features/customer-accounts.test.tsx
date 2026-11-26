import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { renderApp } from '@/test/render';
import { customer, customerAccount, pageOf, server, sessionFor, user } from '@/test/server';

const jane = customer({ id: 'c-jane', name: 'Jane Doe', userId: 'u-jane' });

describe('Team: staff only', () => {
  it('asks for the staff and is called Team', async () => {
    let kind: string | null = null;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/auth/users', ({ request }) => {
        kind = new URL(request.url).searchParams.get('kind');
        return HttpResponse.json(pageOf([user('ADMIN', 'admin1')]));
      }),
    );
    renderApp('/users');

    expect(await screen.findByRole('heading', { name: 'Team' }, { timeout: 10_000 })).toBeInTheDocument();
    await vi.waitFor(() => expect(kind).toBe('staff'));
  });
});

describe("a customer's online account, on their page", () => {
  it('lets a manager disable sign-in', async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('MANAGER', 'manager1')),
      http.get('*/api/v1/customers/:id', () => HttpResponse.json(jane)),
      http.patch('*/api/v1/auth/customer-accounts/u-jane', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(customerAccount({ id: 'u-jane', enabled: false }));
      }),
    );
    renderApp('/customers/c-jane');

    const card = (
      await screen.findByRole('heading', { name: 'Online account' }, { timeout: 10_000 })
    ).closest('div.rounded-xl') as HTMLElement;
    expect(await within(card).findByText('Active')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Reset password for Jane Doe' })).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Disable sign-in' }));
    await vi.waitFor(() => expect(sent).toEqual({ enabled: false }));
  });

  it('lets an advisor unlock a locked account, but not disable it', async () => {
    let unlocked = false;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers/:id', () => HttpResponse.json(jane)),
      http.get('*/api/v1/auth/customer-accounts/u-jane', () =>
        HttpResponse.json(
          customerAccount({
            id: 'u-jane',
            locked: !unlocked,
            lockedUntil: unlocked ? null : '2030-01-01T10:00:00Z',
          }),
        ),
      ),
      http.post('*/api/v1/auth/customer-accounts/u-jane/unlock', () => {
        unlocked = true;
        return HttpResponse.json(customerAccount({ id: 'u-jane' }));
      }),
    );
    renderApp('/customers/c-jane');

    expect(await screen.findByText('Locked', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disable sign-in' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Unlock' }));
    await vi.waitFor(() => expect(unlocked).toBe(true));
    expect(await screen.findByText('Active')).toBeInTheDocument();
  });

  it('says a walk-in customer has no online account', async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers/:id', () =>
        HttpResponse.json(customer({ id: 'c-walk', name: 'Walk In', userId: undefined })),
      ),
    );
    renderApp('/customers/c-walk');

    expect(await screen.findByText(/No online account/, {}, { timeout: 10_000 })).toBeInTheDocument();
  });

  it("shows each customer's account status in the list", async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers', () => HttpResponse.json(pageOf([jane]))),
      http.get('*/api/v1/auth/customer-accounts', () =>
        HttpResponse.json([customerAccount({ id: 'u-jane', enabled: false })]),
      ),
    );
    renderApp('/customers');

    const row = (await screen.findByRole('link', { name: 'Jane Doe' }, { timeout: 10_000 })).closest(
      'tr',
    ) as HTMLElement;
    expect(await within(row).findByText('Disabled')).toBeInTheDocument();
  });
});
