import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { Announcement } from '@/api/types';
import { renderApp } from '@/test/render';
import { problem, server, sessionFor, user } from '@/test/server';

function announcement(overrides: Partial<Announcement> = {}): Announcement {
  return {
    id: 'a1',
    text: 'Winter check-up: free battery test',
    style: 'INFO',
    startsAt: null,
    endsAt: '2026-12-15T23:59:00Z',
    state: 'LIVE',
    createdAt: '2026-11-19T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

/**
 * The settings page is large (about 400 time zones, QuickBooks, service intervals…), and the
 * first test in this file also pays for loading it: the first lookups get more time.
 */
const SLOW_PAGE = { timeout: 10_000 };

async function addButton() {
  const button = await screen.findByRole('button', { name: 'Add announcement' }, SLOW_PAGE);
  // Enabled once the list has loaded (it is disabled at 10).
  await vi.waitFor(() => expect(button).toBeEnabled());
  return button;
}

describe('announcements (workshop ADMIN)', () => {
  it('lists them with where each is in its dates', async () => {
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.get('*/api/v1/auth/tenant/branding/announcements', () =>
        HttpResponse.json([
          announcement(),
          announcement({ id: 'a2', text: 'Holiday hours', style: 'IMPORTANT', state: 'SCHEDULED' }),
          announcement({ id: 'a3', text: 'Summer A/C', state: 'ENDED' }),
        ]),
      ),
    );
    renderApp('/settings');

    const rows = await screen.findAllByTestId('announcement-row', undefined, SLOW_PAGE);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent('Live');
    expect(rows[1]).toHaveTextContent('Scheduled');
    expect(rows[1]).toHaveTextContent('Important');
    expect(rows[2]).toHaveTextContent('Ended');
  });

  it('adds one with a style and dates', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.post('*/api/v1/auth/tenant/branding/announcements', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(announcement({ text: sent.text as string }), { status: 201 });
      }),
    );
    renderApp('/settings');
    await userEvent.click(await addButton());
    const dialog = await screen.findByRole('dialog', { name: 'Add announcement' });
    await userEvent.type(within(dialog).getByLabelText('Message'), 'Holiday hours: closed 24–26 December');
    await userEvent.selectOptions(within(dialog).getByLabelText('Style'), 'IMPORTANT');
    fireEvent.change(within(dialog).getByLabelText('Show until'), { target: { value: '2026-12-27T00:00' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add announcement' }));

    await vi.waitFor(() =>
      expect(sent).toMatchObject({
        text: 'Holiday hours: closed 24–26 December',
        style: 'IMPORTANT',
        startsAt: null,
        endsAt: '2026-12-27T05:00:00.000Z',
      }),
    );
  });

  it('refuses dates the wrong way round, and shows the server refusing an 11th', async () => {
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.post('*/api/v1/auth/tenant/branding/announcements', () =>
        problem(409, 'too-many-announcements', 'A workshop can keep at most 10 announcements.'),
      ),
    );
    renderApp('/settings');
    await userEvent.click(await addButton());
    const dialog = await screen.findByRole('dialog', { name: 'Add announcement' });
    await userEvent.type(within(dialog).getByLabelText('Message'), 'Holiday');
    fireEvent.change(within(dialog).getByLabelText('Show from'), { target: { value: '2026-12-24T08:00' } });
    fireEvent.change(within(dialog).getByLabelText('Show until'), { target: { value: '2026-12-20T08:00' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add announcement' }));
    expect(await within(dialog).findByText('Must be after the start')).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Show from'), { target: { value: '' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add announcement' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'A workshop can keep at most 10 announcements.',
    );
  });

  it('edits one, sending the version read, and deletes another', async () => {
    let edited: Record<string, unknown> | undefined;
    let deleted: string | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.get('*/api/v1/auth/tenant/branding/announcements', () =>
        HttpResponse.json([announcement({ version: 2 }), announcement({ id: 'a2', text: 'Old offer' })]),
      ),
      http.put('*/api/v1/auth/tenant/branding/announcements/:id', async ({ request, params }) => {
        edited = { id: params.id, ...((await request.json()) as Record<string, unknown>) };
        return HttpResponse.json(announcement({ version: 3 }));
      }),
      http.delete('*/api/v1/auth/tenant/branding/announcements/:id', ({ params }) => {
        deleted = params.id as string;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/settings');

    const rows = await screen.findAllByTestId('announcement-row', undefined, SLOW_PAGE);
    await userEvent.click(within(rows[0]).getByRole('button', { name: /^Edit/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit announcement' });
    const message = within(dialog).getByLabelText('Message');
    await userEvent.clear(message);
    await userEvent.type(message, 'Winter check-up until 20 December');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save announcement' }));
    await vi.waitFor(() =>
      expect(edited).toMatchObject({ id: 'a1', text: 'Winter check-up until 20 December', version: 2 }),
    );

    await userEvent.click(within(rows[1]).getByRole('button', { name: /^Delete/ }));
    await vi.waitFor(() => expect(deleted).toBe('a2'));
  });
});
