import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import type { AuditEvent } from '@/api/types';
import { renderApp } from '@/test/render';
import { pageOf, server, sessionFor, user } from '@/test/server';

const event = (overrides: Partial<AuditEvent>): AuditEvent => ({
  id: 'e-1',
  occurredAt: '2026-11-20T15:00:00Z',
  action: 'STAFF_CREATED',
  actorId: 'u-admin',
  actorName: 'owner',
  targetId: 'u-sam',
  targetName: 'sam',
  details: 'Mechanic at Main',
  requestId: 'r-1',
  ...overrides,
});

describe('Admin > Activity', () => {
  it('lists account changes with who made them', async () => {
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/auth/audit-events', () =>
        HttpResponse.json(
          pageOf([event({ id: 'e-2', action: 'ACCOUNT_DISABLED', details: null }), event({ id: 'e-1' })]),
        ),
      ),
    );
    renderApp('/activity');

    expect(await screen.findByRole('heading', { name: 'Activity' }, { timeout: 10_000 })).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    // A header row, then newest first.
    expect(within(rows[1]).getByText('Disabled the account')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Created the account')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Mechanic at Main')).toBeInTheDocument();
    expect(within(rows[2]).getByText('owner')).toBeInTheDocument();
  });

  it('says so when nothing has happened yet', async () => {
    server.use(sessionFor(user('ADMIN', 'owner')));
    renderApp('/activity');

    expect(await screen.findByText('Nothing yet', {}, { timeout: 10_000 })).toBeInTheDocument();
  });
});
