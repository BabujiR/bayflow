import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { navGroupsFor } from '@/lib/navigation';
import { renderApp } from '@/test/render';
import { server, sessionFor, user } from '@/test/server';

afterEach(() => {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
});

describe('staff sidebar', () => {
  it("groups an admin's links into Workshop, Insights and Admin", async () => {
    server.use(sessionFor(user('ADMIN', 'admin1')));
    renderApp('/dashboard');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    const sections = within(nav).getAllByRole('group');
    expect(sections.map((s) => s.getAttribute('aria-label'))).toEqual(['Workshop', 'Insights', 'Admin']);
    expect(
      within(sections[0])
        .getAllByRole('link')
        .map((l) => l.textContent),
    ).toEqual(['Dashboard', 'Appointments', 'Job cards', 'Invoices', 'Customers']);
    expect(within(sections[0]).getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(sections[1]).getByRole('link', { name: 'Reports' })).not.toHaveAttribute('aria-current');
    expect(
      within(sections[2])
        .getAllByRole('link')
        .map((l) => l.textContent),
    ).toEqual(['User accounts', 'Locations', 'Canned jobs', 'Workshop settings']);
  });

  it('collapses to icons, keeps the names for screen readers, and remembers it', async () => {
    server.use(sessionFor(user('ADMIN', 'admin1')));
    const first = renderApp('/dashboard');

    const sidebar = await screen.findByTestId('sidebar');
    await userEvent.click(within(sidebar).getByRole('button', { name: 'Collapse sidebar' }));
    expect(sidebar).toHaveAttribute('data-collapsed', 'true');
    // Icons only, but each link still has its name (and a tooltip).
    const reports = within(sidebar).getByRole('link', { name: 'Reports' });
    expect(reports).toHaveAttribute('title', 'Reports');
    first.unmount();

    renderApp('/dashboard');
    const again = await screen.findByTestId('sidebar');
    expect(again).toHaveAttribute('data-collapsed', 'true');
    await userEvent.click(within(again).getByRole('button', { name: 'Expand sidebar' }));
    expect(again).toHaveAttribute('data-collapsed', 'false');
  });

  it('opens as a menu on small screens and closes after choosing a page', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    const { router } = renderApp('/board');

    await userEvent.click(await screen.findByRole('button', { name: 'Open menu' }));
    const menu = await screen.findByRole('dialog', { name: 'Menu' });
    await userEvent.click(within(menu).getByRole('link', { name: 'Job cards' }));

    expect(router.state.location.pathname).toBe('/job-cards');
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument();
  });

  it('gives each role only its own sections', () => {
    expect(navGroupsFor(['MECHANIC']).map((s) => s.group)).toEqual(['work']);
    expect(navGroupsFor(['MANAGER', 'SERVICE_ADVISOR']).map((s) => s.group)).toEqual([
      'work',
      'insights',
      'admin',
    ]);
  });
});

describe('customer navigation', () => {
  it('stays a single header row, without a sidebar', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'My vehicles' })).toBeInTheDocument();
    expect(screen.queryByTestId('sidebar')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument();
  });
});
