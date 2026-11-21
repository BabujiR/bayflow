import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { request } from '@playwright/test';

import { Api, randomVin, slot, uid, type Slot } from '../../e2e/support/bayflow';

import { demoPassword, WORKSHOP_URL } from './presenter';

/** The sample workshop and its people. Fictional; e-mail addresses use example.com. */
export const WORKSHOP = {
  name: 'Summit Auto Care',
  currency: 'USD',
  timeZone: 'America/New_York',
  labourRate: '115',
  colour: '#0f766e',
  mainLocation: 'Main',
  second: { name: 'Summit North', code: 'north', labourRate: '105' },
  holidayNotice: 'Holiday hours: closed 24–26 December and 1 January. Online booking stays open.',
  winterOffer: 'Winter check-up: free battery and tyre test with every service until 15 December.',
};

export const OWNER = { username: 'owner', email: 'owner@summit-auto.example.com', name: 'Dana Brooks' };
export const ADVISOR = { username: 'sara', email: 'sara@summit-auto.example.com', name: 'Sara Patel' };
export const MECHANIC = { username: 'mike', email: 'mike@summit-auto.example.com', name: 'Mike Johnson' };
export const CUSTOMER = {
  username: 'john-carter',
  email: 'john.carter@example.com',
  name: 'John Carter',
  vehicle: { registration: 'SUM 204', make: 'Honda', model: 'Civic', year: '2019', mileage: '48200' },
};

/** The two jobs the advisor recommends in the walkthrough. */
export const BRAKES = {
  description: 'Replace front brake pads and resurface rotors',
  parts: '145.00',
  hours: '1.5',
};
export const WIPERS = { description: 'Replace wiper blades', parts: '32.00', hours: '0.25' };

const ASSETS = join(dirname(fileURLToPath(import.meta.url)), '..', 'assets');
export const LOGO = join(ASSETS, 'summit-logo.png');
export const COVER = join(ASSETS, 'summit-cover.jpg');

/** The first weekday at least `minDaysAhead` days from today (workshop time), at hour:minute. */
export function weekday(minDaysAhead: number, hour: number, minute = 0): Slot {
  for (let days = minDaysAhead; ; days++) {
    const candidate = slot(days, hour, minute);
    const dayOfWeek = new Date(`${candidate.date}T12:00:00Z`).getUTCDay();
    if (dayOfWeek !== 0 && dayOfWeek !== 6) {
      return candidate;
    }
  }
}

/** Whether the demo workshop exists yet. */
export async function workshopExists(): Promise<boolean> {
  const http = await request.newContext({ baseURL: WORKSHOP_URL });
  try {
    return (await http.get('/api/v1/tenants/current')).ok();
  } finally {
    await http.dispose();
  }
}

/** An API client signed in at the demo workshop. */
export async function signedIn(username: string, password = demoPassword()): Promise<Api> {
  const http = await request.newContext({ baseURL: WORKSHOP_URL });
  const response = await http.post('/api/v1/auth/login', { data: { username, password } });
  if (!response.ok()) {
    throw new Error(
      `Sign-in as ${username} at ${WORKSHOP_URL} failed (${response.status()}). ` +
        'Record 01-walkthrough first, with the same DEMO_SLUG and DEMO_PASSWORD.',
    );
  }
  const { accessToken } = (await response.json()) as { accessToken: string };
  return new Api(http, username, accessToken);
}

async function mainLocationId(api: Api): Promise<string | undefined> {
  const locations = await api.get<{ id: string; code: string; status: string }[]>('/api/v1/auth/locations');
  const active = locations.filter((l) => l.status === 'ACTIVE');
  return active.length > 1 ? active.find((l) => l.code === 'main')?.id : undefined;
}

export interface Prepared {
  customer: { username: string; name: string; api: Api };
  vehicle: { id: string; registration: string; label: string; mileage: number };
  appointmentId: string;
  at: Slot;
}

/**
 * Behind the scenes (not on video): a new customer with a car and a booking at Main, for the
 * clips that start in the middle of the story.
 */
export async function customerWithBooking(
  person: { first: string; last: string; car: { make: string; model: string; year: number } },
  at: Slot,
  serviceType = 'BRAKES',
  notes = 'Grinding noise when braking',
): Promise<Prepared> {
  const id = uid().toLowerCase();
  const username = `${person.first.toLowerCase()}-${id}`;
  const name = `${person.first} ${person.last}`;
  const password = demoPassword();
  const anonymous = await request.newContext({ baseURL: WORKSHOP_URL });
  const registered = await anonymous.post('/api/v1/auth/register', {
    data: { username, email: `${username}@example.com`, password },
  });
  await anonymous.dispose();
  if (!registered.ok()) {
    throw new Error(`Registering ${username} failed: ${registered.status()} ${await registered.text()}`);
  }
  const api = await signedIn(username, password);
  await api.post('/api/v1/customers/me', { name, email: `${username}@example.com` });
  const registration = `SUM ${id.slice(-3).toUpperCase()}`;
  const mileage = 30_000 + Math.floor(Math.random() * 40_000);
  const vehicle = await api.post<{ id: string }>('/api/v1/vehicles', {
    vin: randomVin(),
    registration,
    make: person.car.make,
    model: person.car.model,
    year: person.car.year,
    mileage,
  });
  const appointment = await api.post<{ id: string }>('/api/v1/appointments', {
    vehicleId: vehicle.id,
    locationId: await mainLocationId(api),
    scheduledAt: at.iso,
    durationMinutes: 90,
    serviceType,
    notes,
  });
  return {
    customer: { username, name, api },
    vehicle: {
      id: vehicle.id,
      registration,
      label: `${registration} — ${person.car.year} ${person.car.make} ${person.car.model}`,
      mileage,
    },
    appointmentId: appointment.id,
    at,
  };
}
