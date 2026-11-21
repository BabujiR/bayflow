import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { renderApp } from '@/test/render';
import { appointment, pageOf, server, sessionFor, user } from '@/test/server';

afterEach(() => {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
});

/** Records the query string of every appointments list request. */
function listRequests() {
  const seen: URLSearchParams[] = [];
  server.use(
    http.get('*/api/v1/appointments', ({ request }) => {
      seen.push(new URL(request.url).searchParams);
      return HttpResponse.json(pageOf([appointment()]));
    }),
  );
  return { last: () => seen[seen.length - 1], all: seen };
}

describe('sorting the appointments list', () => {
  it('starts with what is coming up and can be ordered other ways', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    const requests = listRequests();
    const { router } = renderApp('/appointments');

    expect(await screen.findByText('All bookings, upcoming first.')).toBeInTheDocument();
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('upcoming'));

    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'Latest first');
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('latest'));
    expect(screen.getByText('All bookings, latest first.')).toBeInTheDocument();
    expect(router.state.location.search).toContain('sort=latest');

    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'Recently booked');
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('recent'));
  });

  it('shows one day in time order unless another order is chosen', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    const requests = listRequests();
    const { router } = renderApp('/appointments');
    await screen.findByText('All bookings, upcoming first.');

    fireEvent.change(screen.getByLabelText('Day'), { target: { value: '2026-11-24' } });
    await vi.waitFor(() => expect(requests.last()?.get('date')).toBe('2026-11-24'));
    expect(requests.last()?.get('sort')).toBe('earliest');
    expect(screen.getByLabelText('Sort')).toHaveValue('earliest');
    expect(router.state.location.search).toContain('day=2026-11-24');
  });

  it('keeps the order in the address and remembers the last choice', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')));
    const requests = listRequests();
    const first = renderApp('/appointments?sort=earliest');
    expect(await screen.findByText('Your service bookings, earliest first.')).toBeInTheDocument();
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('earliest'));

    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'Latest first');
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('latest'));
    first.unmount();

    // A plain link to the page uses the order chosen last time.
    renderApp('/appointments');
    expect(await screen.findByText('Your service bookings, latest first.')).toBeInTheDocument();
  });

  it('ignores an order it does not know', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    const requests = listRequests();
    renderApp('/appointments?sort=cheapest');
    await screen.findByText('All bookings, upcoming first.');
    await vi.waitFor(() => expect(requests.last()?.get('sort')).toBe('upcoming'));
  });
});
