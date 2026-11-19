import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { effectiveRoles } from '@/auth/roles';
import { renderApp } from '@/test/render';
import { server, sessionFor, user } from '@/test/server';

describe('managers (phase L2)', () => {
  it('act as service advisors', () => {
    expect(effectiveRoles(['MANAGER'])).toEqual(['MANAGER', 'SERVICE_ADVISOR']);
    expect(effectiveRoles(['MECHANIC'])).toEqual(['MECHANIC']);
  });

  it("get the advisor's screens and the Manager label", async () => {
    server.use(sessionFor(user('MANAGER', 'manager1')));
    renderApp('/board');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: "Today's board" })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Customers' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'User accounts' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Not permitted' })).not.toBeInTheDocument();
  });
});
