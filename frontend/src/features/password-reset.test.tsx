import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import type { TemporaryPassword } from '@/api/types';
import { renderApp } from '@/test/render';
import { customer, pageOf, server, sessionFor, token, user } from '@/test/server';

const temporary = (userId: string, username: string): TemporaryPassword => ({
  userId,
  username,
  temporaryPassword: 'K7QH-3MZD-W9PT',
  expiresAt: '2026-11-13T10:00:00Z',
});

describe('password resets', () => {
  it("an admin resets a mechanic's password and sees the temporary one once", async () => {
    const mechanic = { ...user('MECHANIC', 'mike'), id: 'u-mike' };
    let resetFor: string | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([user('ADMIN', 'admin1'), mechanic]))),
      http.post('*/api/v1/auth/users/:id/password-reset', ({ params }) => {
        resetFor = params.id as string;
        return HttpResponse.json(temporary('u-mike', 'mike'));
      }),
    );
    renderApp('/users');

    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for mike' }));
    expect(screen.queryByRole('button', { name: 'Reset password for admin1' })).not.toBeInTheDocument();
    const dialog = screen.getByRole('dialog', { name: 'Reset password for mike?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('K7QH-3MZD-W9PT');
    expect(resetFor).toBe('u-mike');

    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument();
  });

  it("an advisor resets a customer's password from the customer's page", async () => {
    const jane = customer();
    let resetFor: string | undefined;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/customers/:id', () => HttpResponse.json(jane)),
      http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([]))),
      http.get('*/api/v1/appointments', () => HttpResponse.json(pageOf([]))),
      http.post('*/api/v1/auth/users/:id/password-reset', ({ params }) => {
        resetFor = params.id as string;
        return HttpResponse.json(temporary(jane.userId!, 'jane'));
      }),
    );
    renderApp(`/customers/${jane.id}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for Jane Doe' }));
    await userEvent.click(
      within(screen.getByRole('dialog', { name: 'Reset password for Jane Doe?' })).getByRole('button', {
        name: 'Reset password',
      }),
    );

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('K7QH-3MZD-W9PT');
    expect(resetFor).toBe(jane.userId);
  });

  it('someone signed in with a temporary password chooses a new one before anything else', async () => {
    let sent: unknown;
    server.use(
      sessionFor({ ...user('CUSTOMER'), mustChangePassword: true }),
      http.post('*/api/v1/auth/me/password', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(token(user('CUSTOMER')));
      }),
    );
    renderApp('/vehicles');

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Temporary password'), 'K7QH-3MZD-W9PT');
    await userEvent.type(screen.getByLabelText('New password'), 'My-Own-Pass-1');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'My-Own-Pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'My vehicles' })).toBeInTheDocument();
    expect(sent).toEqual({ currentPassword: 'K7QH-3MZD-W9PT', newPassword: 'My-Own-Pass-1' });
  });

  it('a wrong temporary password is shown on its field', async () => {
    server.use(
      sessionFor({ ...user('CUSTOMER'), mustChangePassword: true }),
      http.post('*/api/v1/auth/me/password', () =>
        HttpResponse.json(
          {
            type: 'urn:bayflow:problem:current-password-incorrect',
            title: 'Bad Request',
            status: 400,
            detail: 'Your current password is not correct.',
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderApp('/');

    await userEvent.type(await screen.findByLabelText('Temporary password'), 'WRONG-WRONG-1');
    await userEvent.type(screen.getByLabelText('New password'), 'My-Own-Pass-1');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'My-Own-Pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(await screen.findByText('Your current password is not correct.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
  });
});
