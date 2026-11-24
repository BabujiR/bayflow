import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';

import type { CannedJob } from '@/api/types';
import { renderApp } from '@/test/render';
import { branding, location, pageOf, server, sessionFor, user } from '@/test/server';

const cannedJob = { id: 'c1', name: 'Oil change' } as CannedJob;

afterEach(() => window.localStorage.clear());

describe('welcome checklist (new workshop owner)', () => {
  it('shows what is left to set up, ticked off from the workshop', async () => {
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/auth/tenant/branding', () => HttpResponse.json(branding({ logoUrl: '/logo.png' }))),
      http.get('*/api/v1/canned-jobs', () => HttpResponse.json([cannedJob])),
    );
    renderApp('/');

    const card = await screen.findByRole('region', { name: 'Get your workshop ready' });
    expect(within(card).getByText(/2 of 5 done/)).toBeInTheDocument();
    expect(within(card).getByTestId('welcome-logo')).toHaveAttribute('data-done', 'true');
    expect(within(card).getByTestId('welcome-canned')).toHaveAttribute('data-done', 'true');
    expect(within(card).getByTestId('welcome-team')).toHaveAttribute('data-done', 'false');
    expect(within(card).getByRole('link', { name: /Add your team/ })).toHaveAttribute('href', '/users');
  });

  it('can be dismissed, and stays dismissed', async () => {
    server.use(sessionFor(user('ADMIN', 'owner')));
    const first = renderApp('/');

    const card = await screen.findByRole('region', { name: 'Get your workshop ready' });
    await userEvent.click(within(card).getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('region', { name: 'Get your workshop ready' })).not.toBeInTheDocument();
    first.unmount();

    renderApp('/');
    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Get your workshop ready' })).not.toBeInTheDocument();
  });

  it('goes away once everything is set up (other locations are optional)', async () => {
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/auth/tenant/branding', () => HttpResponse.json(branding({ logoUrl: '/logo.png' }))),
      http.get('*/api/v1/canned-jobs', () => HttpResponse.json([cannedJob])),
      http.get('*/api/v1/service-intervals', () => HttpResponse.json([{ id: 'si1' }])),
      http.get('*/api/v1/auth/users', () =>
        HttpResponse.json(pageOf([user('ADMIN', 'owner'), user('MECHANIC', 'mech1')])),
      ),
      http.get('*/api/v1/auth/locations', () => HttpResponse.json([location()])),
    );
    renderApp('/');

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Get your workshop ready' })).not.toBeInTheDocument();
  });

  it('is for owners only', async () => {
    server.use(sessionFor(user('MANAGER', 'manager1')));
    renderApp('/');

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Get your workshop ready' })).not.toBeInTheDocument();
  });
});
