import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

import type { Appointment, Customer, JobCard, PageResponse, Role, TokenResponse, User, Vehicle } from '@/api/types';

export function pageOf<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

/**
 * Background requests most screens make. Tests override these with server.use(...);
 * resetHandlers() after each test restores them.
 */
const defaults = [
  http.get('*/api/v1/customers/me', () => HttpResponse.json(customer())),
  http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([]))),
  http.get('*/api/v1/appointments', () => HttpResponse.json(pageOf([]))),
  http.get('*/api/v1/job-cards', () => HttpResponse.json(pageOf([]))),
];

/** MSW server shared by all tests. */
export const server = setupServer(...defaults);

export function user(role: Role = 'CUSTOMER', username = 'customer1'): User {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    username,
    email: `${username}@bayflow.local`,
    roles: [role],
    enabled: true,
    createdAt: '2026-11-01T10:00:00Z',
  };
}

export function token(u: User = user(), accessToken = 'access-1'): TokenResponse {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, expiresAt: '2026-11-10T20:00:00Z', user: u };
}

export function problem(status: number, type: string, detail: string, errors?: { field: string; message: string }[]) {
  return HttpResponse.json(
    { type: `urn:bayflow:problem:${type}`, title: 'Error', status, detail, errors },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** The session-restore call made on every app start: no cookie → 401. */
export const noSession = http.post('*/api/v1/auth/refresh', () =>
  problem(401, 'invalid-refresh-token', 'Please sign in again.'),
);

/** A valid refresh cookie for the given user. */
export function sessionFor(u: User) {
  return http.post('*/api/v1/auth/refresh', () => HttpResponse.json(token(u)));
}

export function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'c0000000-0000-0000-0000-000000000001',
    userId: '00000000-0000-0000-0000-000000000001',
    name: 'Jane Doe',
    email: 'jane@example.com',
    phone: '+1 555 0100',
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function vehicle(overrides: Partial<Vehicle> = {}): Vehicle {
  return {
    id: 'v0000000-0000-0000-0000-000000000001',
    customerId: 'c0000000-0000-0000-0000-000000000001',
    vin: '1HGCM82633A004352',
    registration: 'BFL 1234',
    make: 'Toyota',
    model: 'Corolla',
    year: 2021,
    mileage: 12000,
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: 'a0000000-0000-0000-0000-000000000001',
    customerId: 'c0000000-0000-0000-0000-000000000001',
    customerName: 'Jane Doe',
    vehicle: { id: 'v1', vin: '1HGCM82633A004352', registration: 'BFL 1234', make: 'Toyota', model: 'Corolla', year: 2021 },
    scheduledAt: '2030-01-15T15:00:00Z',
    endsAt: '2030-01-15T16:00:00Z',
    durationMinutes: 60,
    serviceType: 'BRAKES',
    status: 'BOOKED',
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function jobCard(overrides: Partial<JobCard> = {}): JobCard {
  return {
    id: 'j0000000-0000-0000-0000-000000000001',
    appointmentId: 'a0000000-0000-0000-0000-000000000001',
    vehicleId: 'v0000000-0000-0000-0000-000000000001',
    vin: '1HGCM82633A004352',
    registration: 'BFL 1234',
    customerUserId: '00000000-0000-0000-0000-000000000001',
    customerName: 'Jane Doe',
    mileage: 12480,
    status: 'OPEN',
    findings: [],
    recommendations: [],
    workLogs: [],
    parts: [],
    labour: [],
    labourRate: 95,
    currency: 'USD',
    estimatedTotal: 0,
    approvedEstimate: 0,
    createdAt: '2026-11-10T14:00:00Z',
    updatedAt: '2026-11-10T14:00:00Z',
    ...overrides,
  };
}
