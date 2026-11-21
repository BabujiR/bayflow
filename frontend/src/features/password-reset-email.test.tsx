import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test/render';
import { noSession, problem, publicWorkshop, server, token, user } from '@/test/server';

describe('forgot password by e-mail', () => {
  it('is offered on the sign-in page only when the workshop sends e-mail', async () => {
    server.use(noSession);
    const { unmount } = renderApp('/login');
    expect(await screen.findByText(/Ask the workshop to reset it/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Reset it by email' })).not.toBeInTheDocument();
    unmount();

    server.use(
      http.get('*/api/v1/tenants/current', () =>
        HttpResponse.json(publicWorkshop({ passwordResetByEmail: true })),
      ),
    );
    renderApp('/login');
    expect(await screen.findByRole('link', { name: 'Reset it by email' })).toBeInTheDocument();
  });

  it('sends the link and says so without revealing whether the account exists', async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.get('*/api/v1/tenants/current', () =>
        HttpResponse.json(publicWorkshop({ passwordResetByEmail: true })),
      ),
      http.post('*/api/v1/auth/password/forgot', async ({ request }) => {
        sent = await request.json();
        return new HttpResponse(null, { status: 202 });
      }),
    );
    renderApp('/login');

    await userEvent.click(await screen.findByRole('link', { name: 'Reset it by email' }));
    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Email'), 'jane@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(await screen.findByText(/If an account uses/)).toHaveTextContent('jane@example.com');
    expect(sent).toEqual({ email: 'jane@example.com' });
  });

  it("the link's page sets the new password and signs in", async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.post('*/api/v1/auth/password/reset', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(token(user('CUSTOMER')));
      }),
    );
    const { router } = renderApp('/reset-password?token=abc123');

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('New password'), 'My-Own-Pass-1');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'My-Own-Pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(sent).toEqual({ token: 'abc123', newPassword: 'My-Own-Pass-1' });
  });

  it('an expired or used link offers a new one', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/auth/password/reset', () =>
        problem(400, 'reset-link-invalid', 'This reset link has expired or was already used.'),
      ),
    );
    renderApp('/reset-password?token=old');

    await userEvent.type(await screen.findByLabelText('New password'), 'My-Own-Pass-1');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'My-Own-Pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save new password' }));

    expect(
      await screen.findByRole('heading', { name: "This link doesn't work any more" }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Send a new link' })).toHaveAttribute('href', '/forgot-password');
  });

  it('a link without a token goes straight to asking for a new one', async () => {
    server.use(noSession);
    renderApp('/reset-password');
    expect(
      await screen.findByRole('heading', { name: "This link doesn't work any more" }),
    ).toBeInTheDocument();
  });
});
