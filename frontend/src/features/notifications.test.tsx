import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { safeLink } from '@/features/notifications/NotificationItem';
import { renderApp } from '@/test/render';
import { location, notification, noSession, pageOf, server, sessionFor, user } from '@/test/server';

const unread = (count: number) =>
  http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ count }));

describe('the notification bell', () => {
  it('shows how many are unread, and nothing when all are read', async () => {
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

  it('opens the latest, and clicking one marks it read and goes to it', async () => {
    let marked: string | undefined;
    server.use(
      sessionFor(user('CUSTOMER', 'customer1')),
      unread(1),
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json(
          pageOf([
            notification({
              id: 'n1',
              title: 'Booking confirmed',
              body: 'BFL 1234 on Tue 24 Nov.',
              link: '/appointments',
            }),
            notification({ id: 'n2', title: 'Older one', read: true, link: null }),
          ]),
        ),
      ),
      http.post('*/api/v1/notifications/:id/read', ({ params }) => {
        marked = params.id as string;
        return HttpResponse.json(notification({ id: marked, read: true }));
      }),
    );
    const { router } = renderApp('/');

    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }));
    const items = await screen.findAllByTestId('notification-item');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Booking confirmed (unread)');
    expect(items[1]).not.toHaveTextContent('(unread)');

    await userEvent.click(items[0]);
    await vi.waitFor(() => expect(marked).toBe('n1'));
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/appointments'));
  });

  it('marks everything read at once', async () => {
    let markedAll = false;
    server.use(
      sessionFor(user('MECHANIC', 'mechanic1')),
      http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ count: markedAll ? 0 : 2 })),
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json(pageOf([notification(), notification({ id: 'n2' })])),
      ),
      http.post('*/api/v1/notifications/read-all', () => {
        markedAll = true;
        return HttpResponse.json({ count: 2 });
      }),
    );
    renderApp('/my-jobs');

    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Mark all read' }));

    await vi.waitFor(() => expect(markedAll).toBe(true));
    // The panel stays open (the rest of the page is hidden from assistive tech meanwhile); close it.
    await userEvent.keyboard('{Escape}');
    expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument();
    expect(screen.queryByTestId('notification-count')).not.toBeInTheDocument();
  });

  it('says when there is nothing yet', async () => {
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
  it('lists every notification with where it happened when there are several locations', async () => {
    const north = location({
      id: '0d0d0d0d-1111-4000-8000-0000000000d1',
      code: 'north',
      name: 'North',
      timeZone: 'Pacific/Auckland',
    });
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'advisor1')),
      http.get('*/api/v1/auth/locations', () => HttpResponse.json([location(), north])),
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json(
          pageOf([
            notification({
              title: 'New booking: AB12 CDE',
              locationId: north.id,
              createdAt: '2026-11-19T15:00:00Z',
            }),
          ]),
        ),
      ),
    );
    renderApp('/notifications');

    const list = await screen.findByRole('list', { name: 'Notifications' });
    const item = within(list).getByTestId('notification-item');
    expect(item).toHaveTextContent('New booking: AB12 CDE');
    // 15:00 UTC is 04:00 the next day in Auckland.
    expect(item).toHaveTextContent(/Nov 20, 2026.*4:00/);
    expect(item).toHaveTextContent('North');
  });

  it('shows the text as text, never as markup', async () => {
    server.use(
      sessionFor(user('CUSTOMER', 'customer1')),
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json(pageOf([notification({ title: '<img src=x onerror=alert(1)>' })])),
      ),
    );
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
