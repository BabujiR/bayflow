import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { loadLocationChoiceFor, locationHeader, setLocationChoice } from '@/features/locations/selection';
import { formatDateTime } from '@/lib/format';
import { toggleOption } from '@/test/multi-select';
import { renderApp } from '@/test/render';
import { appointment, location, pageOf, problem, server, sessionFor, user, vehicle } from '@/test/server';

const MAIN = location();
const NORTH = location({
  id: '0d0d0d0d-1111-4000-8000-0000000000d1',
  code: 'north',
  name: 'North',
  address: '1 Harbour Rd',
  timeZone: 'Pacific/Auckland',
  ownTimeZone: 'Pacific/Auckland',
  labourRate: 140,
  ownLabourRate: 140,
});

const twoLocations = http.get('*/api/v1/auth/locations', () => HttpResponse.json([MAIN, NORTH]));

afterEach(() => {
  loadLocationChoiceFor(null);
  setLocationChoice('all');
  try {
    localStorage.clear();
  } catch {
    // ignore
  }
});

describe('one location: nothing changes', () => {
  it('shows no switcher and no location choice', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/board');

    await screen.findByRole('region', { name: 'Booked' });
    expect(screen.queryByRole('combobox', { name: 'Location' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Location')).not.toBeInTheDocument();
  });
});

describe('the location switcher', () => {
  it('narrows every request to the chosen location and remembers it', async () => {
    const headers: (string | null)[] = [];
    server.use(
      sessionFor({ ...user('ADMIN', 'admin1'), allLocations: true }),
      twoLocations,
      http.get('*/api/v1/appointments', ({ request }) => {
        headers.push(request.headers.get('X-Bayflow-Location'));
        return HttpResponse.json(pageOf([]));
      }),
    );
    renderApp('/appointments');

    const switcher = await screen.findByRole('combobox', { name: 'Location' });
    expect(
      within(switcher)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['All locations', 'Main', 'North']);
    await vi.waitFor(() => expect(headers.length).toBeGreaterThan(0));
    expect(headers.at(-1)).toBeNull();

    await userEvent.selectOptions(switcher, NORTH.id);
    await vi.waitFor(() => expect(headers.at(-1)).toBe(NORTH.id));
    expect(locationHeader()).toBe(NORTH.id);
    expect(localStorage.getItem(`bayflow.location.${user().id}`)).toBe(NORTH.id);
  });

  it('only offers the locations a staff member works at', async () => {
    server.use(
      sessionFor({ ...user('SERVICE_ADVISOR', 'advisor1'), allLocations: false, locationIds: [NORTH.id] }),
      twoLocations,
    );
    renderApp('/appointments');

    await screen.findByRole('heading', { name: 'Appointments' });
    // One location of their own: nothing to switch.
    expect(screen.queryByRole('combobox', { name: 'Location' })).not.toBeInTheDocument();
  });
});

describe('times are each location’s', () => {
  it('lists bookings with their location and local time', async () => {
    const atNorth = appointment({ id: 'a-north', locationId: NORTH.id, scheduledAt: '2030-01-15T21:30:00Z' });
    server.use(
      sessionFor({ ...user('ADMIN', 'admin1'), allLocations: true }),
      twoLocations,
      http.get('*/api/v1/appointments', () => HttpResponse.json(pageOf([atNorth]))),
    );
    renderApp('/appointments');

    const row = (await screen.findByRole('cell', { name: 'North' })).closest('tr')!;
    // 21:30 UTC is 10:30 the next morning in Auckland (NZDT).
    expect(
      within(row).getByText(formatDateTime(atNorth.scheduledAt, 'Pacific/Auckland')),
    ).toBeInTheDocument();
    expect(formatDateTime(atNorth.scheduledAt, 'Pacific/Auckland')).toMatch(/16/);
  });

  it("books at the chosen location, in that location's time", async () => {
    let booked: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('CUSTOMER')),
      twoLocations,
      http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([vehicle()]))),
      http.post('*/api/v1/appointments', async ({ request }) => {
        booked = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(appointment({ locationId: NORTH.id }), { status: 201 });
      }),
    );
    renderApp('/appointments/new');

    const where = await screen.findByLabelText('Location');
    expect(where).toHaveValue(MAIN.id);
    await userEvent.selectOptions(where, NORTH.id);
    expect(screen.getByText(/1 Harbour Rd · times in Pacific\/Auckland/)).toBeInTheDocument();

    await screen.findByRole('option', { name: /BFL 1234/ });
    await userEvent.selectOptions(screen.getByLabelText('Vehicle'), 'v0000000-0000-0000-0000-000000000001');
    fireEvent.change(screen.getByLabelText('Date and time'), { target: { value: '2030-01-16T10:30' } });
    await userEvent.click(screen.getByRole('button', { name: 'Book appointment' }));

    // 10:30 in Auckland (UTC+13 in January) is 21:30 UTC the day before.
    await vi.waitFor(() =>
      expect(booked).toMatchObject({ locationId: NORTH.id, scheduledAt: '2030-01-15T21:30:00.000Z' }),
    );
  });

  it('shows the board of one location', async () => {
    const requests: URL[] = [];
    server.use(
      sessionFor({
        ...user('SERVICE_ADVISOR', 'advisor1'),
        allLocations: false,
        locationIds: [MAIN.id, NORTH.id],
      }),
      twoLocations,
      http.get('*/api/v1/appointments', ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(pageOf([]));
      }),
    );
    renderApp('/board');

    const picker = await screen.findByLabelText('Location', { selector: '#board-location' });
    await vi.waitFor(() => expect(requests.at(-1)?.searchParams.get('locationId')).toBe(MAIN.id));
    await userEvent.selectOptions(picker, NORTH.id);
    await vi.waitFor(() => expect(requests.at(-1)?.searchParams.get('locationId')).toBe(NORTH.id));
  });
});

