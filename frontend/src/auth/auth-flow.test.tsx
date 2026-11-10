import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test/render';
import { noSession, problem, server, sessionFor, token, user } from '@/test/server';

describe('signing in and out', () => {
  it('sends anonymous visitors to the login page', async () => {
    server.use(noSession);
    const { router } = renderApp('/appointments');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('restores the session from the refresh cookie on load', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Welcome, advisor1' })).toBeInTheDocument();
  });

  it('validates the login form before calling the server', async () => {
    server.use(noSession);
    renderApp('/login');

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter your username')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();
  });

  it('shows a clear message for a wrong password', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/auth/login', () =>
        problem(401, 'invalid-credentials', 'Username or password is incorrect.'),
      ),
    );
    renderApp('/login');

    await userEvent.type(await screen.findByLabelText('Username'), 'customer1');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username or password is incorrect.');
  });

  it('signs in and returns to the page the user asked for', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/auth/login', async ({ request }) => {
        const body = (await request.json()) as { username: string };
        return HttpResponse.json(token(user('CUSTOMER', body.username)));
      }),
    );
    const { router } = renderApp('/vehicles');

    await userEvent.type(await screen.findByLabelText('Username'), 'customer1');
    await userEvent.type(screen.getByLabelText('Password'), 'Bayflow!2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    // The page's own title (h1), not the "My vehicles" card on the home page.
    expect(await screen.findByRole('heading', { level: 1, name: 'My vehicles' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/vehicles');
  });

  it('signs out from the user menu', async () => {
    let loggedOut = false;
    server.use(
      sessionFor(user('CUSTOMER', 'customer1')),
      http.post('*/api/v1/auth/logout', () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/');

    await userEvent.click(await screen.findByRole('button', { name: 'Account menu for customer1' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Sign out/ }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(loggedOut).toBe(true);
  });

  it('registers a customer and signs them in', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/auth/register', () => HttpResponse.json(user('CUSTOMER', 'newbie'), { status: 201 })),
      http.post('*/api/v1/auth/login', () => HttpResponse.json(token(user('CUSTOMER', 'newbie')))),
    );
    renderApp('/register');

    await userEvent.type(await screen.findByLabelText('Username'), 'newbie');
    await userEvent.type(screen.getByLabelText('Email'), 'newbie@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-1');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Correct-Horse-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'Welcome, newbie' })).toBeInTheDocument();
  });

  it('shows the server error on the right field when registering', async () => {
    server.use(
      noSession,
      http.post('*/api/v1/auth/register', () => problem(409, 'username-taken', 'That username is already taken.')),
    );
    renderApp('/register');

    await userEvent.type(await screen.findByLabelText('Username'), 'customer1');
    await userEvent.type(screen.getByLabelText('Email'), 'c1@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-1');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Different-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();

    const confirm = screen.getByLabelText('Confirm password');
    await userEvent.clear(confirm);
    await userEvent.type(confirm, 'Correct-Horse-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('That username is already taken.')).toBeInTheDocument();
  });
});

describe('role-based navigation', () => {
  it('shows customers their own links only', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'My vehicles' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'User accounts' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: "Today's board" })).not.toBeInTheDocument();
  });

  it('shows admins the user accounts link', async () => {
    server.use(sessionFor(user('ADMIN', 'admin1')));
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'User accounts' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'My jobs' })).not.toBeInTheDocument();
  });

  it('shows "not permitted" when a role opens another role\'s page', async () => {
    server.use(sessionFor(user('MECHANIC', 'mechanic1')));
    renderApp('/users');

    expect(await screen.findByRole('heading', { name: 'Not permitted' })).toBeInTheDocument();
  });

  it('shows a 404 page for unknown paths', async () => {
    server.use(sessionFor(user('CUSTOMER')));
    renderApp('/no-such-page');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
