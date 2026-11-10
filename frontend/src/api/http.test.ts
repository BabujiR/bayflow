import { http as mock, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { problem, server } from '@/test/server';

import { ApiError } from './errors';
import { configureSession, http, setAccessToken } from './http';

describe('http client', () => {
  it('sends the access token and parses JSON', async () => {
    setAccessToken('token-a');
    server.use(
      mock.get('*/api/v1/things', ({ request }) =>
        HttpResponse.json({ auth: request.headers.get('Authorization') }),
      ),
    );

    await expect(http.get('/api/v1/things')).resolves.toEqual({ auth: 'Bearer token-a' });
  });

  it('adds query parameters and skips empty ones', async () => {
    server.use(mock.get('*/api/v1/things', ({ request }) => HttpResponse.json({ url: request.url })));

    const result = await http.get<{ url: string }>('/api/v1/things', { status: 'BOOKED', date: '', page: 0 });

    expect(result.url).toContain('status=BOOKED');
    expect(result.url).toContain('page=0');
    expect(result.url).not.toContain('date=');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    setAccessToken('expired');
    const refresh = vi.fn(async () => {
      setAccessToken('fresh');
      return true;
    });
    configureSession({ refresh, onExpired: vi.fn() });
    server.use(
      mock.get('*/api/v1/things', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json({ ok: true })
          : problem(401, 'unauthorized', 'Token expired'),
      ),
    );

    await expect(http.get('/api/v1/things')).resolves.toEqual({ ok: true });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('shares one refresh between concurrent 401s', async () => {
    setAccessToken('expired');
    let resolveRefresh!: (ok: boolean) => void;
    const refresh = vi.fn(
      () =>
        new Promise<boolean>((resolve) => {
          resolveRefresh = (ok) => {
            setAccessToken('fresh');
            resolve(ok);
          };
        }),
    );
    configureSession({ refresh, onExpired: vi.fn() });
    let rejected = 0;
    server.use(
      mock.get('*/api/v1/things/:id', ({ request, params }) => {
        if (request.headers.get('Authorization') === 'Bearer fresh') {
          return HttpResponse.json({ id: params.id });
        }
        rejected++;
        return problem(401, 'unauthorized', 'Token expired');
      }),
    );

    const calls = Promise.all([http.get('/api/v1/things/1'), http.get('/api/v1/things/2')]);
    // Both requests have been rejected and are waiting on the refresh before it completes.
    await vi.waitFor(() => expect(rejected).toBe(2));
    await vi.waitFor(() => expect(refresh).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 20));
    resolveRefresh(true);

    await expect(calls).resolves.toEqual([{ id: '1' }, { id: '2' }]);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('ends the session when the refresh fails', async () => {
    const onExpired = vi.fn();
    configureSession({ refresh: async () => false, onExpired });
    server.use(mock.get('*/api/v1/things', () => problem(401, 'unauthorized', 'Token expired')));

    await expect(http.get('/api/v1/things')).rejects.toMatchObject({ status: 401, code: 'unauthorized' });
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('turns Problem Details into ApiError with field errors', async () => {
    server.use(
      mock.post('*/api/v1/things', () =>
        problem(400, 'validation-failed', 'One or more fields are invalid.', [
          { field: 'vin', message: 'must be a 17-character VIN' },
        ]),
      ),
    );

    const error = await http.post('/api/v1/things', { vin: 'x' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'validation-failed',
      message: 'One or more fields are invalid.',
      fieldErrors: { vin: 'must be a 17-character VIN' },
    });
  });

  it('does not refresh for public endpoints', async () => {
    const refresh = vi.fn(async () => true);
    configureSession({ refresh, onExpired: vi.fn() });
    server.use(mock.post('*/api/v1/auth/login', () => problem(401, 'invalid-credentials', 'Wrong password')));

    const { authApi } = await import('./auth');
    await expect(authApi.login({ username: 'a', password: 'b' })).rejects.toMatchObject({
      code: 'invalid-credentials',
    });
    expect(refresh).not.toHaveBeenCalled();
  });
});
