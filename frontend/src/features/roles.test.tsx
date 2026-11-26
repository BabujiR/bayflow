import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { User } from '@/api/types';
import { optionOf, toggleOption } from '@/test/multi-select';
import { renderApp } from '@/test/render';
import { pageOf, problem, server, sessionFor, token, user } from '@/test/server';

afterEach(() => {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
});

const ME = { ...user('ADMIN', 'owner1'), id: 'u-owner' };

describe('one person, several roles', () => {
  it('creates an account that is both a service advisor and a mechanic', async () => {
    let created: unknown;
    server.use(
      sessionFor(ME),
      http.post('*/api/v1/auth/users', async ({ request }) => {
        created = await request.json();
        return HttpResponse.json(user('MECHANIC', 'sam'), { status: 201 });
      }),
    );
    renderApp('/users');

    await userEvent.click(
      await screen.findByRole('button', { name: 'New staff account' }, { timeout: 10_000 }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'New staff account' });
    await userEvent.type(within(dialog).getByLabelText('Username'), 'sam');
    await userEvent.type(within(dialog).getByLabelText('Email'), 'sam@example.com');
    await userEvent.type(within(dialog).getByLabelText('Temporary password'), 'Correct-Horse-1');
    await toggleOption(dialog, 'Roles', /^Service advisor/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }));

    await vi.waitFor(() => expect(created).toMatchObject({ roles: ['SERVICE_ADVISOR', 'MECHANIC'] }));
  });

  it("changes someone's roles, and keeps your own admin role", async () => {
    let sent: unknown;
    const mechanic: User = { ...user('MECHANIC', 'mech1'), id: 'u-mech' };
    server.use(
      sessionFor(ME),
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([ME, mechanic]))),
      http.put('*/api/v1/auth/users/u-mech/roles', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json<User>({ ...mechanic, roles: ['SERVICE_ADVISOR', 'MECHANIC'] });
      }),
    );
    renderApp('/users');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit roles of owner1' }, { timeout: 10_000 }),
    );
    let dialog = await screen.findByRole('dialog', { name: 'Roles of owner1' });
    expect(await optionOf(dialog, 'Roles', /^Admin/)).toHaveAttribute('aria-disabled', 'true');
    await userEvent.keyboard('{Escape}');
    await userEvent.keyboard('{Escape}');

    await userEvent.click(screen.getByRole('button', { name: 'Edit roles of mech1' }));
    dialog = await screen.findByRole('dialog', { name: 'Roles of mech1' });
    await toggleOption(dialog, 'Roles', /^Service advisor/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save roles' }));

    await vi.waitFor(() => expect(sent).toEqual({ roles: ['SERVICE_ADVISOR', 'MECHANIC'] }));
  });

  it('asks an owner who is only an admin whether they also work in the shop', async () => {
    let sent: unknown;
    let current: User = ME;
    server.use(
      // The session follows the account: after the change, the new token carries the new roles.
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(token(current))),
      http.put('*/api/v1/auth/users/u-owner/roles', async ({ request }) => {
        sent = await request.json();
        current = { ...ME, roles: ['ADMIN', 'SERVICE_ADVISOR', 'MECHANIC'] };
        return HttpResponse.json<User>(current);
      }),
    );
    renderApp('/');

    expect(
      await screen.findByRole('heading', { name: 'Do you also work in the shop?' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /I also run the front desk/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /I also work on cars/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Add to my account' }));

    await vi.waitFor(() => expect(sent).toEqual({ roles: ['ADMIN', 'SERVICE_ADVISOR', 'MECHANIC'] }));
    // Their day's work, and every section of the menu.
    const today = await screen.findByRole('region', { name: 'Your work today' });
    expect(within(today).getByRole('link', { name: /Today's board/ })).toBeInTheDocument();
    expect(within(today).getByRole('link', { name: /My jobs/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Do you also work in the shop?' })).not.toBeInTheDocument();
  });

  it('shows the last-admin rule from the server', async () => {
    const other: User = { ...user('ADMIN', 'admin2'), id: 'u-a2' };
    server.use(
      sessionFor(ME),
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([ME, other]))),
      http.put('*/api/v1/auth/users/u-a2/roles', () =>
        problem(409, 'last-admin', 'The workshop needs at least one admin.'),
      ),
    );
    renderApp('/users');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit roles of admin2' }, { timeout: 10_000 }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Roles of admin2' });
    await toggleOption(dialog, 'Roles', /^Admin/);
    await toggleOption(dialog, 'Roles', /^Mechanic/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save roles' }));
    // Stays open: the change didn't happen.
    expect(await screen.findByRole('dialog', { name: 'Roles of admin2' })).toBeInTheDocument();
  });
});
