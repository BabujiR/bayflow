import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { AppNotification } from '@/api/types';
import { safeLink } from '@/features/notifications/NotificationItem';
import { renderApp } from '@/test/render';
import { location, notification, noSession, pageOf, server, sessionFor, user } from '@/test/server';

const unread = (count: number) =>
  http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ count }));

/** A user's notifications on the server: reading one deletes it, as notification-service does. */
function inbox(initial: AppNotification[]) {
  const left = [...initial];
  const read: string[] = [];
  server.use(
    http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ count: left.length })),
    http.get('*/api/v1/notifications', () => HttpResponse.json(pageOf([...left]))),
    http.post('*/api/v1/notifications/:id/read', ({ params }) => {
      const index = left.findIndex((n) => n.id === params.id);
      if (index < 0) {
        return new HttpResponse(null, { status: 404 });
      }
      left.splice(index, 1);
      read.push(params.id as string);
      return new HttpResponse(null, { status: 204 });
    }),
    http.post('*/api/v1/notifications/read-all', () => {
      const count = left.length;
      left.splice(0);
      return HttpResponse.json({ count });
    }),
  );
  return { left, read };
}

describe('the notification bell', () => {
  it('shows how many are unread, and nothing when there are none', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')), unread(3));
    const view = renderApp('/');

    const bell = await screen.findByRole('button', { name: 'Notifications, 3 unread' });
    expect(within(bell).getByTestId('notification-count')).toHaveTextContent('3');
    view.unmount();

    server.use(unread(0));
    renderApp('/');
    const quiet = await screen.findByRole('button', { name: 'Notifications' });
    expect(within(quiet).queryByTestId('notification-count')).not.toBeInTheDocument();
  });

  it('caps the number at 9+', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')), unread(42));
    renderApp('/board');
    expect(await screen.findByTestId('notification-count')).toHaveTextContent('9+');
  });

  it('opening one goes to it and removes it for good', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    const server_ = inbox([
      notification({ id: 'n1', title: 'Booking confirmed', link: '/appointments' }),
      notification({ id: 'n2', title: 'Estimate ready' }),
    ]);
    const { router } = renderApp('/');

    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }));
    const items = await screen.findAllByTestId('notification-item');
    expect(items).toHaveLength(2);

    await userEvent.click(items[0]);
    await vi.waitFor(() => expect(server_.read).toEqual(['n1']));
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/appointments'));

    // Opened again: the one just read is gone, the other is still there.
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }));
    const left = await screen.findAllByTestId('notification-item');
    expect(left).toHaveLength(1);
    expect(left[0]).toHaveTextContent('Estimate ready');
  });

  it('marks everything read at once, which empties it', async () => {
    server.use(sessionFor(user('MECHANIC', 'mechanic1')));
    const server_ = inbox([notification(), notification({ id: 'n2' })]);
    renderApp('/my-jobs');

    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Mark all read' }));

    await vi.waitFor(() => expect(server_.left).toHaveLength(0));
    expect(await screen.findByText(/You're all caught up/)).toBeInTheDocument();
    // The panel stays open (the rest of the page is hidden from assistive tech meanwhile); close it.
    await userEvent.keyboard('{Escape}');
    expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument();
    expect(screen.queryByTestId('notification-count')).not.toBeInTheDocument();
  });

  it('says when there is nothing', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    renderApp('/');
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }));
    expect(await screen.findByText(/You're all caught up/)).toBeInTheDocument();
  });

  it('is not there, and asks nothing, when nobody is signed in', async () => {
    const asked = vi.fn();
    server.use(
      noSession,
      http.get('*/api/v1/notifications/unread-count', () => {
        asked();
        return HttpResponse.json({ count: 1 });
      }),
    );
    renderApp('/login');
    await screen.findByRole('button', { name: 'Sign in' });
    expect(screen.queryByTestId('notification-bell')).not.toBeInTheDocument();
    expect(asked).not.toHaveBeenCalled();
  });
});

describe('the notifications page', () => {
  it('lists them with where they happened when there are several locations', async () => {
    const north = location({
      id: '0d0d0d0d-1111-4000-8000-0000000000d1',
      code: 'north',
      name: 'North',
      timeZone: 'Pacific/Auckland',
    });
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/auth/locations', () => HttpResponse.json([location(), north])),
    );
    inbox([notification({ title: 'New booking: AB12 CDE', locationId: north.id, createdAt: '2026-11-19T15:00:00Z' })]);
    renderApp('/notifications');

    const list = await screen.findByRole('list', { name: 'Notifications' });
    const item = within(list).getByTestId('notification-item');
    expect(item).toHaveTextContent('New booking: AB12 CDE');
    // 15:00 UTC is 04:00 the next day in Auckland.
    expect(item).toHaveTextContent(/Nov 20, 2026.*4:00/);
    expect(item).toHaveTextContent('North');
  });

  it('opening one removes it from the page', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    inbox([
      notification({ id: 'n1', title: 'First', link: null }),
      notification({ id: 'n2', title: 'Second', link: null }),
    ]);
    renderApp('/notifications');

    await userEvent.click(await screen.findByText('First'));
    await vi.waitFor(() => expect(screen.queryByText('First')).not.toBeInTheDocument());
    expect(screen.getByText('Second')).toBeInTheDocument();
  });

  it('says so when there are none', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    renderApp('/notifications');
    expect(await screen.findByText("You're all caught up")).toBeInTheDocument();
  });

  it('shows the text as text, never as markup', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    inbox([notification({ title: '<img src=x onerror=alert(1)>' })]);
    renderApp('/notifications');
    const item = await screen.findByTestId('notification-item');
    expect(item).toHaveTextContent('<img src=x onerror=alert(1)>');
    expect(item.querySelector('img')).toBeNull();
  });
});

describe('links in notifications', () => {
  it('only follows paths inside the app', () => {
    expect(safeLink('/job-cards/1')).toBe('/job-cards/1');
    expect(safeLink('//evil.example')).toBeNull();
    expect(safeLink('https://evil.example')).toBeNull();
    expect(safeLink('/\\evil.example')).toBeNull();
    expect(safeLink(null)).toBeNull();
  });
});
