import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

import type { Role, TokenResponse, User } from '@/api/types';

/** MSW server shared by all tests; each test adds the handlers it needs. */
export const server = setupServer();

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