describe('the Locations page', () => {
  it('adds a location and explains a refused archive', async () => {
    let created: Record<string, unknown> | undefined;
    server.use(
      sessionFor({ ...user('ADMIN', 'admin1'), allLocations: true }),
      twoLocations,
      http.post('*/api/v1/auth/locations', async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(location({ id: 'new', code: 'south', name: 'South' }), { status: 201 });
      }),
      http.post('*/api/v1/auth/locations/:id/archive', () =>
        problem(409, 'location-has-appointments', 'This location still has 2 upcoming appointment(s).'),
      ),
    );
    renderApp('/locations');

    expect(await screen.findByText('Pacific/Auckland')).toBeInTheDocument();
    expect(screen.getAllByText('(workshop default)').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Add location' }));
    const form = await screen.findByRole('form', { name: 'Add location' });
    await userEvent.type(within(form).getByLabelText('Name'), 'South');
    await userEvent.type(within(form).getByLabelText('Code'), 'South');
    await userEvent.type(within(form).getByLabelText('Labour rate per hour'), '120');
    await userEvent.click(within(form).getByRole('button', { name: 'Add location' }));
    await vi.waitFor(() => expect(created).toMatchObject({ name: 'South', code: 'south', labourRate: 120 }));
    expect(created?.timeZone).toBeUndefined();

    await userEvent.click(screen.getByRole('button', { name: 'Archive North' }));
    expect(await screen.findByText(/still has 2 upcoming appointment/)).toBeInTheDocument();
  });
});

describe('User accounts', () => {
  it('assigns staff to locations', async () => {
    let created: Record<string, unknown> | undefined;
    server.use(
      sessionFor({ ...user('ADMIN', 'admin1'), allLocations: true }),
      twoLocations,
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([]))),
      http.post('*/api/v1/auth/users', async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { ...user('MANAGER', 'mgr'), allLocations: false, locationIds: [NORTH.id] },
          {
            status: 201,
          },
        );
      }),
    );
    renderApp('/users');

    await userEvent.click(await screen.findByRole('button', { name: 'New staff account' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Username'), 'mgr');
    await userEvent.type(within(dialog).getByLabelText('Email'), 'mgr@example.com');
    await userEvent.type(within(dialog).getByLabelText('Temporary password'), 'Correct-Horse-1');
    await toggleOption(dialog, 'Roles', /^Mechanic/);
    await toggleOption(dialog, 'Roles', /^Manager/);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }));
    expect(await within(dialog).findByText('Choose at least one location')).toBeInTheDocument();
    expect(created).toBeUndefined();

    await toggleOption(dialog, 'Works at', /North/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }));
    await vi.waitFor(() => expect(created).toMatchObject({ roles: ['MANAGER'], locationIds: [NORTH.id] }));
  });
});
